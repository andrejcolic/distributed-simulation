package common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Socket;

import common.msg.Message;

/**
 * Socket wrapper with a magic handshake and messages sent over {@code ObjectStream}s.
 *
 * <p>The handshake is the key to robustness:
 * <ul>
 *   <li><b>Test 4</b> (garbage on the server port): wrong magic → {@link ProtocolException},
 *       the server closes that connection and carries on.</li>
 *   <li><b>Test 5</b> (client connecting to a web server): the foreign protocol does not send
 *       our magic or does not reply in time → timeout/{@link ProtocolException}, the client
 *       reports it cleanly.</li>
 * </ul>
 *
 * <p>Both sides first write the magic and then read it (symmetrically), then create the
 * {@code ObjectOutputStream} before the {@code ObjectInputStream} (avoids a deadlock on headers).
 */
public final class Connection implements AutoCloseable {

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    private Connection(Socket socket, ObjectOutputStream out, ObjectInputStream in) {
        this.socket = socket;
        this.out = out;
        this.in = in;
    }

    /** Client side: connect and run the handshake with timeouts. */
    public static Connection connect(String host, int port) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), Protocol.HANDSHAKE_TIMEOUT_MS);
            return handshake(socket);
        } catch (IOException e) {
            closeQuietly(socket);
            throw e;
        }
    }

    /** Server side: an accepted socket goes through the same handshake. */
    public static Connection accept(Socket socket) throws IOException {
        try {
            return handshake(socket);
        } catch (IOException e) {
            closeQuietly(socket);
            throw e;
        }
    }

    private static Connection handshake(Socket socket) throws IOException {
        // Timeout during the handshake so a foreign/invalid protocol cannot hang the connection.
        socket.setSoTimeout(Protocol.HANDSHAKE_TIMEOUT_MS);

        DataOutputStream dout = new DataOutputStream(socket.getOutputStream());
        dout.writeInt(Protocol.MAGIC);
        dout.flush();

        DataInputStream din = new DataInputStream(socket.getInputStream());
        int magic = din.readInt();
        if (magic != Protocol.MAGIC) {
            throw new ProtocolException("Wrong protocol (magic=0x"
                + Integer.toHexString(magic) + "), expected 0x"
                + Integer.toHexString(Protocol.MAGIC));
        }

        // Handshake OK — clear the read timeout for long-running operations.
        socket.setSoTimeout(0);

        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
        oos.flush();
        ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
        return new Connection(socket, oos, ois);
    }

    public synchronized void send(Message message) throws IOException {
        out.writeObject(message);
        out.flush();
        // Prevent the ObjectOutputStream reference table from growing (memory leak on
        // long-lived connections / large transfers — Test 7).
        out.reset();
    }

    /** Blocks until a message arrives; throws {@link IOException} on disconnect/EOF. */
    public Message receive() throws IOException, ClassNotFoundException {
        return (Message) in.readObject();
    }

    public Socket getSocket() {
        return socket;
    }

    @Override
    public void close() {
        closeQuietly(socket);
    }

    private static void closeQuietly(Socket socket) {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // closing the connection — ignore errors
        }
    }
}
