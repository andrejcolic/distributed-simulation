package common;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import common.msg.FileChunk;

// Streams files in chunks so a large file is never held whole in memory.
public final class StreamUtil {

    public static final int CHUNK = 64 * 1024; // 64 KB

    private StreamUtil() {}

    // Sends a file as a sequence of chunks, ending with a null-data chunk. Returns bytes sent.
    public static long sendFile(Connection conn, File file) throws IOException {
        long total = 0;
        try (InputStream rawIn = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[CHUNK];
            int n;
            while ((n = rawIn.read(buf)) != -1) {
                conn.send(new FileChunk(Arrays.copyOf(buf, n))); // exact-size chunk
                total += n;
            }
            conn.send(new FileChunk(null)); // null data = end-of-file marker
        }
        return total;
    }

    // Receives chunks (until the null-data marker) into a file. Returns bytes received.
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
                byte[] data = ((FileChunk) msg).data;
                if (data == null) {
                    break; // null data marks end-of-file
                }
                out.write(data);
                total += data.length;
            }
        }
        return total;
    }

    // Copy a stream to another in chunks.
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
