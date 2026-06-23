package common;

public enum JobType {
    SINGLETHREAD, MULTITHREAD, OPTIMISTIC;

    public static JobType from(String s) {
        if (s == null) throw new IllegalArgumentException("Simulation type is not set.");

        switch (s.trim().toUpperCase()) {
            case "SINGLETHREAD": case "SINGLE": case "1": return SINGLETHREAD;
            case "MULTITHREAD":  case "MULTI":  case "2": return MULTITHREAD;
            case "OPTIMISTIC":   case "OPT":    case "3": return OPTIMISTIC;
            default: throw new IllegalArgumentException("Unknown simulation type: " + s);
        }
    }
}