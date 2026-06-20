package rs.ac.bg.etf.kdp.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rs.ac.bg.etf.kdp.common.DistributedSubJobSpec;
import rs.ac.bg.etf.kdp.common.JobStatus;
import rs.ac.bg.etf.kdp.common.Logger;
import rs.ac.bg.etf.kdp.common.msg.WorkerMessages;

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
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // shutting down
        }
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
        List<DistributedSubJobSpec> subs = Partitioner.partition(job, chosen);
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
        ServerJob job = jobs.get(jobId);
        if (job != null) {
            jobs.setStatus(job, JobStatus.Failed, reason, null);
        }
        if (coordinator != null) {
            releaseSlots(coordinator.workers());
        }
        schedule();
    }

    void onWorkerDisconnected(WorkerHandle worker) {
        if (worker == null) {
            return;
        }
        workers.unregister(worker);
        // Fail any job this worker was part of (heartbeat-driven restart comes in a later celina).
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
            ServerJob job = jobs.get(c.jobId());
            if (job != null && job.status == JobStatus.Running) {
                jobs.setStatus(job, JobStatus.Ready, "worker disconnected", null);
            }
            releaseSlots(c.workers());
        }
        log.log("Worker " + worker.name + " disconnected (" + workers.size() + " left).");
        schedule();
    }

    private void finishJob(JobCoordinator coordinator) {
        ServerJob job = jobs.get(coordinator.jobId());
        if (job != null) {
            jobs.writeResult(job, coordinator.mergedStates());
            jobs.setStatus(job, JobStatus.Done, null, null);
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
