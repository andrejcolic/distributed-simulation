package client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local, on-disk store of job tickets (jobId + output name). Lets the client recover what to
 * ask for after it is killed and restarted (Test 2). One line per ticket: {@code jobId\toutputName}.
 */
public final class TicketStore {

    private final Path file;
    private final Map<String, String> tickets = new LinkedHashMap<>(); // jobId -> outputName

    public TicketStore(String path) {
        this.file = new File(path).toPath();
        load();
    }

    public synchronized void add(String jobId, String outputName) {
        tickets.put(jobId, outputName == null ? "" : outputName);
        save();
    }

    public synchronized List<String> jobIds() {
        return new ArrayList<>(tickets.keySet());
    }

    public synchronized String outputName(String jobId) {
        return tickets.get(jobId);
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\t", 2);
                tickets.put(parts[0], parts.length > 1 ? parts[1] : "");
            }
        } catch (IOException e) {
            System.err.println("Could not read tickets: " + e.getMessage());
        }
    }

    private void save() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, String> e : tickets.entrySet()) {
                lines.add(e.getKey() + "\t" + e.getValue());
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not save tickets: " + e.getMessage());
        }
    }
}
