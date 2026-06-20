package rs.ac.bg.etf.kdp.common.msg;

import rs.ac.bg.etf.kdp.common.JobInfo;
import rs.ac.bg.etf.kdp.common.JobSpec;

/**
 * Messages for the Client &harr; Server channel. Grouped as nested static classes
 * (all {@link Message} / {@code Serializable}).
 */
public final class ClientMessages {

    private ClientMessages() {
    }

    /* ----- Client -> Server ----- */

    public static final class SubmitJobRequest implements Message {
        private static final long serialVersionUID = 1L;
        public final JobSpec spec;

        public SubmitJobRequest(JobSpec spec) {
            this.spec = spec;
        }
    }

    public static final class StatusRequest implements Message {
        private static final long serialVersionUID = 1L;
        public final String jobId;

        public StatusRequest(String jobId) {
            this.jobId = jobId;
        }
    }

    public static final class ResultRequest implements Message {
        private static final long serialVersionUID = 1L;
        public final String jobId;

        public ResultRequest(String jobId) {
            this.jobId = jobId;
        }
    }

    public static final class AbortRequest implements Message {
        private static final long serialVersionUID = 1L;
        public final String jobId;

        public AbortRequest(String jobId) {
            this.jobId = jobId;
        }
    }

    /* ----- Server -> Client ----- */

    public static final class SubmitJobResponse implements Message {
        private static final long serialVersionUID = 1L;
        public final String jobId;

        public SubmitJobResponse(String jobId) {
            this.jobId = jobId;
        }
    }

    public static final class StatusResponse implements Message {
        private static final long serialVersionUID = 1L;
        public final JobInfo info;

        public StatusResponse(JobInfo info) {
            this.info = info;
        }
    }

    /**
     * Header of the response to {@code GET_RESULT}. If {@code available==true},
     * a stream of {@link FileChunk}s totaling {@code fileSize} follows this message.
     */
    public static final class ResultResponse implements Message {
        private static final long serialVersionUID = 1L;
        public final JobInfo info;
        public final boolean available;
        public final long fileSize;

        public ResultResponse(JobInfo info, boolean available, long fileSize) {
            this.info = info;
            this.available = available;
            this.fileSize = fileSize;
        }
    }

    public static final class AbortResponse implements Message {
        private static final long serialVersionUID = 1L;
        public final boolean ok;
        public final String message;

        public AbortResponse(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    /** Generic error / request rejection (e.g. unknown jobId). */
    public static final class ErrorResponse implements Message {
        private static final long serialVersionUID = 1L;
        public final String message;

        public ErrorResponse(String message) {
            this.message = message;
        }
    }
}
