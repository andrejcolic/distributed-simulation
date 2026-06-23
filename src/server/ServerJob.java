package server;

import common.JobInfo;
import common.JobSpec;
import common.JobStatus;

/**
 * Server-side state of a single job. Mutable fields are guarded by the owning
 * {@link JobManager}'s monitor.
 */
public class ServerJob {

    final String id;
    JobSpec spec;
    JobStatus status;
    final long submittedAt;
    long finishedAt;
    String message;
    String assignedWorker;
    long resultSize;
    /** False until the streamed input files have arrived and validated — keeps it off the queue. */
    boolean schedulable;

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
