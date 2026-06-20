package rs.ac.bg.etf.kdp.server;

import java.io.File;
import java.io.IOException;
import java.net.Socket;

import rs.ac.bg.etf.kdp.common.Connection;
import rs.ac.bg.etf.kdp.common.ConfigException;
import rs.ac.bg.etf.kdp.common.ConfigValidator;
import rs.ac.bg.etf.kdp.common.JobStatus;
import rs.ac.bg.etf.kdp.common.StreamUtil;
import rs.ac.bg.etf.kdp.common.msg.ClientMessages;
import rs.ac.bg.etf.kdp.common.msg.Message;
import rs.ac.bg.etf.kdp.common.msg.WorkerMessages;

/**
 * Handles one accepted connection on its own thread. The first message decides the role:
 * a {@link WorkerMessages.RegisterRequest} means a worker, anything else is treated as a client.
 *
 * <p>Robustness (Test 4): a non-protocol / garbage connection fails the handshake or message
 * decode; this handler logs it, closes that one connection, and the server keeps running.
 */
public final class ConnectionHandler implements Runnable {

    private final Socket socket;
    private final CentralServer server;

    public ConnectionHandler(Socket socket, CentralServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        Connection conn;
        try {
            conn = Connection.accept(socket);
        } catch (IOException e) {
            // Wrong/garbage protocol — reject this connection only (Test 4).
            server.getLog().log("Rejected connection from "
                + socket.getRemoteSocketAddress() + ": " + e.getMessage());
            closeSocket();
            return;
        }

        try {
            Message first = conn.receive();
            if (first instanceof WorkerMessages.RegisterRequest) {
                handleWorker(conn, (WorkerMessages.RegisterRequest) first);
            } else {
                handleClient(conn, first);
            }
        } catch (java.io.EOFException e) {
            // Peer closed the connection cleanly (e.g. client finished one request) — normal.
        } catch (IOException | ClassNotFoundException e) {
            server.getLog().log("Connection from " + socket.getRemoteSocketAddress()
                + " ended: " + e.getMessage());
        } finally {
            conn.close();
        }
    }

    /* ----- worker side ----- */

    private void handleWorker(Connection conn, WorkerMessages.RegisterRequest reg)
            throws IOException, ClassNotFoundException {
        String peerHost = socket.getInetAddress().getHostAddress();
        WorkerHandle handle = server.getWorkers().register(
            reg.name, conn, peerHost, reg.peerPort, reg.capacity);
        server.getLog().log("Worker " + reg.name + " registered (capacity " + reg.capacity
            + ", peer " + peerHost + ":" + reg.peerPort + ", id " + handle.id + ").");
        conn.send(new WorkerMessages.RegisterResponse(true, "registered as " + handle.id));
        server.schedule();

        try {
            while (true) {
                Message msg = conn.receive();
                if (msg instanceof WorkerMessages.SyncReport) {
                    server.onSyncReport((WorkerMessages.SyncReport) msg);
                } else if (msg instanceof WorkerMessages.SubJobDone) {
                    WorkerMessages.SubJobDone d = (WorkerMessages.SubJobDone) msg;
                    server.onSubJobDone(d.jobId, d.workerIndex, d.states);
                } else if (msg instanceof WorkerMessages.SubJobFailed) {
                    WorkerMessages.SubJobFailed f = (WorkerMessages.SubJobFailed) msg;
                    server.onSubJobFailed(f.jobId, f.workerIndex, f.reason);
                } else if (msg instanceof WorkerMessages.Pong) {
                    // heartbeat reply — liveness handled in a later celina
                }
            }
        } finally {
            server.onWorkerDisconnected(handle);
        }
    }

    /* ----- client side ----- */

    private void handleClient(Connection conn, Message first)
            throws IOException, ClassNotFoundException {
        Message msg = first;
        while (msg != null) {
            if (msg instanceof ClientMessages.SubmitJobRequest) {
                handleSubmit(conn, (ClientMessages.SubmitJobRequest) msg);
            } else if (msg instanceof ClientMessages.StatusRequest) {
                handleStatus(conn, ((ClientMessages.StatusRequest) msg).jobId);
            } else if (msg instanceof ClientMessages.ResultRequest) {
                handleResult(conn, ((ClientMessages.ResultRequest) msg).jobId);
            } else if (msg instanceof ClientMessages.AbortRequest) {
                handleAbort(conn, ((ClientMessages.AbortRequest) msg).jobId);
            } else {
                conn.send(new ClientMessages.ErrorResponse(
                    "Unsupported request: " + msg.getClass().getSimpleName()));
            }
            msg = conn.receive(); // throws on disconnect, ending the loop
        }
    }

    private void handleSubmit(Connection conn, ClientMessages.SubmitJobRequest req)
            throws IOException {
        ServerJob job = server.getJobs().create(req.spec);
        try {
            ConfigValidator.validate(req.spec);
            server.getJobs().setStatus(job, JobStatus.Ready, null, null);
            conn.send(new ClientMessages.SubmitJobResponse(job.id));
            server.schedule();
        } catch (ConfigException e) {
            // Invalid configuration (Test 6): the job exists but is Failed with a clear reason.
            server.getJobs().setStatus(job, JobStatus.Failed, e.getMessage(), null);
            conn.send(new ClientMessages.SubmitJobResponse(job.id));
        }
    }

    private void handleStatus(Connection conn, String jobId) throws IOException {
        ServerJob job = server.getJobs().get(jobId);
        if (job == null) {
            conn.send(new ClientMessages.ErrorResponse("Unknown job: " + jobId));
        } else {
            conn.send(new ClientMessages.StatusResponse(job.toInfo()));
        }
    }

    private void handleResult(Connection conn, String jobId) throws IOException {
        ServerJob job = server.getJobs().get(jobId);
        if (job == null) {
            conn.send(new ClientMessages.ErrorResponse("Unknown job: " + jobId));
            return;
        }
        File result = server.getJobs().resultFile(jobId);
        boolean available = job.status == JobStatus.Done && result.exists();
        long size = available ? result.length() : 0;
        conn.send(new ClientMessages.ResultResponse(job.toInfo(), available, size));
        if (available) {
            StreamUtil.sendFile(conn, result);
        }
    }

    private void handleAbort(Connection conn, String jobId) throws IOException {
        ServerJob job = server.getJobs().get(jobId);
        if (job == null) {
            conn.send(new ClientMessages.AbortResponse(false, "Unknown job: " + jobId));
            return;
        }
        if (job.status == JobStatus.Done || job.status == JobStatus.Failed) {
            conn.send(new ClientMessages.AbortResponse(false,
                "Job already finished: " + job.status));
            return;
        }
        server.getJobs().setStatus(job, JobStatus.Aborted, "aborted by user", null);
        conn.send(new ClientMessages.AbortResponse(true, "aborted"));
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closing
        }
    }
}
