package server;

import common.Connection;

// Server-side handle for one worker: its connection, peer address, capacity, and active-job count.
public class WorkerHandle {

    final String id;
    final String name;
    final Connection connection;
    final String peerHost;
    final int peerPort;
    final int capacity;
    int active;

    volatile long lastSeen;   // last time anything was heard (heartbeat)
    volatile boolean dead;    // set once when declared lost

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
