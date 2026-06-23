package common;

/**
 * Protocol constants shared by all three programs. The magic signature is used to detect a
 * wrong/garbage protocol (Test 4 — server, Test 5 — client).
 */
public final class Protocol {

    /** Magic signature at the start of every connection — proves the peer speaks our protocol. */
    public static final int MAGIC = 0x8888888;

    /** Default central server port. */
    public static final int DEFAULT_SERVER_PORT = 5050;

    /** Handshake timeout (ms) on connect — prevents hanging on a foreign server. */
    public static final int HANDSHAKE_TIMEOUT_MS = 5000;

    /** How often the server pings each worker (heartbeat). */
    public static final int HEARTBEAT_INTERVAL_MS = 2000;

    /** A worker is declared dead if nothing is heard from it within this window. */
    public static final int HEARTBEAT_TIMEOUT_MS = 6000;

    private Protocol() {
    }
}
