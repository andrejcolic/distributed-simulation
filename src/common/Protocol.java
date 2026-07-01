package common;

// Shared network constants.
public final class Protocol {

    public static final int MAGIC = 0x8888888;
    public static final int DEFAULT_SERVER_PORT = 5039;
    public static final int HANDSHAKE_TIMEOUT_MS = 5000;
    public static final int HEARTBEAT_INTERVAL_MS = 2000;
    // Generous so a worker busy with a large job's startup (big file transfer / netlist build / GC
    // pauses) is not falsely declared dead. A real death is usually caught at once by the dropped
    // connection ("connection closed"); this timeout is the fallback for a frozen/unreachable worker.
    public static final int HEARTBEAT_TIMEOUT_MS = 30000;

    private Protocol() {}
}
