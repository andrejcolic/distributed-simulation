package rs.ac.bg.etf.kdp.worker;

import java.io.IOException;

/**
 * Worker entry point. Supports a GUI and a headless mode ({@code --headless}).
 * (The GUI is added later; for now both modes run the same headless runtime.)
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
        // boolean headless = args.length >= 4 && "--headless".equals(args[3]); // GUI added later

        Worker worker = new Worker(serverHost, serverPort, parallelJobs);
        try {
            worker.run();
        } catch (IOException e) {
            System.err.println("Worker could not connect to the server: " + e.getMessage());
        }
    }
}
