package rs.ac.bg.etf.kdp.worker;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import rs.ac.bg.etf.kdp.common.Connection;
import rs.ac.bg.etf.kdp.common.DistributedSubJobSpec;
import rs.ac.bg.etf.kdp.common.PeerEndpoint;
import rs.ac.bg.etf.kdp.common.msg.Message;
import rs.ac.bg.etf.kdp.common.msg.PeerMessages;
import rs.ac.bg.etf.kdp.common.msg.WorkerMessages;
import rs.ac.bg.etf.sleep.simulation.Event;
import rs.ac.bg.etf.sleep.simulation.Netlist;
import rs.ac.bg.etf.sleep.simulation.Simulator;
import rs.ac.bg.etf.sleep.simulation.SimulatorSinglethread;

/**
 * One distributed sub-job running on this worker. Sets up peer connections, runs the simulation
 * with a {@link DistributedSimBuffer}, and drives the conservative time barrier coordinated by
 * the server.
 *
 * <p>The simulation engine is {@code SimulatorSinglethread} regardless of the requested type:
 * the conservative barrier enforces global timestamp order, so processing the global-minimum
 * event each step yields the same result as a single-machine single-threaded run. (Optimistic /
 * Time Warp across machines is the documented alternative, not implemented here.)
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class DistributedJob {

    private final DistributedSubJobSpec spec;
    private final Connection serverConn;     // worker's link to the server (for reports/done)
    private final Runnable onComplete;
    private final DistributedSimBuffer buffer;

    private final CountDownLatch peersReady;
    private final Connection[] peerConns;

    private final Object barrierLock = new Object();
    private WorkerMessages.SyncBarrier pendingBarrier;

    public DistributedJob(DistributedSubJobSpec spec, Connection serverConn, Runnable onComplete) {
        this.spec = spec;
        this.serverConn = serverConn;
        this.onComplete = onComplete;
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

    /** Delivered by the worker's peer listener when a lower-index peer connects to us. */
    public void addInboundPeer(int fromIndex, Connection conn) {
        registerPeer(fromIndex, conn);
    }

    /** Delivered by the worker's receive loop when a barrier for this job arrives. */
    public void onBarrier(WorkerMessages.SyncBarrier barrier) {
        synchronized (barrierLock) {
            pendingBarrier = barrier;
            barrierLock.notifyAll();
        }
    }

    private void run() {
        try {
            Netlist netlist = NetlistBuilder.build(spec.getComponentLines(),
                spec.getConnectionLines());
            connectToHigherPeers();
            awaitPeers();

            Simulator simulator = new SimulatorSinglethread(spec.getWorkerIndex());
            simulator.setQueue(buffer);
            simulator.setNetlist(netlist);
            simulator.init();

            conservativeLoop(simulator);

            serverConn.send(new WorkerMessages.SubJobDone(spec.getJobId(),
                spec.getWorkerIndex(), netlist.getState()));
        } catch (Exception e) {
            sendFailed(e.getMessage());
        } finally {
            closePeers();
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    /** The conservative barrier loop: process everything safe, then report and wait. */
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

    /* ----- peer setup ----- */

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

    /* ----- helpers ----- */

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
