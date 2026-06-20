package rs.ac.bg.etf.kdp.common;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Job-independent structural validation of a job (Test 6). It does not instantiate
 * components (the worker does that via reflection); it only checks the format that would
 * otherwise <i>silently</i> fail in {@code Netlist.addComponent} (which merely calls
 * {@code printStackTrace}).
 *
 * <p>Checks: components exist, numeric id and a present FQN class, unique ids, a header plus
 * 4 numeric fields per connection with valid references, and a sensible end time and output name.
 */
public final class ConfigValidator {

    private ConfigValidator() {
    }

    public static void validate(JobSpec spec) throws ConfigException {
        if (spec == null) {
            throw new ConfigException("Job is not set (spec == null).");
        }
        if (spec.getEndTime() <= 0) {
            throw new ConfigException("End logical time must be > 0 (got: "
                + spec.getEndTime() + ").");
        }
        if (isBlank(spec.getOutputName())) {
            throw new ConfigException("Output file name is not set.");
        }

        Set<Long> ids = validateComponents(spec.getComponentLines());
        validateConnections(spec.getConnectionLines(), ids);
    }

    private static Set<Long> validateComponents(List<String> lines) throws ConfigException {
        if (lines == null || lines.isEmpty()) {
            throw new ConfigException("Components file is empty.");
        }
        Set<Long> ids = new HashSet<>();
        int row = 0;
        for (String line : lines) {
            row++;
            if (isBlank(line)) {
                continue;
            }
            String[] t = line.trim().split("\\s+");
            if (t.length < 2) {
                throw new ConfigException("Component (row " + row
                    + "): expected at least 'id FQN', got: \"" + line + "\".");
            }
            long id;
            try {
                id = Long.parseLong(t[0]);
            } catch (NumberFormatException e) {
                throw new ConfigException("Component (row " + row
                    + "): id is not an integer: \"" + t[0] + "\".");
            }
            if (!ids.add(id)) {
                throw new ConfigException("Component (row " + row
                    + "): duplicate id " + id + ".");
            }
            if (isBlank(t[1]) || !t[1].contains(".")) {
                throw new ConfigException("Component (row " + row
                    + "): invalid class name (FQN): \"" + t[1] + "\".");
            }
        }
        if (ids.isEmpty()) {
            throw new ConfigException("No valid component found.");
        }
        return ids;
    }

    private static void validateConnections(List<String> lines, Set<Long> ids)
            throws ConfigException {
        if (lines == null || lines.isEmpty()) {
            throw new ConfigException("Connections file is empty (missing at least the header).");
        }
        // The first row is the HEADER and is skipped (this is how TestG works too).
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isBlank(line)) {
                continue;
            }
            String[] t = line.trim().split("\\s+");
            if (t.length != 4) {
                throw new ConfigException("Connection (row " + (i + 1)
                    + "): expected 4 fields 'srcID srcPort dstID dstPort', got "
                    + t.length + ": \"" + line + "\".");
            }
            long srcId = parseRef(t[0], "srcID", i + 1);
            parseRef(t[1], "srcPort", i + 1);
            long dstId = parseRef(t[2], "dstID", i + 1);
            parseRef(t[3], "dstPort", i + 1);
            if (!ids.contains(srcId)) {
                throw new ConfigException("Connection (row " + (i + 1)
                    + "): srcID " + srcId + " does not exist among the components.");
            }
            if (!ids.contains(dstId)) {
                throw new ConfigException("Connection (row " + (i + 1)
                    + "): dstID " + dstId + " does not exist among the components.");
            }
        }
    }

    private static long parseRef(String s, String field, int row) throws ConfigException {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new ConfigException("Connection (row " + row + "): field " + field
                + " is not an integer: \"" + s + "\".");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
