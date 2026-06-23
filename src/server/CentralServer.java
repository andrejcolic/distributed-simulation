package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.DistributedSubJobSpec;
import common.JobStatus;
import common.Logger;
import common.msg.WorkerMessages;

/**
 * Central server: accepts connections (one thread per connection), keeps the job and worker
 * registries, splits each job across the available workers, and coordinates the conservative
 * time barrier and result collection per job.
 */
public final class CentralServer {

    private final int port;
    private final Logger log;
    private final JobManager jobs;
    private final WorkerRegistry workers = new WorkerRegistry();
    private final Map<String, JobCoordinator> coordinators = new HashMap<>();

    /** One lock for all scheduling and worker slot-count changes (prevents double assignment). */
    private final Object scheduleLock = new Object();

    private volatile boolean running = true;
    private ServerSocket serverSocket;
    private HeartbeatMonitor heartbeat;

    public CentralServer(int port) {
        this.port = port;
        this.log = new Logger("logs/server.log");
        this.jobs = new JobManager("jobs", log);
    }

    public JobManager getJobs() {
        return jobs;
    }

    public WorkerRegistry getWorkers() {
        return workers;
    }

    public Logger getLog() {
        return log;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        log.log("Server listening on port " + port + ".");
        heartbeat = new HeartbeatMonitor(this);
        Thread hb = new Thread(heartbeat, "heartbeat");
        hb.setDaemon(true);
        hb.start();
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                new Thread(new ConnectionHandler(socket, this),
                    "conn-" + socket.getPort()).start();
            } catch (IOException e) {
                if (running) {
                    log.log("Accept failed: " + e.getMessage());
                }
            }
        }
    }

    public void stop() {
        running = false;
        if (heartbeat != null) {
            heartbeat.stop();
        }
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // shutting down
        }
    }

    /** Records that a worker is alive (called whenever any message is received from it). */
    void touch(WorkerHandle worker) {
        worker.touch();
    }

    /* ----- scheduling ----- */

    /** Splits each Ready job across all currently free workers and assigns the sub-jobs. */
    void schedule() {
        synchronized (scheduleLock) {
            while (true) {
                ServerJob job = jobs.nextReady();
                if (job == null) {
                    return;
                }
                List<WorkerHandle> available = workers.available();
                if (available.isEmpty()) {
                    return;
                }
                if (!assign(job, available)) {
                    return; // assignment aborted (e.g. a worker died); retry later
                }
            }
        }
    }

    private boolean assign(ServerJob job, List<WorkerHandle> chosen) {
        List<DistributedSubJobSpec> subs;
        try {
            subs = Partitioner.partition(job, chosen, jobs);
        } catch (IOException e) {
            jobs.setStatus(job, JobStatus.Failed, "partition failed: " + e.getMessage(), null);
            jobs.cleanupInputs(job.id);
            return true; // terminal; continue scheduling other jobs
        }
        JobCoordinator coordinator = new JobCoordinator(job.id, chosen,
            job.spec.getEndTime(), log);

        jobs.setStatus(job, JobStatus.Scheduled, null, workerNames(chosen));
        for (WorkerHandle w : chosen) {
            w.active++;
        }
        synchronized (coordinators) {
            coordinators.put(job.id, coordinator);
        }

        for (int i = 0; i < chosen.size(); i++) {
            try {
                chosen.get(i).connection.send(new WorkerMessages.AssignSubJob(subs.get(i)));
            } catch (IOException e) {
                // A worker died mid-assignment: undo and requeue the job.
                log.log("Assign of job " + job.id + " to " + chosen.get(i).name
                    + " failed: " + e.getMessage());
                for (WorkerHandle w : chosen) {
                    w.active--;
                }
                workers.unregister(chosen.get(i));
                synchronized (coordinators) {
                    coordinators.remove(job.id);
                }
                jobs.setStatus(job, JobStatus.Ready, "reassign after worker loss", null);
                return false;
            }
        }
        jobs.setStatus(job, JobStatus.Running, null, workerNames(chosen));
        log.log("Job " + job.id + " split across " + chosen.size() + " worker(s).");
        return true;
    }

    /* ----- message routing from ConnectionHandler ----- */

    void onSyncReport(WorkerMessages.SyncReport report) {
        JobCoordinator coordinator;
        synchronized (coordinators) {
            coordinator = coordinators.get(report.jobId);
        }
        if (coordinator != null) {
            coordinator.onReport(report);
        }
    }

    void onSubJobDone(String jobId, int workerIndex, String[][] states) {
        JobCoordinator coordinator;
        synchronized (coordinators) {
            coordinator = coordinators.get(jobId);
        }
        if (coordinator == null) {
            return;
        }
        boolean complete = coordinator.onDone(workerIndex, states);
        if (complete) {
            finishJob(coordinator);
        }
    }

    void onSubJobFailed(String jobId, int workerIndex, String reason) {
        JobCoordinator coordinator;
        synchronized (coordinators) {
            coordinator = coordinators.remove(jobId);
        }
        if (coordinator == null) {
            // Already handled (e.g. a worker loss is restarting this job) — ignore stray failures.
            return;
        }
        ServerJob job = jobs.get(jobId);
        if (job != null) {
            jobs.setStatus(job, JobStatus.Failed, reason, null);
            jobs.cleanupInputs(jobId);
        }
        // Cancel the other workers' sub-jobs so they stop waiting at the barrier.
        cancelOthers(coordinator, null);
        releaseSlots(coordinator.workers());
        schedule();
    }

    /** Aborts a job at the user's request: cancels any running sub-jobs and releases resources. */
    void abortJob(ServerJob job) {
        JobCoordinator coordinator;
        synchronized (coordinators) {
            coordinator = coordinators.remove(job.id);
        }
        if (coordinator != null) {
            cancelOthers(coordinator, null);
            releaseSlots(coordinator.workers());
        }
        jobs.setStatus(job, JobStatus.Aborted, "aborted by user", null);
        jobs.cleanupInputs(job.id);
        schedule();
    }

    /**
     * Declares a worker lost (heartbeat timeout or broken connection). Idempotent: the actual
     * loss handling runs exactly once. Any job the worker was part of is restarted on the
     * remaining workers (Test 3).
     */
    void markDead(WorkerHandle worker, String reason) {
        if (worker == null) {
            return;
        }
        synchronized (worker) {
            if (worker.dead) {
                return;
            }
            worker.dead = true;
        }
        worker.connection.close(); // unblocks its ConnectionHandler receive loop
        workers.unregister(worker);

        List<JobCoordinator> affected = new ArrayList<>();
        synchronized (coordinators) {
            for (JobCoordinator c : new ArrayList<>(coordinators.values())) {
                if (c.workers().contains(worker)) {
                    affected.add(c);
                    coordinators.remove(c.jobId());
                }
            }
        }
        for (JobCoordinator c : affected) {
            // Tell the surviving workers to abandon this run, then re-queue it for a fresh split.
            cancelOthers(c, worker);
            releaseSlots(c.workers());
            ServerJob job = jobs.get(c.jobId());
            if (job != null && (job.status == JobStatus.Running
                || job.status == JobStatus.Scheduled)) {
                jobs.setStatus(job, JobStatus.Ready, "restart: worker " + worker.name + " lost",
                    null);
                log.log("Job " + c.jobId() + " will restart on remaining workers.");
            }
        }
        log.log("Worker " + worker.name + " lost (" + reason + "), " + workers.size() + " left.");
        schedule();
    }

    /** Sends CancelSubJob to every worker of the coordinator except {@code skip}. */
    private void cancelOthers(JobCoordinator coordinator, WorkerHandle skip) {
        for (WorkerHandle w : coordinator.workers()) {
            if (w == skip || w.dead) {
                continue;
            }
            try {
                w.connection.send(new WorkerMessages.CancelSubJob(coordinator.jobId()));
            } catch (IOException e) {
                log.log("Cancel of job " + coordinator.jobId() + " to " + w.name
                    + " failed: " + e.getMessage());
            }
        }
    }

    private void finishJob(JobCoordinator coordinator) {
        ServerJob job = jobs.get(coordinator.jobId());
        if (job != null) {
            jobs.writeResult(job, coordinator.mergedStates());
            jobs.setStatus(job, JobStatus.Done, null, null);
            jobs.cleanupInputs(job.id); // release large input files (Test 7)
        }
        synchronized (coordinators) {
            coordinators.remove(coordinator.jobId());
        }
        releaseSlots(coordinator.workers());
        schedule();
    }

    private void releaseSlots(List<WorkerHandle> handles) {
        synchronized (scheduleLock) {
            for (WorkerHandle w : handles) {
                if (w.active > 0) {
                    w.active--;
                }
            }
        }
    }

    private static String workerNames(List<WorkerHandle> handles) {
        return handles.stream().map(w -> w.name)
            .sorted(Comparator.naturalOrder())
            .reduce((a, b) -> a + "," + b).orElse("");
    }
}
