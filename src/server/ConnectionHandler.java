package server;

import java.io.File;
import java.io.IOException;
import java.net.Socket;

import common.Connection;
import common.ConfigException;
import common.ConfigValidator;
import common.JobStatus;
import common.StreamUtil;
import common.msg.ClientMessages;
import common.msg.Message;
import common.msg.WorkerMessages;

// Handles one accepted connection on its own thread. The first message decides the role:
// RegisterRequest = a worker's control connection, FetchFiles = a worker downloading its input
// split, anything else = a client request. A garbage/non-protocol connection is just logged and
// closed, so the server keeps running.
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
            // wrong/garbage protocol — reject just this connection
            server.getLog().log("Rejected connection from "
                + socket.getRemoteSocketAddress() + ": " + e.getMessage());
            closeSocket();
            return;
        }

        try {
            Message first = conn.receive();
            if (first instanceof WorkerMessages.RegisterRequest) {
                handleWorker(conn, (WorkerMessages.RegisterRequest) first);
            } else if (first instanceof WorkerMessages.FetchFiles) {
                handleFetch(conn, (WorkerMessages.FetchFiles) first);
            } else {
                handleClient(conn, first);
            }
        } catch (java.io.EOFException e) {
            // Peer closed the connection cleanly — normal.
        } catch (IOException | ClassNotFoundException e) {
            server.getLog().log("Connection from " + socket.getRemoteSocketAddress()
                + " ended: " + e.getMessage());
        } finally {
            conn.close();
        }
    }

    /* worker control connection */

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
                server.touch(handle); // any message proves the worker is alive
                if (msg instanceof WorkerMessages.SyncReport) {
                    server.onSyncReport((WorkerMessages.SyncReport) msg);
                } else if (msg instanceof WorkerMessages.SubJobDone) {
                    WorkerMessages.SubJobDone d = (WorkerMessages.SubJobDone) msg;
                    server.onSubJobDone(d.jobId, d.workerIndex, d.states);
                } else if (msg instanceof WorkerMessages.SubJobFailed) {
                    WorkerMessages.SubJobFailed f = (WorkerMessages.SubJobFailed) msg;
                    server.onSubJobFailed(f.jobId, f.workerIndex, f.reason);
                } else if (msg instanceof WorkerMessages.Pong) {
                    // liveness already recorded by touch() above
                }
            }
        } finally {
            server.markDead(handle, "connection closed");
        }
    }

    /* worker file download (dedicated connection) */

    private void handleFetch(Connection conn, WorkerMessages.FetchFiles req) throws IOException {
        File sub = server.getJobs().subFile(req.jobId, req.workerIndex);
        File connections = server.getJobs().connectionsFile(req.jobId);
        // The job may have been torn down and its inputs cleaned up while this worker was still
        // fetching. Tell it explicitly instead of letting sendFile throw and reset the socket.
        if (!sub.exists() || !connections.exists()) {
            server.getLog().log("Fetch for job " + req.jobId + " w" + req.workerIndex
                + " declined: input files no longer available (job torn down).");
            conn.send(new WorkerMessages.FetchResponse(false, "inputs unavailable"));
            return;
        }
        conn.send(new WorkerMessages.FetchResponse(true, null));
        StreamUtil.sendFile(conn, sub);
        StreamUtil.sendFile(conn, connections);
    }

    /* client connection */

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
        // stream the inputs straight to disk
        StreamUtil.receiveFile(conn, server.getJobs().componentsFile(job.id));
        StreamUtil.receiveFile(conn, server.getJobs().connectionsFile(job.id));
        try {
            ConfigValidator.validate(req.spec, server.getJobs().componentsFile(job.id),
                server.getJobs().connectionsFile(job.id));
            server.getJobs().markSchedulable(job);
            server.getJobs().setStatus(job, JobStatus.Ready, null, null);
            conn.send(new ClientMessages.SubmitJobResponse(job.id));
            server.schedule();
        } catch (ConfigException e) {
            // invalid config: the job exists but is Failed with a clear reason
            server.getJobs().setStatus(job, JobStatus.Failed, e.getMessage(), null);
            server.getJobs().cleanupInputs(job.id);
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
        // result size travels in JobInfo, so it isn't repeated here
        conn.send(new ClientMessages.ResultResponse(job.toInfo(), available));
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
        server.abortJob(job);
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
