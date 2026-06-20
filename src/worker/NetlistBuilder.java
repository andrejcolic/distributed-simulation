package rs.ac.bg.etf.kdp.worker;

import java.util.List;

import rs.ac.bg.etf.sleep.simulation.Netlist;

/**
 * Builds a {@link Netlist} from text lines using the given framework's reflection loader.
 * A worker builds only its own components but adds <b>all</b> connections, so
 * {@code Netlist.transform} can route events to remote components too.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class NetlistBuilder {

    private NetlistBuilder() {
    }

    public static Netlist build(List<String> componentLines, List<String> connectionLines) {
        Netlist netlist = new Netlist();

        int expected = 0;
        for (String line : componentLines) {
            if (isBlank(line)) {
                continue;
            }
            expected++;
            netlist.addComponent(line.trim().split("\\s+"));
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
        int count = Math.max(0, connectionLines.size() - 1);
        String[][] connections = new String[count][];
        for (int i = 0; i < count; i++) {
            connections[i] = connectionLines.get(i + 1).trim().split("\\s+");
        }
        netlist.addConnection(connections);

        return netlist;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
