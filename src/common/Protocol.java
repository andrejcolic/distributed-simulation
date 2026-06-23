package common;

// Shared network constants.
public final class Protocol {

    public static final int MAGIC = 0x8888888;            // marks a connection as ours
    public static final int DEFAULT_SERVER_PORT = 5050;
    public static final int HANDSHAKE_TIMEOUT_MS = 5000;
    public static final int HEARTBEAT_INTERVAL_MS = 2000;  // ping interval
    public static final int HEARTBEAT_TIMEOUT_MS = 6000;   // declare a worker dead after this silence

    private Protocol() {}
}
