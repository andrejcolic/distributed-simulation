package server;

import java.awt.GraphicsEnvironment;
import java.io.IOException;

import common.Protocol;

// Server entry point. Shows the AWT GUI by default; --headless (or a headless host) starts without
// a window. Usage: java server.ServerMain [serverPort] [--headless]
public class ServerMain {

    public static void main(String[] args) {
        int port = Protocol.DEFAULT_SERVER_PORT;
        boolean headless = GraphicsEnvironment.isHeadless();
        for (String a : args) {
            if ("--headless".equals(a)) {
                headless = true;
            } else {
                try {
                    port = Integer.parseInt(a);
                } catch (NumberFormatException ignored) {
                    // not the port argument
                }
            }
        }

        CentralServer server = new CentralServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        if (!headless) {
            new ServerGUI(server, port).showUi();
        }
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Server could not start on port " + port + ": " + e.getMessage());
        }
    }
}
