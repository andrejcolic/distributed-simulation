package rs.ac.bg.etf.kdp.worker;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import rs.ac.bg.etf.kdp.common.Connection;
import rs.ac.bg.etf.kdp.common.msg.Message;
import rs.ac.bg.etf.kdp.common.msg.PeerMessages;
import rs.ac.bg.etf.kdp.common.msg.WorkerMessages;

/**
 * Worker runtime: connects to the central server, registers its parallel capacity and peer port,
 * then serves assigned distributed sub-jobs. A peer listener accepts connections from other
 * workers so they can exchange simulation events directly.
 *
 * <p>Has no Swing dependency, so it works headless as well.
 */
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

    /** Connects, registers and runs the receive loop. Returns when the connection drops. */
    public void run() throws IOException {
        peerServer = new ServerSocket(0);
        int peerPort = peerServer.getLocalPort();
        startPeerListener();

        connection = Connection.connect(host, port);
        System.out.println("Worker '" + name + "' connected to " + host + ":" + port
            + " (capacity " + capacity + ", peer port " + peerPort + ")");
        try {
            connection.send(new WorkerMessages.RegisterRequest(name, capacity, peerPort));
            receiveLoop();
        } finally {
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

    /* ----- server channel ----- */

    private void receiveLoop() {
        while (running) {
            Message msg;
            try {
                msg = connection.receive();
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Worker '" + name + "' lost connection: " + e.getMessage());
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
            } else if (msg instanceof WorkerMessages.Ping) {
                sendQuietly(new WorkerMessages.Pong());
            } else if (msg instanceof WorkerMessages.RegisterResponse) {
                System.out.println("Worker '" + name + "' registered: "
                    + ((WorkerMessages.RegisterResponse) msg).message);
            }
        }
    }

    private void handleAssign(WorkerMessages.AssignSubJob assign) {
        final String jobId = assign.subJob.getJobId();
        activeJobs.incrementAndGet();
        DistributedJob job = new DistributedJob(assign.subJob, connection,
            () -> { jobs.remove(jobId); activeJobs.decrementAndGet(); });
        registerJob(jobId, job);
        System.out.println("Worker '" + name + "' starting job " + jobId
            + " as worker " + assign.subJob.getWorkerIndex()
            + "/" + assign.subJob.getWorkerCount());
        job.start();
    }

    private void registerJob(String jobId, DistributedJob job) {
        List<PendingPeer> drain;
        synchronized (jobs) {
            jobs.put(jobId, job);
            drain = pending.remove(jobId);
        }
        if (drain != null) {
            for (PendingPeer p : drain) {
                job.addInboundPeer(p.fromIndex, p.conn);
            }
        }
    }

    private DistributedJob getJob(String jobId) {
        synchronized (jobs) {
            return jobs.get(jobId);
        }
    }

    /* ----- peer listener ----- */

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
            routeInbound(h.jobId, h.fromIndex, conn);
        } catch (IOException | ClassNotFoundException e) {
            // bad/garbage peer connection — ignore it
        }
    }

    private void routeInbound(String jobId, int fromIndex, Connection conn) {
        DistributedJob job;
        synchronized (jobs) {
            job = jobs.get(jobId);
            if (job == null) {
                pending.computeIfAbsent(jobId, k -> new ArrayList<>())
                    .add(new PendingPeer(fromIndex, conn));
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
            System.out.println("Worker '" + name + "' could not send "
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
        final int fromIndex;
        final Connection conn;

        PendingPeer(int fromIndex, Connection conn) {
            this.fromIndex = fromIndex;
            this.conn = conn;
        }
    }
}
