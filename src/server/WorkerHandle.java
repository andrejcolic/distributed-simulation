package rs.ac.bg.etf.kdp.server;

import rs.ac.bg.etf.kdp.common.Connection;

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

    WorkerHandle(String id, String name, Connection connection,
                 String peerHost, int peerPort, int capacity) {
        this.id = id;
        this.name = name;
        this.connection = connection;
        this.peerHost = peerHost;
        this.peerPort = peerPort;
        this.capacity = capacity;
    }

    boolean hasFreeSlot() {
        return active < capacity;
    }
}
