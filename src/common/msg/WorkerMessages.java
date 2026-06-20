package rs.ac.bg.etf.kdp.common.msg;

import rs.ac.bg.etf.kdp.common.DistributedSubJobSpec;

/**
 * Messages for the Server &harr; Worker channel. Grouped as nested static classes
 * (all {@link Message} / {@code Serializable}).
 */
public final class WorkerMessages {

    private WorkerMessages() {
    }

    /* ----- Worker -> Server ----- */

    /**
     * Sent right after the worker starts: parallel capacity plus the port of the worker's peer
     * listener (so the server can tell other workers how to reach this one).
     */
    public static final class RegisterRequest implements Message {
        private static final long serialVersionUID = 1L;
        public final String name;
        public final int capacity;
        public final int peerPort;

        public RegisterRequest(String name, int capacity, int peerPort) {
            this.name = name;
            this.capacity = capacity;
            this.peerPort = peerPort;
        }
    }

    /** Worker reports it has reached the target time and returns its components' states. */
    public static final class SubJobDone implements Message {
        private static final long serialVersionUID = 1L;
        public final String jobId;
        public final int workerIndex;
        public final String[][] states;

        public SubJobDone(String jobId, int workerIndex, String[][] states) {
            this.jobId = jobId;
            this.workerIndex = workerIndex;
            this.states = states;
        }
    }

    public static final class SubJobFailed implements Message {
        private static final long serialVersionUID = 1L;
        public final String jobId;
        public final int workerIndex;
        public final String reason;

        public SubJobFailed(String jobId, int workerIndex, String reason) {
            this.jobId = jobId;
            this.workerIndex = workerIndex;
            this.reason = reason;
        }
    }

    /**
     * Conservative time-sync report: this worker is locally quiescent at the current safe time.
     * {@code localMin} is the smallest timestamp left in its queue ({@code Long.MAX_VALUE} if
     * empty); {@code sent}/{@code received} are cumulative peer-event counts for in-flight detection.
     */
    public static final class SyncReport implements Message {
        private static final long serialVersionUID = 1L;
        public final String jobId;
        public final int workerIndex;
        public final long localMin;
        public final long sent;
        public final long received;

        public SyncReport(String jobId, int workerIndex, long localMin, long sent, long received) {
            this.jobId = jobId;
            this.workerIndex = workerIndex;
            this.localMin = localMin;
            this.sent = sent;
            this.received = received;
        }
    }

    /** Worker's reply to a {@link Ping}; proves it is still alive (heartbeat). */
    public static final class Pong implements Message {
        private static final long serialVersionUID = 1L;
    }

    /* ----- Server -> Worker ----- */

    public static final class RegisterResponse implements Message {
        private static final long serialVersionUID = 1L;
        public final boolean ok;
        public final String message;

        public RegisterResponse(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    public static final class AssignSubJob implements Message {
        private static final long serialVersionUID = 1L;
        public final DistributedSubJobSpec subJob;

        public AssignSubJob(DistributedSubJobSpec subJob) {
            this.subJob = subJob;
        }
    }

    /**
     * Conservative time-sync barrier: advance the safe time to {@code safeTime} (process events
     * up to it), or {@code terminate} the run. A barrier with an unchanged {@code safeTime} and
     * {@code terminate=false} is a "recheck" (messages were still in flight).
     */
    public static final class SyncBarrier implements Message {
        private static final long serialVersionUID = 1L;
        public final String jobId;
        public final long safeTime;
        public final boolean terminate;

        public SyncBarrier(String jobId, long safeTime, boolean terminate) {
            this.jobId = jobId;
            this.safeTime = safeTime;
            this.terminate = terminate;
        }
    }

    /** Heartbeat request the server sends every x seconds. */
    public static final class Ping implements Message {
        private static final long serialVersionUID = 1L;
    }
}
