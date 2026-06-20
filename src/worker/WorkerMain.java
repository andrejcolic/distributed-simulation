package rs.ac.bg.etf.kdp.worker;

import rs.ac.bg.etf.kdp.common.Protocol;

/**
 * Worker entry point. Supports a GUI and a headless mode ({@code --headless}).
 *
 * Usage:
 * {@code java rs.ac.bg.etf.kdp.worker.WorkerMain <serverHost> <serverPort> <parallelJobs> [--headless]}
 */
public class WorkerMain {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println(
                "Usage: WorkerMain <serverHost> <serverPort> <parallelJobs> [--headless]");
            return;
        }
        String serverHost = args[0];
        int serverPort = Integer.parseInt(args[1]);
        int parallelJobs = Integer.parseInt(args[2]);
        boolean headless = args.length >= 4 && "--headless".equals(args[3]);

        // TODO: register with the server (REGISTER capacity), JobRunner per job,
        //       DistributedSimBuffer + PeerRouter, heartbeat. GUI if !headless.
        System.out.println("Worker — skeleton: server=" + serverHost + ":" + serverPort
            + ", capacity=" + parallelJobs + ", headless=" + headless
            + ", magic=" + Integer.toHexString(Protocol.MAGIC));
    }
}
