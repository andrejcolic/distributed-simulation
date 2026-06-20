package rs.ac.bg.etf.kdp.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import rs.ac.bg.etf.kdp.common.DistributedSubJobSpec;
import rs.ac.bg.etf.kdp.common.JobSpec;
import rs.ac.bg.etf.kdp.common.PeerEndpoint;

/**
 * Splits a job into one sub-job per worker. Components are distributed round-robin (so each
 * worker gets roughly the same count); every worker receives all connections plus a routing table
 * ({@code componentId -> worker index}) and the peer endpoints.
 */
public final class Partitioner {

    private Partitioner() {
    }

    public static List<DistributedSubJobSpec> partition(ServerJob job, List<WorkerHandle> workers) {
        JobSpec spec = job.spec;
        int k = workers.size();

        List<PeerEndpoint> peers = new ArrayList<>(k);
        for (WorkerHandle w : workers) {
            peers.add(new PeerEndpoint(w.peerHost, w.peerPort));
        }

        // Per-worker component line buckets + routing table.
        List<List<String>> buckets = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            buckets.add(new ArrayList<>());
        }
        HashMap<Long, Integer> routing = new HashMap<>();
        int next = 0;
        for (String line : spec.getComponentLines()) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            String[] t = line.trim().split("\\s+");
            long id = Long.parseLong(t[0]);
            int idx = next % k;
            next++;
            buckets.get(idx).add(line);
            routing.put(id, idx);
        }

        List<DistributedSubJobSpec> subs = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            subs.add(new DistributedSubJobSpec(job.id, i, k,
                buckets.get(i), spec.getConnectionLines(),
                routing, peers, spec.getType(), spec.getEndTime()));
        }
        return subs;
    }
}
