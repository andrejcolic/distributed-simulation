package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.Protocol;

/**
 * Ulazna tačka centralnog servera.
 *
 * Upotreba: {@code java rs.ac.bg.etf.kdp.server.ServerMain [serverPort]}
 */
public class ServerMain {

    public static void main(String[] args) {
        int port = Protocol.DEFAULT_SERVER_PORT;
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }
        // TODO: pokreni CentralniServer (prihvat veza, JobManager, WorkerRegistry,
        //       HeartbeatMonitor, ServerLog, ServerGUI).
        System.out.println("CentralniServer — skelet, port " + port);
    }
}
