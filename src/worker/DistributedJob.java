package worker;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import common.Connection;
import common.DistributedSubJobSpec;
import common.PeerEndpoint;
import common.msg.Message;
import common.msg.PeerMessages;
import common.msg.WorkerMessages;
import sleep.simulation.Event;
import sleep.simulation.Netlist;
import sleep.simulation.Simulator;
import sleep.simulation.SimulatorSinglethread;

// One sub-job running on this worker: sets up peer connections, runs the simulation with a
// DistributedSimBuffer, and follows the server-coordinated conservative barrier. The engine is
// always SimulatorSinglethread — the barrier enforces global timestamp order, so processing the
// global-minimum event each step matches a single-machine run. (Optimistic/Time Warp is the
// documented alternative, not implemented here.)
@SuppressWarnings({"rawtypes", "unchecked"})
public final class DistributedJob {

    private final DistributedSubJobSpec spec;
    private final Connection serverConn;     // worker's link to the server (for reports/done)
    private final Runnable onComplete;
    private final java.io.File componentsFile;
    private final java.io.File connectionsFile;
    private final DistributedSimBuffer buffer;

    private final CountDownLatch peersReady;
    private final Connection[] peerConns;

    private final Object barrierLock = new Object();
    private WorkerMessages.SyncBarrier pendingBarrier;
    private volatile boolean cancelled;

    public DistributedJob(DistributedSubJobSpec spec, Connection serverConn, Runnable onComplete,
                          java.io.File componentsFile, java.io.File connectionsFile) {
        this.spec = spec;
        this.serverConn = serverConn;
        this.onComplete = onComplete;
        this.componentsFile = componentsFile;
        this.connectionsFile = connectionsFile;
        this.buffer = new DistributedSimBuffer(spec.getRouting(), spec.getWorkerIndex());
        this.peerConns = new Connection[spec.getWorkerCount()];
        this.peersReady = new CountDownLatch(Math.max(0, spec.getWorkerCount() - 1));
    }

    public String jobId() {
        return spec.getJobId();
    }

    public void start() {
        new Thread(this::run, "job-" + spec.getJobId() + "-w" + spec.getWorkerIndex()).start();
    }

    // Called by the peer listener when a lower-index peer connects to us.
    public void addInboundPeer(int fromIndex, Connection conn) {
        registerPeer(fromIndex, conn);
    }

    // Called by the receive loop when a barrier for this job arrives.
    public void onBarrier(WorkerMessages.SyncBarrier barrier) {
        synchronized (barrierLock) {
            pendingBarrier = barrier;
            barrierLock.notifyAll();
        }
    }

    // Abandon this sub-job (a peer failed; the server restarts it). Terminates silently.
    public void cancel() {
        cancelled = true;
        buffer.setTerminated();
        onBarrier(new WorkerMessages.SyncBarrier(spec.getJobId(), 0, true));
    }

    private void run() {
        try {
            Netlist netlist = NetlistBuilder.build(componentsFile, connectionsFile);
            connectToHigherPeers();
            awaitPeers();

            Simulator simulator = new SimulatorSinglethread(spec.getWorkerIndex());
            simulator.setQueue(buffer);
            simulator.setNetlist(netlist);
            simulator.init();

            conservativeLoop(simulator);

            if (cancelled) {
                return; // restarted elsewhere — stay silent
            }
            serverConn.send(new WorkerMessages.SubJobDone(spec.getJobId(),
                spec.getWorkerIndex(), netlist.getState()));
        } catch (Exception e) {
            if (!cancelled) {
                sendFailed(e.getMessage());
            }
        } finally {
            closePeers();
            deleteLocalFiles(); // free the downloaded input split
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    // The conservative barrier loop: process everything safe, then report and wait.
    private void conservativeLoop(Simulator simulator) {
        buffer.setSafeTime(Long.MIN_VALUE);
        while (!buffer.isTerminated()) {
            Event e;
            while ((e = buffer.pollProcessable()) != null) {
                simulator.setlTime(e.getlTime());
                if (e.ok()) {
                    simulator.work(e);
                }
            }
            // Locally quiescent at the current safe time: report and wait for the next barrier.
            send(new WorkerMessages.SyncReport(spec.getJobId(), spec.getWorkerIndex(),
                buffer.getMinrank(), buffer.sentCount(), buffer.receivedCount()));

            WorkerMessages.SyncBarrier barrier = awaitBarrier();
            if (barrier == null || barrier.terminate) {
                buffer.setTerminated();
                break;
            }
            buffer.setSafeTime(barrier.safeTime);
        }
    }

    private WorkerMessages.SyncBarrier awaitBarrier() {
        synchronized (barrierLock) {
            while (pendingBarrier == null) {
                try {
                    barrierLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            WorkerMessages.SyncBarrier b = pendingBarrier;
            pendingBarrier = null;
            return b;
        }
    }

    /* peer setup */

    private void connectToHigherPeers() throws IOException {
        int self = spec.getWorkerIndex();
        List<PeerEndpoint> peers = spec.getPeers();
        for (int j = self + 1; j < spec.getWorkerCount(); j++) {
            PeerEndpoint ep = peers.get(j);
            Connection conn = Connection.connect(ep.host, ep.port);
            conn.send(new PeerMessages.PeerHello(spec.getJobId(), self));
            registerPeer(j, conn);
        }
    }

    private void registerPeer(int index, Connection conn) {
        synchronized (peerConns) {
            if (peerConns[index] != null) {
                return; // already registered
            }
            peerConns[index] = conn;
        }
        buffer.setPeer(index, conn);
        startReceiver(index, conn);
        peersReady.countDown();
    }

    private void startReceiver(int index, Connection conn) {
        Thread t = new Thread(() -> {
            try {
                while (!buffer.isTerminated()) {
                    Message m = conn.receive();
                    if (m instanceof PeerMessages.RouteEvents) {
                        buffer.receiveEvents(((PeerMessages.RouteEvents) m).events);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                if (!buffer.isTerminated()) {
                    System.out.println("Peer " + index + " receiver ended: " + e.getMessage());
                }
            }
        }, "peer-recv-" + spec.getJobId() + "-" + index);
        t.setDaemon(true);
        t.start();
    }

    private void awaitPeers() throws InterruptedException {
        peersReady.await();
    }

    private void closePeers() {
        for (Connection c : peerConns) {
            if (c != null) {
                c.close();
            }
        }
    }

    private void deleteLocalFiles() {
        if (componentsFile != null) {
            componentsFile.delete();
        }
        if (connectionsFile != null) {
            connectionsFile.delete();
        }
    }

    /* helpers */

    private void send(Message msg) {
        try {
            serverConn.send(msg);
        } catch (IOException e) {
            buffer.setTerminated();
        }
    }

    private void sendFailed(String reason) {
        send(new WorkerMessages.SubJobFailed(spec.getJobId(), spec.getWorkerIndex(),
            String.valueOf(reason)));
    }
}
