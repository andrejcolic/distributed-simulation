package rs.ac.bg.etf.kdp.client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import rs.ac.bg.etf.kdp.common.Connection;
import rs.ac.bg.etf.kdp.common.JobInfo;
import rs.ac.bg.etf.kdp.common.JobSpec;
import rs.ac.bg.etf.kdp.common.JobType;
import rs.ac.bg.etf.kdp.common.StreamUtil;
import rs.ac.bg.etf.kdp.common.msg.ClientMessages;
import rs.ac.bg.etf.kdp.common.msg.Message;

/**
 * Reusable client API toward the central server. Each call opens its own short-lived
 * connection and closes it, so the client owns no long-lived link — it may disconnect and
 * reconnect at any time and still ask for results later by job id (Test 2).
 *
 * <p>If the host/port points at a non-protocol server (e.g. a web server, Test 5), the
 * handshake fails and the call throws {@link IOException} with a clear reason instead of hanging.
 */
public final class ClientSession {

    private final String host;
    private final int port;

    public ClientSession(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** Reads two files and builds a {@link JobSpec}. */
    public static JobSpec buildSpec(String componentsPath, String connectionsPath,
                                    JobType type, long endTime, String outputName)
            throws IOException {
        List<String> components = Files.readAllLines(
            new File(componentsPath).toPath(), StandardCharsets.UTF_8);
        List<String> connections = Files.readAllLines(
            new File(connectionsPath).toPath(), StandardCharsets.UTF_8);
        return new JobSpec(components, connections, type, endTime, outputName);
    }

    public String submit(JobSpec spec) throws IOException {
        try (Connection conn = Connection.connect(host, port)) {
            conn.send(new ClientMessages.SubmitJobRequest(spec));
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
     * Fetches the result into {@code dest} if it is available. Returns the {@link JobInfo}
     * header; {@code dest} is written only when {@link JobInfo#hasResult()} (i.e. the response
     * advertised the file).
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
