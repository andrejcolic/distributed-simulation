package rs.ac.bg.etf.kdp.common;

/**
 * Verzionisane konstante mrežnog protokola (magic + verzija) zajedničke za sve
 * tri komponente. Koristi se za detekciju pogrešnog/garbage protokola
 * (Test 4 — server, Test 5 — klijent).
 */
public final class Protocol {

    /** Magic potpis na početku svake veze — provera da je sagovornik naš protokol. */
    public static final int MAGIC = 0x4B445031; // "KDP1"

    /** Verzija protokola; menja se uz nekompatibilne izmene poruka. */
    public static final int VERSION = 1;

    /** Podrazumevani port centralnog servera. */
    public static final int DEFAULT_SERVER_PORT = 5050;

    /** Timeout (ms) za rukovanje pri povezivanju — sprečava visenje na tuđem serveru. */
    public static final int HANDSHAKE_TIMEOUT_MS = 5000;

    private Protocol() {
    }
}
