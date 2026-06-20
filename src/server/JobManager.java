package rs.ac.bg.etf.kdp.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import rs.ac.bg.etf.kdp.common.JobSpec;
import rs.ac.bg.etf.kdp.common.JobStatus;
import rs.ac.bg.etf.kdp.common.Logger;

/**
 * Thread-safe registry of jobs with persistence on disk, so job state survives a server
 * restart and a client disconnect (Test 2). Each job lives in {@code jobs/<id>/}:
 * <ul>
 *   <li>{@code spec.ser} — the serialized {@link JobSpec} (lets the server reassign after restart),</li>
 *   <li>{@code status.properties} — status, timestamps, message, result size,</li>
 *   <li>{@code result.txt} — the merged output (written when Done).</li>
 * </ul>
 */
public final class JobManager {

    private final File baseDir;
    private final Logger log;
    private final Map<String, ServerJob> jobs = new LinkedHashMap<>();
    private final AtomicLong seq = new AtomicLong(0);

    public JobManager(String baseDir, Logger log) {
        this.baseDir = new File(baseDir);
        this.log = log;
        this.baseDir.mkdirs();
        loadFromDisk();
    }

    public synchronized ServerJob create(JobSpec spec) {
        String id = "j" + seq.incrementAndGet();
        ServerJob job = new ServerJob(id, spec, System.currentTimeMillis());
        jobs.put(id, job);
        jobDir(id).mkdirs();
        persistSpec(job);
        persistStatus(job);
        log.log("Job " + id + " received (output '" + spec.getOutputName() + "').");
        return job;
    }

    public synchronized ServerJob get(String id) {
        return jobs.get(id);
    }

    public synchronized List<ServerJob> all() {
        return new ArrayList<>(jobs.values());
    }

    /** Next job in {@code Ready} state, or {@code null} if none. */
    public synchronized ServerJob nextReady() {
        for (ServerJob job : jobs.values()) {
            if (job.status == JobStatus.Ready) {
                return job;
            }
        }
        return null;
    }

    public synchronized void setStatus(ServerJob job, JobStatus status, String message,
                                       String assignedWorker) {
        job.status = status;
        if (message != null) {
            job.message = message;
        }
        if (assignedWorker != null) {
            job.assignedWorker = assignedWorker;
        }
        if (status == JobStatus.Done || status == JobStatus.Failed
            || status == JobStatus.Aborted) {
            job.finishedAt = System.currentTimeMillis();
        }
        persistStatus(job);
        log.log("Job " + job.id + " -> " + status
            + (job.assignedWorker != null ? " [" + job.assignedWorker + "]" : "")
            + (message != null && !message.isEmpty() ? " (" + message + ")" : ""));
    }

    /**
     * Writes the merged component states to {@code result.txt} (one component per line), sorted by
     * component id so the output is deterministic regardless of how the job was split.
     */
    public synchronized void writeResult(ServerJob job, String[][] states) {
        String[][] sorted = states.clone();
        java.util.Arrays.sort(sorted, java.util.Comparator.comparingLong(JobManager::leadingId));
        File out = resultFile(job.id);
        try (PrintWriter pw = new PrintWriter(out, StandardCharsets.UTF_8.name())) {
            for (String[] state : sorted) {
                StringBuilder sb = new StringBuilder();
                for (String s : state) {
                    sb.append(s).append(' ');
                }
                pw.println(sb.toString().trim());
            }
        } catch (IOException e) {
            log.log("Job " + job.id + " result write failed: " + e.getMessage());
        }
        job.resultSize = out.length();
    }

    private static long leadingId(String[] state) {
        if (state == null || state.length == 0) {
            return Long.MAX_VALUE;
        }
        try {
            return Long.parseLong(state[0]);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    public File resultFile(String id) {
        return new File(jobDir(id), "result.txt");
    }

    private File jobDir(String id) {
        return new File(baseDir, id);
    }

    /* ----- persistence ----- */

    private void persistSpec(ServerJob job) {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(new File(jobDir(job.id), "spec.ser")))) {
            oos.writeObject(job.spec);
        } catch (IOException e) {
            log.log("Job " + job.id + " spec persist failed: " + e.getMessage());
        }
    }

    private void persistStatus(ServerJob job) {
        Properties p = new Properties();
        p.setProperty("status", job.status.name());
        p.setProperty("submittedAt", Long.toString(job.submittedAt));
        p.setProperty("finishedAt", Long.toString(job.finishedAt));
        p.setProperty("message", job.message == null ? "" : job.message);
        p.setProperty("assignedWorker", job.assignedWorker == null ? "" : job.assignedWorker);
        p.setProperty("resultSize", Long.toString(job.resultSize));
        p.setProperty("outputName", job.spec == null ? "" : job.spec.getOutputName());
        try (FileOutputStream fos = new FileOutputStream(
                new File(jobDir(job.id), "status.properties"))) {
            p.store(fos, "job " + job.id);
        } catch (IOException e) {
            log.log("Job " + job.id + " status persist failed: " + e.getMessage());
        }
    }

    private void loadFromDisk() {
        File[] dirs = baseDir.listFiles(File::isDirectory);
        if (dirs == null) {
            return;
        }
        long maxSeq = 0;
        for (File dir : dirs) {
            String id = dir.getName();
            ServerJob job = loadJob(dir, id);
            if (job == null) {
                continue;
            }
            jobs.put(id, job);
            if (id.startsWith("j")) {
                try {
                    maxSeq = Math.max(maxSeq, Long.parseLong(id.substring(1)));
                } catch (NumberFormatException ignored) {
                    // non-standard id, ignore for the sequence counter
                }
            }
        }
        seq.set(maxSeq);
        if (!jobs.isEmpty()) {
            log.log("Restored " + jobs.size() + " job(s) from disk.");
        }
    }

    private ServerJob loadJob(File dir, String id) {
        File statusFile = new File(dir, "status.properties");
        File specFile = new File(dir, "spec.ser");
        if (!statusFile.exists() || !specFile.exists()) {
            return null;
        }
        JobSpec spec;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(specFile))) {
            spec = (JobSpec) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
        Properties p = new Properties();
        try (FileInputStream fis = new FileInputStream(statusFile)) {
            p.load(fis);
        } catch (IOException e) {
            return null;
        }
        ServerJob job = new ServerJob(id, spec, parseLong(p.getProperty("submittedAt")));
        JobStatus saved = JobStatus.valueOf(p.getProperty("status", "Ready"));
        // Jobs that were mid-flight when the server stopped are re-queued so they get
        // reassigned to a worker (Test 2/3).
        if (saved == JobStatus.Scheduled || saved == JobStatus.Running) {
            saved = JobStatus.Ready;
        }
        job.status = saved;
        job.finishedAt = parseLong(p.getProperty("finishedAt"));
        job.message = p.getProperty("message", "");
        job.assignedWorker = emptyToNull(p.getProperty("assignedWorker", ""));
        job.resultSize = parseLong(p.getProperty("resultSize"));
        return job;
    }

    private static long parseLong(String s) {
        try {
            return s == null ? 0 : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
