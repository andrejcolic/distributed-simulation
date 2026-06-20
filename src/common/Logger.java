package rs.ac.bg.etf.kdp.common;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Simple thread-safe log: writes to a file (survives restarts) and to the console, and
 * optionally forwards lines to a GUI via a listener. Used by the server (section 7),
 * but is general-purpose.
 */
public final class Logger {

    private static final DateTimeFormatter TS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path file;
    private final List<Consumer<String>> listeners = new ArrayList<>();

    public Logger(String filePath) {
        this.file = Paths.get(filePath);
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            System.err.println("Cannot prepare the log file: " + e.getMessage());
        }
    }

    /** Register a GUI listener (e.g. appending to a JTextArea). */
    public synchronized void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    public synchronized void log(String message) {
        String line = LocalDateTime.now().format(TS) + "  " + message;
        System.out.println(line);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(file,
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            pw.println(line);
        } catch (IOException e) {
            System.err.println("Error writing the log: " + e.getMessage());
        }
        for (Consumer<String> l : listeners) {
            try {
                l.accept(line);
            } catch (RuntimeException ignored) {
                // a GUI listener must not bring down the log
            }
        }
    }
}
