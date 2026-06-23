package common.msg;

import common.DistributedSubJobSpec;

// Messages for the Server <-> Worker channel.
public final class WorkerMessages {

    private WorkerMessages() {}

    /* Worker -> Server */

    // Sent on startup: parallel capacity + the worker's peer-listener port.
    public static final class RegisterRequest implements Message {
        public final String name;
        public final int capacity;
        public final int peerPort;

        public RegisterRequest(String name, int capacity, int peerPort) {
            this.name = name;
            this.capacity = capacity;
            this.peerPort = peerPort;
        }
    }

    // Worker reached the target time; returns its components' states.
    public static final class SubJobDone implements Message {
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
        public final String jobId;
        public final int workerIndex;
        public final String reason;

        public SubJobFailed(String jobId, int workerIndex, String reason) {
            this.jobId = jobId;
            this.workerIndex = workerIndex;
            this.reason = reason;
        }
    }

    // This worker is idle at the current safe time. localMin is its smallest queued timestamp
    // (Long.MAX_VALUE if empty); sent/received are cumulative peer-event counts (in-flight check).
    public static final class SyncReport implements Message {
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

    // Reply to Ping (heartbeat).
    public static final class Pong implements Message {
    }

    /* Server -> Worker */

    public static final class RegisterResponse implements Message {
        public final boolean ok;
        public final String message;

        public RegisterResponse(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    public static final class AssignSubJob implements Message {
        public final DistributedSubJobSpec subJob;

        public AssignSubJob(DistributedSubJobSpec subJob) {
            this.subJob = subJob;
        }
    }

    // Advance to safeTime, or terminate. Same safeTime with terminate=false means "recheck"
    // (peer messages were still in flight).
    public static final class SyncBarrier implements Message {
        public final String jobId;
        public final long safeTime;
        public final boolean terminate;

        public SyncBarrier(String jobId, long safeTime, boolean terminate) {
            this.jobId = jobId;
            this.safeTime = safeTime;
            this.terminate = terminate;
        }
    }

    // Heartbeat request.
    public static final class Ping implements Message {
    }

    // Worker asks the server to stream its input split, on a separate download connection.
    public static final class FetchFiles implements Message {
        public final String jobId;
        public final int workerIndex;

        public FetchFiles(String jobId, int workerIndex) {
            this.jobId = jobId;
            this.workerIndex = workerIndex;
        }
    }

    // Reply before any file bytes. available=false means the inputs are gone (job torn down),
    // so the worker abandons silently instead of seeing an abrupt socket reset.
    public static final class FetchResponse implements Message {
        public final boolean available;
        public final String reason;

        public FetchResponse(boolean available, String reason) {
            this.available = available;
            this.reason = reason;
        }
    }

    // Tells a worker to drop its sub-job (the run is restarting on the remaining workers).
    public static final class CancelSubJob implements Message {
        public final String jobId;

        public CancelSubJob(String jobId) {
            this.jobId = jobId;
        }
    }
}
