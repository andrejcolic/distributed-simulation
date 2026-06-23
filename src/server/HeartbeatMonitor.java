package server;

import java.io.IOException;

import common.Protocol;
import common.msg.WorkerMessages;

// Pings every worker periodically and declares one dead if a ping fails or nothing is heard within
// HEARTBEAT_TIMEOUT_MS. The server then restarts that worker's job on the remaining workers.
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
