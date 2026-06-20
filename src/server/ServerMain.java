package rs.ac.bg.etf.kdp.server;

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
        // TODO: start the central server (accept connections, JobManager, WorkerRegistry,
        //       HeartbeatMonitor, ServerLog, ServerGUI).
        System.out.println("CentralServer — skeleton, port " + port);
    }
}
