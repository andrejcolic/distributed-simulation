package rs.ac.bg.etf.kdp.common;

/**
 * Job statuses tracked by the central server (see assignment, section 7).
 */
public enum JobStatus {
    /** Arrived at the server, not yet forwarded to anyone. */
    Ready,
    /** Currently being forwarded to a worker. */
    Scheduled,
    /** Execution in progress. */
    Running,
    /** Finished successfully. */
    Done,
    /** Could not be executed (exception / invalid configuration). */
    Failed,
    /** The user gave up on the execution. */
    Aborted
}
