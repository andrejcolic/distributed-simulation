package server;

import java.io.IOException;

import common.Protocol;
import common.msg.WorkerMessages;

/**
 * Periodically pings every registered worker and declares a worker dead if a ping cannot be sent
 * or nothing has been heard from it within {@link Protocol#HEARTBEAT_TIMEOUT_MS}. A dead worker is
 * reported to the server, which restarts that worker's job on the remaining workers (Test 3).
 */
public final class HeartbeatMonitor implements Runnable {

    private final CentralServer server;
    private volatile boolean running = true;

    public HeartbeatMonitor(CentralServer server) {
        this.server = server;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(Protocol.HEARTBEAT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            long now = System.currentTimeMillis();
            for (WorkerHandle w : server.getWorkers().all()) {
                if (w.dead) {
                    continue;
                }
                if (now - w.lastSeen > Protocol.HEARTBEAT_TIMEOUT_MS) {
                    server.markDead(w, "heartbeat timeout");
                    continue;
                }
                try {
                    w.connection.send(new WorkerMessages.Ping());
                } catch (IOException e) {
                    server.markDead(w, "ping failed: " + e.getMessage());
                }
            }
        }
    }
}
