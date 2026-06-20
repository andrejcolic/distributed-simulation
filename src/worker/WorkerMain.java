package rs.ac.bg.etf.kdp.worker;

import rs.ac.bg.etf.kdp.common.Protocol;

/**
 * Ulazna tačka radne stanice. Podržava GUI i headless režim ({@code --headless}).
 *
 * Upotreba:
 * {@code java rs.ac.bg.etf.kdp.worker.WorkerMain <serverHost> <serverPort> <parallelJobs> [--headless]}
 */
public class WorkerMain {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println(
                "Upotreba: WorkerMain <serverHost> <serverPort> <parallelJobs> [--headless]");
            return;
        }
        String serverHost = args[0];
        int serverPort = Integer.parseInt(args[1]);
        int parallelJobs = Integer.parseInt(args[2]);
        boolean headless = args.length >= 4 && "--headless".equals(args[3]);

        // TODO: registracija na server (REGISTER capacity), JobRunner po poslu,
        //       DistributedSimBuffer + PeerRouter, heartbeat. GUI ako !headless.
        System.out.println("RadnaStanica — skelet: server=" + serverHost + ":" + serverPort
            + ", kapacitet=" + parallelJobs + ", headless=" + headless
            + ", magic=" + Integer.toHexString(Protocol.MAGIC));
    }
}
