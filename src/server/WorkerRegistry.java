package server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import common.Connection;

/**
 * Thread-safe registry of currently connected workers.
 */
public final class WorkerRegistry {

    private final Map<String, WorkerHandle> workers = new LinkedHashMap<>();
    private final AtomicLong seq = new AtomicLong(0);

    public synchronized WorkerHandle register(String name, Connection connection,
                                              String peerHost, int peerPort, int capacity) {
        String id = "w" + seq.incrementAndGet();
        WorkerHandle handle = new WorkerHandle(id, name, connection, peerHost, peerPort, capacity);
        workers.put(id, handle);
        return handle;
    }

    /** All workers that currently have a free slot. */
    public synchronized List<WorkerHandle> available() {
        List<WorkerHandle> result = new ArrayList<>();
        for (WorkerHandle w : workers.values()) {
            if (w.hasFreeSlot()) {
                result.add(w);
            }
        }
        return result;
    }

    public synchronized void unregister(WorkerHandle handle) {
        if (handle != null) {
            workers.remove(handle.id);
        }
    }

    /** A worker with a free slot, or {@code null} if all are busy / none connected. */
    public synchronized WorkerHandle findAvailable() {
        for (WorkerHandle w : workers.values()) {
            if (w.hasFreeSlot()) {
                return w;
            }
        }
        return null;
    }

    public synchronized List<WorkerHandle> all() {
        return new ArrayList<>(workers.values());
    }

    public synchronized int size() {
        return workers.size();
    }
}
