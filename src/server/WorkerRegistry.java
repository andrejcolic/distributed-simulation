package server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import common.Connection;

// Thread-safe registry of connected workers without blocking global locks.
public final class WorkerRegistry {

    // Upotrebom ConcurrentHashMap izbegavamo synchronized na celoj mapi
    private final Map<String, WorkerHandle> workers = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(0);

    public WorkerHandle register(String name, Connection connection,
                                 String peerHost, int peerPort, int capacity) {
        String id = "w" + seq.incrementAndGet();
        WorkerHandle handle = new WorkerHandle(id, name, connection, peerHost, peerPort, capacity);
        workers.put(id, handle);
        return handle;
    }

    public List<WorkerHandle> available() {
        List<WorkerHandle> result = new ArrayList<>();
        for (WorkerHandle w : workers.values()) {
            if (w.hasFreeSlot()) {
                result.add(w);
            }
        }
        return result;
    }

    public void unregister(WorkerHandle handle) {
        if (handle != null) {
            workers.remove(handle.id);
        }
    }

    // Radnik sa slobodnim slotom, ili null
    public WorkerHandle findAvailable() {
        for (WorkerHandle w : workers.values()) {
            if (w.hasFreeSlot()) {
                return w;
            }
        }
        return null;
    }

    public List<WorkerHandle> all() {
        return new ArrayList<>(workers.values());
    }

    public int size() {
        return workers.size();
    }
}