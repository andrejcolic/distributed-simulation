package server;

import common.JobInfo;
import common.JobSpec;
import common.JobStatus;

// Server-side state of one job; mutable fields are guarded by the JobManager monitor.
public class ServerJob {

    final String id;
    JobSpec spec;
    JobStatus status;
    final long submittedAt;
    long finishedAt;
    String message;
    String assignedWorker;
    long resultSize;
    boolean schedulable; // false until inputs have arrived and validated

    ServerJob(String id, JobSpec spec, long submittedAt) {
        this.id = id;
        this.spec = spec;
        this.status = JobStatus.Ready;
        this.submittedAt = submittedAt;
        this.message = "";
    }

    JobInfo toInfo() {
        return new JobInfo(id, status, submittedAt, finishedAt, resultSize, message);
    }
}
