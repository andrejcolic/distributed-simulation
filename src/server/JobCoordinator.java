package server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import common.Logger;
import common.msg.WorkerMessages;

/**
 * Coordinates one running distributed job: the conservative time barrier and the collection of
 * per-worker results.
 *
 * <p>Each round, every worker sends a {@link WorkerMessages.SyncReport} once it is locally
 * quiescent. When all K have reported, the coordinator computes the global minimum timestamp and
 * checks for in-flight peer messages ({@code sent == received}). It then broadcasts a
 * {@link WorkerMessages.SyncBarrier}: advance the safe time, recheck (messages still in flight),
 * or terminate (global minimum reached the end time, or all queues empty).
 */
public final class JobCoordinator {

    private final String jobId;
    private final List<WorkerHandle> workers;
    private final int k;
    private final long endTime;
    private final Logger log;

    // sync round state
    private final long[] localMin;
    private final long[] sent;
    private final long[] received;
    private final boolean[] reported;
    private int reportedCount;
    private long safeTime = Long.MIN_VALUE;
    private boolean terminated;

    // result collection
    private final String[][][] states;
    private final boolean[] done;
    private int doneCount;

    public JobCoordinator(String jobId, List<WorkerHandle> workers, long endTime, Logger log) {
        this.jobId = jobId;
        this.workers = new ArrayList<>(workers);
        this.k = workers.size();
        this.endTime = endTime;
        this.log = log;
        this.localMin = new long[k];
        this.sent = new long[k];
        this.received = new long[k];
        this.reported = new boolean[k];
        this.states = new String[k][][];
        this.done = new boolean[k];
    }

    public String jobId() {
        return jobId;
    }

    public List<WorkerHandle> workers() {
        return workers;
    }

    /** Feeds one worker's sync report; broadcasts a barrier once all workers have reported. */
    public synchronized void onReport(WorkerMessages.SyncReport r) {
        if (terminated || r.workerIndex < 0 || r.workerIndex >= k) {
            return;
        }
        localMin[r.workerIndex] = r.localMin;
        sent[r.workerIndex] = r.sent;
        received[r.workerIndex] = r.received;
        if (!reported[r.workerIndex]) {
            reported[r.workerIndex] = true;
            reportedCount++;
        }
        if (reportedCount == k) {
            computeAndBroadcast();
        }
    }

    private void computeAndBroadcast() {
        long globalMin = Long.MAX_VALUE;
        long totalSent = 0;
        long totalReceived = 0;
        for (int i = 0; i < k; i++) {
            globalMin = Math.min(globalMin, localMin[i]);
            totalSent += sent[i];
            totalReceived += received[i];
        }
        // start the next round
        reportedCount = 0;
        Arrays.fill(reported, false);

        boolean terminate;
        long newSafe;
        if (totalSent != totalReceived) {
            // peer messages still in flight — keep the safe time, ask everyone to recheck
            newSafe = safeTime;
            terminate = false;
        } else if (globalMin == Long.MAX_VALUE || globalMin >= endTime) {
            terminate = true;
            newSafe = safeTime;
        } else {
            safeTime = globalMin;
            newSafe = globalMin;
            terminate = false;
        }
        if (terminate) {
            terminated = true;
        }
        broadcast(new WorkerMessages.SyncBarrier(jobId, newSafe, terminate));
    }

    private void broadcast(WorkerMessages.SyncBarrier barrier) {
        for (WorkerHandle w : workers) {
            try {
                w.connection.send(barrier);
            } catch (IOException e) {
                log.log("Job " + jobId + " barrier to " + w.name + " failed: " + e.getMessage());
            }
        }
    }

    /** Feeds one worker's result; returns true when all workers have finished. */
    public synchronized boolean onDone(int workerIndex, String[][] workerStates) {
        if (workerIndex < 0 || workerIndex >= k) {
            return false;
        }
        if (!done[workerIndex]) {
            done[workerIndex] = true;
            states[workerIndex] = workerStates;
            doneCount++;
        }
        return doneCount == k;
    }

    /** Merges every worker's component states into a single array (server then sorts by id). */
    public synchronized String[][] mergedStates() {
        List<String[]> all = new ArrayList<>();
        for (String[][] perWorker : states) {
            if (perWorker != null) {
                for (String[] s : perWorker) {
                    all.add(s);
                }
            }
        }
        return all.toArray(new String[0][]);
    }
}
