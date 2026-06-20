package rs.ac.bg.etf.kdp.common;

/**
 * Simulation type chosen by the client. Maps to a concrete strategy from the given
 * framework ({@code SimulatorSinglethread / SimulatorMultithread / SimulatorOptimistic}).
 */
public enum JobType {
    SINGLETHREAD,
    MULTITHREAD,
    OPTIMISTIC;

    /** Lenient parsing from user input (Test 6 — invalid type → exception). */
    public static JobType from(String s) {
        if (s == null) {
            throw new IllegalArgumentException("Simulation type is not set.");
        }
        switch (s.trim().toUpperCase()) {
            case "SINGLETHREAD":
            case "SINGLE":
            case "1":
                return SINGLETHREAD;
            case "MULTITHREAD":
            case "MULTI":
            case "2":
                return MULTITHREAD;
            case "OPTIMISTIC":
            case "OPT":
            case "3":
                return OPTIMISTIC;
            default:
                throw new IllegalArgumentException("Unknown simulation type: " + s);
        }
    }
}
