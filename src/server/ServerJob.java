package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.JobInfo;
import rs.ac.bg.etf.kdp.common.JobSpec;
import rs.ac.bg.etf.kdp.common.JobStatus;

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
