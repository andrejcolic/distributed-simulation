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

// Socket wrapper: a magic handshake, then messages over ObjectStreams. A wrong or foreign
// protocol fails the handshake (with a connect/read timeout) so the connection is rejected, not
// hung. Both sides write the magic then read it; the output stream is created before the input
// stream to avoid a deadlock on the ObjectStream headers.
public final class Connection implements AutoCloseable {

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    private Connection(Socket socket, ObjectOutputStream out, ObjectInputStream in) {
        this.socket = socket;
        this.out = out;
        this.in = in;
    }

    // Client side: connect and run the handshake (with timeouts).
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

    // Server side: an accepted socket goes through the same handshake.
    public static Connection accept(Socket socket) throws IOException {
        try {
            return handshake(socket);
        } catch (IOException e) {
            closeQuietly(socket);
            throw e;
        }
    }

    private static Connection handshake(Socket socket) throws IOException {
        // timeout so a foreign protocol can't hang us
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

        socket.setSoTimeout(0); // handshake OK; drop the timeout for long transfers

        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
        oos.flush();
        ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
        return new Connection(socket, oos, ois);
    }

    public synchronized void send(Message message) throws IOException {
        out.writeObject(message);
        out.flush();
        out.reset(); // don't let the stream's object table grow on long connections
    }

    // Blocks until a message arrives; throws IOException on disconnect/EOF.
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
