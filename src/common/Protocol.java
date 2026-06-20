package rs.ac.bg.etf.kdp.common;

/**
 * Versioned protocol constants (magic + version) shared by all three programs.
 * Used to detect a wrong/garbage protocol (Test 4 — server, Test 5 — client).
 */
public final class Protocol {

    /** Magic signature at the start of every connection — proves the peer speaks our protocol. */
    public static final int MAGIC = 0x4B445031; // "KDP1"

    /** Protocol version; bumped on incompatible message changes. */
    public static final int VERSION = 1;

    /** Default central server port. */
    public static final int DEFAULT_SERVER_PORT = 5050;

    /** Handshake timeout (ms) on connect — prevents hanging on a foreign server. */
    public static final int HANDSHAKE_TIMEOUT_MS = 5000;

    private Protocol() {
    }
}
