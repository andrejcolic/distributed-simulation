package common.msg;

import common.JobInfo;
import common.JobSpec;

public final class ClientMessages {

    private ClientMessages() {}

    /* Client -> Server */

    public static final class SubmitJobRequest implements Message {
        public final JobSpec spec;
        public SubmitJobRequest(JobSpec spec) { this.spec = spec; }
    }

    public static final class StatusRequest implements Message {
        public final String jobId;
        public StatusRequest(String jobId) { this.jobId = jobId; }
    }

    public static final class ResultRequest implements Message {
        public final String jobId;
        public ResultRequest(String jobId) { this.jobId = jobId; }
    }

    public static final class AbortRequest implements Message {
        public final String jobId;
        public AbortRequest(String jobId) { this.jobId = jobId; }
    }

    /* Server -> Client */

    public static final class SubmitJobResponse implements Message {
        public final String jobId;
        public SubmitJobResponse(String jobId) { this.jobId = jobId; }
    }

    public static final class StatusResponse implements Message {
        public final JobInfo info;
        public StatusResponse(JobInfo info) { this.info = info; }
    }

    public static final class ResultResponse implements Message {
        public final JobInfo info;
        public final boolean available;
        public ResultResponse(JobInfo info, boolean available) {
            this.info = info;
            this.available = available;
        }
    }

    public static final class AbortResponse implements Message {
        public final boolean ok;
        public final String message;
        public AbortResponse(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    public static final class ErrorResponse implements Message {
        public final String message;
        public ErrorResponse(String message) { this.message = message; }
    }
}