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

// Client API toward the server. Each call opens a short-lived connection, so the client owns no
// long-lived link and can disconnect/reconnect and still ask for results by job id.
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

    // Sends the job metadata, then streams the two input files. Returns the job id.
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

    // Fetches the result into dest if available (streamed). Returns the JobInfo header; dest is
    // written only when a result is advertised.
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
