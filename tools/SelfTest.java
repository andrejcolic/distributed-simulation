import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

import rs.ac.bg.etf.kdp.common.Connection;
import rs.ac.bg.etf.kdp.common.ConfigException;
import rs.ac.bg.etf.kdp.common.ConfigValidator;
import rs.ac.bg.etf.kdp.common.JobSpec;
import rs.ac.bg.etf.kdp.common.JobType;
import rs.ac.bg.etf.kdp.common.StreamUtil;
import rs.ac.bg.etf.kdp.common.msg.ClientMessages;
import rs.ac.bg.etf.kdp.common.msg.Message;

/**
 * Standalone smoke test for the common layer (handshake, messaging, file streaming,
 * config validation). Not part of any package — run from the default package.
 *
 * Build + run (Windows):
 *   build.bat
 *   javac -cp bin -d bin tools\SelfTest.java
 *   java -cp bin SelfTest
 */
public class SelfTest {

    public static void main(String[] args) throws Exception {
        boolean ok = true;
        ok &= testHandshakeAndStreaming();
        ok &= testGarbageRejected();
        ok &= testConfigValidation();
        System.out.println(ok ? "\nALL TESTS PASSED" : "\nSOME TESTS FAILED");
        if (!ok) {
            System.exit(1);
        }
    }

    /** Handshake + message round-trip + 200 KB file streaming over loopback. */
    private static boolean testHandshakeAndStreaming() throws Exception {
        ServerSocket ss = new ServerSocket(0);
        int port = ss.getLocalPort();
        final long[] received = {-1};
        Thread server = new Thread(() -> {
            try (Socket s = ss.accept(); Connection c = Connection.accept(s)) {
                c.receive(); // StatusRequest
                c.send(new ClientMessages.SubmitJobResponse("job-42"));
                File f = File.createTempFile("recv", ".bin");
                f.deleteOnExit();
                received[0] = StreamUtil.receiveFile(c, f);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        server.start();

        String jobId;
        long sent;
        try (Connection c = Connection.connect("127.0.0.1", port)) {
            c.send(new ClientMessages.StatusRequest("x"));
            Message r = c.receive();
            jobId = ((ClientMessages.SubmitJobResponse) r).jobId;
            File big = File.createTempFile("send", ".bin");
            big.deleteOnExit();
            try (FileOutputStream fo = new FileOutputStream(big)) {
                fo.write(new byte[200_000]);
            }
            sent = StreamUtil.sendFile(c, big);
        }
        server.join();

        boolean pass = "job-42".equals(jobId) && sent == 200_000 && received[0] == 200_000;
        System.out.println("[1] handshake + messaging + streaming: " + (pass ? "OK" : "FAIL")
            + " (jobId=" + jobId + ", sent=" + sent + ", received=" + received[0] + ")");
        return pass;
    }

    /** A non-protocol client (e.g. HTTP) must be rejected — Test 4/5 logic. */
    private static boolean testGarbageRejected() throws Exception {
        ServerSocket ss = new ServerSocket(0);
        int port = ss.getLocalPort();
        final boolean[] rejected = {false};
        Thread server = new Thread(() -> {
            try (Socket s = ss.accept()) {
                try {
                    Connection.accept(s);
                } catch (IOException expected) {
                    rejected[0] = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        server.start();
        try (Socket junk = new Socket("127.0.0.1", port)) {
            junk.getOutputStream().write("GET / HTTP/1.1\r\n\r\n".getBytes());
            junk.getOutputStream().flush();
            Thread.sleep(200);
        }
        server.join();
        System.out.println("[2] garbage protocol rejected (Test 4/5): "
            + (rejected[0] ? "OK" : "FAIL"));
        return rejected[0];
    }

    /** Valid config accepted; invalid config rejected with a clear message — Test 6. */
    private static boolean testConfigValidation() {
        boolean validOk = false;
        try {
            ConfigValidator.validate(new JobSpec(
                Arrays.asList("1 a.b.C Bag 1 2"),
                Arrays.asList("srcID srcPort dstID dstPort", "1 0 1 0"),
                JobType.OPTIMISTIC, 100, "out.txt"));
            validOk = true;
        } catch (ConfigException e) {
            System.out.println("    unexpected rejection: " + e.getMessage());
        }
        boolean invalidRejected = false;
        try {
            ConfigValidator.validate(new JobSpec(
                Arrays.asList("X a.b.C Bag"),
                Arrays.asList("header"),
                JobType.OPTIMISTIC, 100, "out.txt"));
        } catch (ConfigException e) {
            invalidRejected = true;
        }
        boolean pass = validOk && invalidRejected;
        System.out.println("[3] config validation (Test 6): " + (pass ? "OK" : "FAIL"));
        return pass;
    }
}
