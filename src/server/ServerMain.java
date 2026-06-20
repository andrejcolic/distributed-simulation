package rs.ac.bg.etf.kdp.server;

import java.io.IOException;

import rs.ac.bg.etf.kdp.common.Protocol;

/**
 * Central server entry point.
 *
 * Usage: {@code java rs.ac.bg.etf.kdp.server.ServerMain [serverPort]}
 */
public class ServerMain {

    public static void main(String[] args) {
        int port = Protocol.DEFAULT_SERVER_PORT;
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }
        CentralServer server = new CentralServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Server could not start on port " + port + ": " + e.getMessage());
        }
    }
}
