package server;

import common.Connection;

/**
 * Server-side handle for one registered worker: its connection, peer listener address (so other
 * workers can reach it), declared parallel capacity, and the number of jobs currently running.
 */
public class WorkerHandle {

    final String id;
    final String name;
    final Connection connection;
    final String peerHost;
    final int peerPort;
    final int capacity;
    int active;

    /** Last time anything was heard from this worker (heartbeat liveness). */
    volatile long lastSeen;
    /** Set once when the worker is declared lost, so loss is handled exactly once. */
    volatile boolean dead;

    WorkerHandle(String id, String name, Connection connection,
                 String peerHost, int peerPort, int capacity) {
        this.id = id;
        this.name = name;
        this.connection = connection;
        this.peerHost = peerHost;
        this.peerPort = peerPort;
        this.capacity = capacity;
        this.lastSeen = System.currentTimeMillis();
    }

    void touch() {
        lastSeen = System.currentTimeMillis();
    }

    boolean hasFreeSlot() {
        return active < capacity;
    }
}
