package worker;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import common.Connection;
import common.DistributedSubJobSpec;
import common.StreamUtil;
import common.msg.Message;
import common.msg.PeerMessages;
import common.msg.WorkerMessages;

// Worker runtime: registers with the server, serves assigned sub-jobs, and accepts peer
// connections from other workers for direct event exchange. Runs headless (no GUI dependency).
public final class Worker {

    private final String host;
    private final int port;
    private final int capacity;
    private final String name;

    private final AtomicInteger activeJobs = new AtomicInteger(0);
    private final Map<String, DistributedJob> jobs = new HashMap<>();
    private final Map<String, List<PendingPeer>> pending = new HashMap<>();

    private volatile Connection connection;
    private volatile ServerSocket peerServer;
    private volatile boolean running = true;
    private volatile boolean connected = false;
    private volatile Consumer<String> statusListener; // optional GUI hook

    public Worker(String host, int port, int capacity) {
        this.host = host;
        this.port = port;
        this.capacity = capacity;
        this.name = resolveName();
    }

    public int getActiveJobs() {
        return activeJobs.get();
    }

    public int getCapacity() {
        return capacity;
    }

    public String getName() {
        return name;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isRunning() {
        return running;
    }

    public void setStatusListener(Consumer<String> listener) {
        this.statusListener = listener;
    }

    private void status(String msg) {
        System.out.println(msg);
        Consumer<String> l = statusListener;
        if (l != null) {
            try {
                l.accept(msg);
            } catch (RuntimeException ignored) {
                // a GUI listener must not bring down the worker
            }
        }
    }

    // Connects, registers, runs the receive loop. Returns when the connection drops.
    public void run() throws IOException {
        peerServer = new ServerSocket(0);
        int peerPort = peerServer.getLocalPort();
        startPeerListener();

        connection = Connection.connect(host, port);
        status("Worker '" + name + "' connected to " + host + ":" + port
            + " (capacity " + capacity + ", peer port " + peerPort + "), registering…");
        try {
            connection.send(new WorkerMessages.RegisterRequest(name, capacity, peerPort));
            receiveLoop();
        } finally {
            connected = false;
            connection.close();
            closePeerServer();
        }
    }

    public void stop() {
        running = false;
        Connection c = connection;
        if (c != null) {
            c.close();
        }
        closePeerServer();
    }

    /* server channel */

    private void receiveLoop() {
        while (running) {
            Message msg;
            try {
                msg = connection.receive();
            } catch (IOException | ClassNotFoundException e) {
                status("Worker '" + name + "' lost connection: " + e.getMessage());
                return;
            }
            if (msg instanceof WorkerMessages.AssignSubJob) {
                handleAssign((WorkerMessages.AssignSubJob) msg);
            } else if (msg instanceof WorkerMessages.SyncBarrier) {
                WorkerMessages.SyncBarrier b = (WorkerMessages.SyncBarrier) msg;
                DistributedJob job = getJob(b.jobId);
                if (job != null) {
                    job.onBarrier(b);
                }
            } else if (msg instanceof WorkerMessages.CancelSubJob) {
                WorkerMessages.CancelSubJob c = (WorkerMessages.CancelSubJob) msg;
                DistributedJob job = getJob(c.jobId);
                if (job != null) {
                    status("Worker '" + name + "' cancelling job " + c.jobId
                        + " (restart on remaining workers)");
                    job.cancel();
                }
            } else if (msg instanceof WorkerMessages.Ping) {
                sendQuietly(new WorkerMessages.Pong());
            } else if (msg instanceof WorkerMessages.RegisterResponse) {
                connected = true;
                status("Worker '" + name + "' registered: "
                    + ((WorkerMessages.RegisterResponse) msg).message);
            }
        }
    }

    // Start the job off the receive loop so barriers/pings keep flowing during the input transfer.
    private void handleAssign(WorkerMessages.AssignSubJob assign) {
        new Thread(() -> startJob(assign.subJob),
            "assign-" + assign.subJob.getJobId()).start();
    }

    private void startJob(DistributedSubJobSpec sub) {
        final String jobId = sub.getJobId();
        activeJobs.incrementAndGet();
        // The attempt keeps a restart's local files separate from the previous run's.
        File dir = new File("work", jobId + "_a" + sub.getAttempt() + "_w" + sub.getWorkerIndex());
        dir.mkdirs();
        File comp = new File(dir, "components.txt");
        File conn = new File(dir, "connections.txt");

        FetchOutcome outcome = fetchInputs(sub, comp, conn);
        if (outcome != FetchOutcome.OK) {
            activeJobs.decrementAndGet();
            if (outcome == FetchOutcome.ABANDONED) {
                // Inputs are gone (job torn down): stay silent, like a cancel.
                status("Worker '" + name + "' abandoning job " + jobId
                    + " (inputs no longer available — job torn down).");
            } else {
                status("Worker '" + name + "' could not fetch inputs for job " + jobId
                    + " after retries.");
                sendQuietly(new WorkerMessages.SubJobFailed(jobId, sub.getWorkerIndex(),
                    "input fetch failed after retries"));
            }
            return;
        }

        final DistributedJob[] holder = new DistributedJob[1];
        DistributedJob job = new DistributedJob(sub, connection,
            () -> completeJob(jobId, holder[0]), comp, conn);
        holder[0] = job;
        int workerIndex = sub.getWorkerIndex() + 1;
        registerJob(jobId, job);
        status("Worker '" + name + "' starting job " + jobId
            + " as worker " + workerIndex + "/" + sub.getWorkerCount());
        job.start();
    }

    // Fetches this worker's input split, retrying transient failures. ABANDONED if the server says
    // the inputs are gone, FAILED only after every attempt failed.
    private FetchOutcome fetchInputs(DistributedSubJobSpec sub, File comp, File conn) {
        final int attempts = 3;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try (Connection fc = Connection.connect(host, port)) {
                fc.send(new WorkerMessages.FetchFiles(sub.getJobId(), sub.getWorkerIndex()));
                Message resp = fc.receive();
                if (!(resp instanceof WorkerMessages.FetchResponse)) {
                    throw new IOException("unexpected fetch response: "
                        + (resp == null ? "null" : resp.getClass().getSimpleName()));
                }
                if (!((WorkerMessages.FetchResponse) resp).available) {
                    return FetchOutcome.ABANDONED;
                }
                StreamUtil.receiveFile(fc, comp);
                StreamUtil.receiveFile(fc, conn);
                return FetchOutcome.OK;
            } catch (IOException | ClassNotFoundException e) {
                status("Worker '" + name + "' fetch attempt " + attempt + "/" + attempts
                    + " for job " + sub.getJobId() + " failed: " + e.getMessage());
                if (attempt < attempts) {
                    try {
                        Thread.sleep(250L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return FetchOutcome.FAILED;
                    }
                }
            }
        }
        return FetchOutcome.FAILED;
    }

    private enum FetchOutcome {
        OK, ABANDONED, FAILED
    }

    private void completeJob(String jobId, DistributedJob job) {
        synchronized (jobs) {
            if (jobs.get(jobId) == job) { // only if a restart hasn't replaced it
                jobs.remove(jobId);
            }
        }
        activeJobs.decrementAndGet();
    }

    // Registers the job and drains pending peer connections, keyed by attempt (reject stale ones).
    private void registerJob(String jobId, DistributedJob job) {
        List<PendingPeer> drain;
        List<PendingPeer> keep = new ArrayList<>();
        synchronized (jobs) {
            jobs.put(jobId, job);
            drain = pending.remove(jobId);
        }
        if (drain != null) {
            for (PendingPeer p : drain) {
                if (p.attempt == job.attempt()) {
                    job.addInboundPeer(p.fromIndex, p.conn);
                } else if (p.attempt < job.attempt()) {
                    p.conn.close();
                } else {
                    keep.add(p);
                }
            }
        }
        if (!keep.isEmpty()) {
            synchronized (jobs) {
                pending.computeIfAbsent(jobId, k -> new ArrayList<>()).addAll(keep);
            }
        }
    }

    private DistributedJob getJob(String jobId) {
        synchronized (jobs) {
            return jobs.get(jobId);
        }
    }

    /* peer listener */

    private void startPeerListener() {
        Thread t = new Thread(() -> {
            while (running) {
                Socket socket;
                try {
                    socket = peerServer.accept();
                } catch (IOException e) {
                    return; // listener closed
                }
                new Thread(() -> acceptPeer(socket), "peer-accept").start();
            }
        }, "peer-listener");
        t.setDaemon(true);
        t.start();
    }

    private void acceptPeer(Socket socket) {
        try {
            Connection conn = Connection.accept(socket);
            Message hello = conn.receive();
            if (!(hello instanceof PeerMessages.PeerHello)) {
                conn.close();
                return;
            }
            PeerMessages.PeerHello h = (PeerMessages.PeerHello) hello;
            routeInbound(h.jobId, h.attempt, h.fromIndex, conn);
        } catch (IOException | ClassNotFoundException e) {
            // bad/garbage peer connection — ignore it
        }
    }

    private void routeInbound(String jobId, int attempt, int fromIndex, Connection conn) {
        DistributedJob job;
        synchronized (jobs) {
            job = jobs.get(jobId);
            if (job == null || attempt > job.attempt()) {
                pending.computeIfAbsent(jobId, k -> new ArrayList<>())
                    .add(new PendingPeer(attempt, fromIndex, conn));
                return;
            }
            if (attempt < job.attempt()) {
                conn.close(); // connection from a previous (restarted) run
                return;
            }
        }
        job.addInboundPeer(fromIndex, conn);
    }

    private void closePeerServer() {
        try {
            ServerSocket ps = peerServer;
            if (ps != null) {
                ps.close();
            }
        } catch (IOException ignored) {
            // shutting down
        }
    }

    private void sendQuietly(Message msg) {
        try {
            Connection c = connection;
            if (c != null) {
                c.send(msg);
            }
        } catch (IOException e) {
            status("Worker '" + name + "' could not send "
                + msg.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String resolveName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return "worker-" + (System.currentTimeMillis() % 100000);
        }
    }

    private static final class PendingPeer {
        final int attempt;
        final int fromIndex;
        final Connection conn;

        PendingPeer(int attempt, int fromIndex, Connection conn) {
            this.attempt = attempt;
            this.fromIndex = fromIndex;
            this.conn = conn;
        }
    }
}
