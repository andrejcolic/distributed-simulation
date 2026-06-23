package common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

// Structural validation of a job's input files (read line by line). Catches bad input up front,
// since Netlist.addComponent would otherwise just printStackTrace and skip a broken component.
public final class ConfigValidator {

    private ConfigValidator() {
    }

    public static void validate(JobSpec spec, File componentsFile, File connectionsFile)
            throws ConfigException {
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

        Set<Long> ids = validateComponents(componentsFile);
        validateConnections(connectionsFile, ids);
    }

    private static Set<Long> validateComponents(File file) throws ConfigException {
        Set<Long> ids = new HashSet<>();
        int row = 0;
        try (BufferedReader in = reader(file)) {
            String line;
            while ((line = in.readLine()) != null) {
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
        } catch (IOException e) {
            throw new ConfigException("Cannot read components file: " + e.getMessage());
        }
        if (ids.isEmpty()) {
            throw new ConfigException("No valid component found.");
        }
        return ids;
    }

    private static void validateConnections(File file, Set<Long> ids) throws ConfigException {
        int row = 0;
        try (BufferedReader in = reader(file)) {
            String line;
            while ((line = in.readLine()) != null) {
                row++;
                if (row == 1) {
                    continue; // header row
                }
                if (isBlank(line)) {
                    continue;
                }
                String[] t = line.trim().split("\\s+");
                if (t.length != 4) {
                    throw new ConfigException("Connection (row " + row
                        + "): expected 4 fields 'srcID srcPort dstID dstPort', got "
                        + t.length + ": \"" + line + "\".");
                }
                long srcId = parseRef(t[0], "srcID", row);
                parseRef(t[1], "srcPort", row);
                long dstId = parseRef(t[2], "dstID", row);
                parseRef(t[3], "dstPort", row);
                if (!ids.contains(srcId)) {
                    throw new ConfigException("Connection (row " + row
                        + "): srcID " + srcId + " does not exist among the components.");
                }
                if (!ids.contains(dstId)) {
                    throw new ConfigException("Connection (row " + row
                        + "): dstID " + dstId + " does not exist among the components.");
                }
            }
            if (row == 0) {
                throw new ConfigException("Connections file is empty (missing at least the header).");
            }
        } catch (IOException e) {
            throw new ConfigException("Cannot read connections file: " + e.getMessage());
        }
    }

    private static BufferedReader reader(File file) throws IOException {
        return new BufferedReader(new InputStreamReader(
            new FileInputStream(file), StandardCharsets.UTF_8));
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
