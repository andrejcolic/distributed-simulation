package rs.ac.bg.etf.kdp.common;

/**
 * Statusi posla na centralnom serveru (vidi postavku, sekcija 7 CLAUDE.md).
 */
public enum JobStatus {
    /** Pristigao na server, nije nikome prosleđen. */
    Ready,
    /** Trenutno se prosleđuje radnoj stanici. */
    Scheduled,
    /** Izvršavanje je u toku. */
    Running,
    /** Uspešno se izvršio. */
    Done,
    /** Nije mogao da se izvrši (izuzetak / neispravna konfiguracija). */
    Failed,
    /** Korisnik je odustao od izvršavanja. */
    Aborted
}
