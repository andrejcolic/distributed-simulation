package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import common.DistributedSubJobSpec;
import common.JobStatus;
import common.Logger;
import common.msg.WorkerMessages;

// Central server: accepts connections (one thread each), keeps the job/worker registries, splits
// each job across the free workers, and coordinates the time barrier and result merge per job.
public final class CentralServer {

    private final int port;
    private final Logger log;
    private final JobManager jobs;
    private final WorkerRegistry workers = new WorkerRegistry();
    // Atomic map; "claim then act" uses remove(key, value) so each job is handled once.
    private final Map<String, JobCoordinator> coordinators = new ConcurrentHashMap<>();

    // One lock for scheduling and worker slot counts (prevents double assignment).
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

    void touch(WorkerHandle worker) {
        worker.touch();
    }

    /* scheduling */

    // Splits each Ready job across the free workers and assigns the sub-jobs.
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
        coordinators.put(job.id, coordinator);

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
                coordinators.remove(job.id);
                jobs.setStatus(job, JobStatus.Ready, "reassign after worker loss", null);
                return false;
            }
        }
        jobs.setStatus(job, JobStatus.Running, null, workerNames(chosen));
        log.log("Job " + job.id + " split across " + chosen.size() + " worker(s).");
        return true;
    }

    /* messages from ConnectionHandler */

    void onSyncReport(WorkerMessages.SyncReport report) {
        JobCoordinator coordinator = coordinators.get(report.jobId);
        if (coordinator != null) {
            coordinator.onReport(report);
        }
    }

    void onSubJobDone(String jobId, int workerIndex, String[][] states) {
        JobCoordinator coordinator = coordinators.get(jobId);
        if (coordinator == null) {
            return;
        }
        boolean complete = coordinator.onDone(workerIndex, states);
        if (complete) {
            finishJob(coordinator);
        }
    }

    void onSubJobFailed(String jobId, int workerIndex, String reason) {
        JobCoordinator coordinator = coordinators.remove(jobId);
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

    // User abort: cancel any running sub-jobs and release resources.
    void abortJob(ServerJob job) {
        JobCoordinator coordinator = coordinators.remove(job.id);
        if (coordinator != null) {
            cancelOthers(coordinator, null);
            releaseSlots(coordinator.workers());
        }
        jobs.setStatus(job, JobStatus.Aborted, "aborted by user", null);
        jobs.cleanupInputs(job.id);
        schedule();
    }

    // Declares a worker lost (idempotent). Any job it was running restarts on the remaining workers.
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
        for (JobCoordinator c : coordinators.values()) {
            // claim atomically: remove(key, value) wins only once, so no double-handling
            if (c.workers().contains(worker) && coordinators.remove(c.jobId(), c)) {
                affected.add(c);
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

    // Sends CancelSubJob to the coordinator's workers except `skip`.
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
        // Claim the job first: if a concurrent worker-loss/abort already removed it, do nothing.
        if (!coordinators.remove(coordinator.jobId(), coordinator)) {
            return;
        }
        ServerJob job = jobs.get(coordinator.jobId());
        if (job != null) {
            jobs.writeResult(job, coordinator.mergedStates());
            jobs.setStatus(job, JobStatus.Done, null, null);
            jobs.cleanupInputs(job.id); // free large input files
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
