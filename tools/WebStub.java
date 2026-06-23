import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Minimal stand-in for a web server: accepts a connection and replies with an HTTP response
 * (it does not speak the KDP protocol). Used to verify that the client detects a wrong protocol
 * (Test 5) instead of hanging.
 *
 *   java -cp bin WebStub <port>
 */
public class WebStub {
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("WebStub listening on " + port);
            while (true) {
                Socket s = ss.accept();
                new Thread(() -> serve(s)).start();
            }
        }
    }

    private static void serve(Socket s) {
        try (Socket socket = s) {
            // A real web server would parse the request line; this just sends a canned response.
            OutputStream out = socket.getOutputStream();
            String body = "<html>hello</html>";
            String response = "HTTP/1.1 200 OK\r\nContent-Length: " + body.length()
                + "\r\nContent-Type: text/html\r\n\r\n" + body;
            out.write(response.getBytes());
            out.flush();
        } catch (IOException ignored) {
            // client went away
        }
    }
}
