package common;

// Job statuses defined by the assignment.
public enum JobStatus {
    Ready,      // arrived, not yet assigned
    Scheduled,  // being sent to a worker
    Running,    // executing
    Done,       // finished successfully
    Failed,     // could not execute
    Aborted     // user gave up
}
