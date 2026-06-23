package worker;

import java.awt.GraphicsEnvironment;
import java.io.IOException;

/**
 * Worker entry point. Shows the AWT GUI by default; pass {@code --headless} (or run on a headless
 * host) for the windowless runtime driven purely from the command line.
 *
 * Usage:
 * {@code java worker.WorkerMain <serverHost> <serverPort> <parallelJobs> [--headless]}
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
        boolean headless = GraphicsEnvironment.isHeadless()
            || (args.length >= 4 && "--headless".equals(args[3]));

        Worker worker = new Worker(serverHost, serverPort, parallelJobs);
        Runtime.getRuntime().addShutdownHook(new Thread(worker::stop));

        if (headless) {
            runWorker(worker);
        } else {
            new WorkerGUI(worker, serverHost, serverPort).showUi();
            // Run the blocking worker loop off the AWT thread so the GUI stays responsive.
            Thread t = new Thread(() -> runWorker(worker), "worker-runtime");
            t.start();
        }
    }

    private static void runWorker(Worker worker) {
        try {
            worker.run();
        } catch (IOException e) {
            System.err.println("Worker could not connect to the server: " + e.getMessage());
        }
    }
}
