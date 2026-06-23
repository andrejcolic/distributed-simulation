package worker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import sleep.simulation.Netlist;

/**
 * Builds a {@link Netlist} from files using the given framework's reflection loader, reading line
 * by line so large inputs (Test 7) are never loaded whole into memory. A worker builds only its
 * own components but adds <b>all</b> connections, so {@code Netlist.transform} can route events to
 * remote components too.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class NetlistBuilder {

    private NetlistBuilder() {
    }

    public static Netlist build(File componentsFile, File connectionsFile) throws IOException {
        Netlist netlist = new Netlist();

        int expected = 0;
        try (BufferedReader in = reader(componentsFile)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (isBlank(line)) {
                    continue;
                }
                expected++;
                netlist.addComponent(line.trim().split("\\s+"));
            }
        }
        // Netlist.addComponent swallows reflection errors (printStackTrace) and just skips the
        // component. Detect that here so a bad class name surfaces as a failure (Test 6).
        int loaded = netlist.getComponents().size();
        if (loaded < expected) {
            throw new IllegalStateException("Failed to load components by reflection: expected "
                + expected + ", loaded " + loaded
                + " (check class names on the worker classpath).");
        }

        // The first connection line is the HEADER and is skipped (as in TestG).
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader in = reader(connectionsFile)) {
            String line;
            boolean first = true;
            while ((line = in.readLine()) != null) {
                if (first) {
                    first = false;
                    continue;
                }
                if (isBlank(line)) {
                    continue;
                }
                rows.add(line.trim().split("\\s+"));
            }
        }
        netlist.addConnection(rows.toArray(new String[0][]));

        return netlist;
    }

    private static BufferedReader reader(File file) throws IOException {
        return new BufferedReader(new InputStreamReader(
            new FileInputStream(file), StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
