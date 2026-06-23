package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import common.DistributedSubJobSpec;
import common.JobSpec;
import common.PeerEndpoint;

// Splits a job into one sub-job per worker: reads the components file line by line and writes each
// line to one worker's split round-robin (≈ equal counts). Every worker gets all connections plus a
// routing table (componentId -> worker index) and the peer endpoints.
public final class Partitioner {

    private Partitioner() {
    }

    public static List<DistributedSubJobSpec> partition(ServerJob job, List<WorkerHandle> workers,
                                                        JobManager jobs) throws IOException {
        JobSpec spec = job.spec;
        int k = workers.size();

        List<PeerEndpoint> peers = new ArrayList<>(k);
        for (WorkerHandle w : workers) {
            peers.add(new PeerEndpoint(w.peerHost, w.peerPort));
        }

        PrintWriter[] out = new PrintWriter[k];
        HashMap<Long, Integer> routing = new HashMap<>();
        try {
            for (int i = 0; i < k; i++) {
                out[i] = new PrintWriter(jobs.subFile(job.id, i), StandardCharsets.UTF_8.name());
            }
            try (BufferedReader in = new BufferedReader(new InputStreamReader(
                    new FileInputStream(jobs.componentsFile(job.id)), StandardCharsets.UTF_8))) {
                String line;
                int next = 0;
                while ((line = in.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    String[] t = line.trim().split("\\s+", 2);
                    long id = Long.parseLong(t[0]);
                    int idx = next % k;
                    next++;
                    out[idx].println(line);
                    routing.put(id, idx);
                }
            }
        } finally {
            for (PrintWriter w : out) {
                if (w != null) {
                    w.close();
                }
            }
        }

        List<DistributedSubJobSpec> subs = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            subs.add(new DistributedSubJobSpec(job.id, i, k,
                routing, peers, spec.getType(), spec.getEndTime()));
        }
        return subs;
    }
}
