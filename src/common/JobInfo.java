package common;

import java.io.Serializable;

public class JobInfo implements Serializable {

    private final String jobId;
    private final JobStatus status;
    private final long submittedAt;
    private final long finishedAt;     // 0 if not finished
    private final long resultSize;     // bytes, 0 if no result
    private final String message;

    public JobInfo(String jobId, JobStatus status, long submittedAt, long finishedAt,
                   long resultSize, String message) {
        this.jobId = jobId;
        this.status = status;
        this.submittedAt = submittedAt;
        this.finishedAt = finishedAt;
        this.resultSize = resultSize;
        this.message = message;
    }

    public String getJobId() {
        return jobId;
    }

    public JobStatus getStatus() {
        return status;
    }

    public long getSubmittedAt() {
        return submittedAt;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public long getResultSize() {
        return resultSize;
    }

    public String getMessage() {
        return message;
    }

    public boolean hasResult() {
        return status == JobStatus.Done && resultSize > 0;
    }
}
