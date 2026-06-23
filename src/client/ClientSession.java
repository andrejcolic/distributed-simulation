package client;

import java.io.File;
import java.io.IOException;

import common.Connection;
import common.JobInfo;
import common.JobSpec;
import common.JobType;
import common.StreamUtil;
import common.msg.ClientMessages;
import common.msg.Message;

/**
 * Reusable client API toward the central server. Each call opens its own short-lived connection
 * and closes it, so the client owns no long-lived link — it may disconnect and reconnect at any
 * time and still ask for results later by job id (Test 2).
 *
 * <p>Large files are streamed in chunks (Test 7): submission streams the two input files and result
 * retrieval streams the output file, so nothing is held whole in memory.
 *
 * <p>If the host/port points at a non-protocol server (e.g. a web server, Test 5), the handshake
 * fails and the call throws {@link IOException} with a clear reason instead of hanging.
 */
public final class ClientSession {

    private final String host;
    private final int port;

    public ClientSession(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static JobSpec spec(JobType type, long endTime, String outputName) {
        return new JobSpec(type, endTime, outputName);
    }

    /** Submits a job: sends the metadata, then streams the two input files. Returns the job id. */
    public String submit(JobSpec spec, File componentsFile, File connectionsFile)
            throws IOException {
        try (Connection conn = Connection.connect(host, port)) {
            conn.send(new ClientMessages.SubmitJobRequest(spec));
            StreamUtil.sendFile(conn, componentsFile);
            StreamUtil.sendFile(conn, connectionsFile);
            Message resp = receive(conn);
            if (resp instanceof ClientMessages.SubmitJobResponse) {
                return ((ClientMessages.SubmitJobResponse) resp).jobId;
            }
            throw new IOException(errorText(resp, "submit"));
        }
    }

    public JobInfo status(String jobId) throws IOException {
        try (Connection conn = Connection.connect(host, port)) {
            conn.send(new ClientMessages.StatusRequest(jobId));
            Message resp = receive(conn);
            if (resp instanceof ClientMessages.StatusResponse) {
                return ((ClientMessages.StatusResponse) resp).info;
            }
            throw new IOException(errorText(resp, "status"));
        }
    }

    /**
     * Fetches the result into {@code dest} if it is available (streamed in chunks). Returns the
     * {@link JobInfo} header; {@code dest} is written only when the result is advertised.
     */
    public JobInfo fetchResult(String jobId, File dest) throws IOException {
        try (Connection conn = Connection.connect(host, port)) {
            conn.send(new ClientMessages.ResultRequest(jobId));
            Message resp = receive(conn);
            if (!(resp instanceof ClientMessages.ResultResponse)) {
                throw new IOException(errorText(resp, "result"));
            }
            ClientMessages.ResultResponse r = (ClientMessages.ResultResponse) resp;
            if (r.available) {
                StreamUtil.receiveFile(conn, dest);
            }
            return r.info;
        }
    }

    public boolean abort(String jobId) throws IOException {
        try (Connection conn = Connection.connect(host, port)) {
            conn.send(new ClientMessages.AbortRequest(jobId));
            Message resp = receive(conn);
            if (resp instanceof ClientMessages.AbortResponse) {
                return ((ClientMessages.AbortResponse) resp).ok;
            }
            throw new IOException(errorText(resp, "abort"));
        }
    }

    private static Message receive(Connection conn) throws IOException {
        try {
            return conn.receive();
        } catch (ClassNotFoundException e) {
            throw new IOException("Unknown response class", e);
        }
    }

    private static String errorText(Message resp, String op) {
        if (resp instanceof ClientMessages.ErrorResponse) {
            return op + " rejected: " + ((ClientMessages.ErrorResponse) resp).message;
        }
        return "Unexpected response to " + op + ": "
            + (resp == null ? "null" : resp.getClass().getSimpleName());
    }
}
