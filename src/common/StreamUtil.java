package rs.ac.bg.etf.kdp.common;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import rs.ac.bg.etf.kdp.common.msg.FileChunk;

/**
 * Helpers for streaming files in chunks (Test 7). The whole file is never loaded into
 * memory; all streams are closed in {@code try-with-resources}.
 */
public final class StreamUtil {

    /** Transfer chunk size (64 KB). */
    public static final int CHUNK = 64 * 1024;

    private StreamUtil() {
    }

    /**
     * Send a file over the connection as a sequence of {@link FileChunk}s. The last chunk
     * has {@code last=true}. Returns the number of bytes sent.
     */
    public static long sendFile(Connection conn, File file) throws IOException {
        long total = 0;
        try (InputStream rawIn = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[CHUNK];
            int n;
            while ((n = rawIn.read(buf)) != -1) {
                conn.send(new FileChunk(buf.clone(), n, false));
                total += n;
            }
            conn.send(new FileChunk(new byte[0], 0, true)); // end-of-file marker
        }
        return total;
    }

    /**
     * Receive {@link FileChunk}s until {@code last} arrives and write them to a file.
     * Returns the number of bytes received.
     */
    public static long receiveFile(Connection conn, File target) throws IOException {
        long total = 0;
        File parent = target.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            while (true) {
                Object msg;
                try {
                    msg = conn.receive();
                } catch (ClassNotFoundException e) {
                    throw new IOException("Unknown class during transfer", e);
                }
                if (!(msg instanceof FileChunk)) {
                    throw new IOException("Expected FileChunk, got: "
                        + (msg == null ? "null" : msg.getClass().getName()));
                }
                FileChunk chunk = (FileChunk) msg;
                if (chunk.length > 0) {
                    out.write(chunk.data, 0, chunk.length);
                    total += chunk.length;
                }
                if (chunk.last) {
                    break;
                }
            }
        }
        return total;
    }

    /** Copy a stream to another in chunks (e.g. saving an uploaded file to disk). */
    public static long copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[CHUNK];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            total += n;
        }
        out.flush();
        return total;
    }
}
