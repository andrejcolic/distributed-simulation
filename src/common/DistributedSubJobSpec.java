package common;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

// One worker's part of a distributed run: its index, the routing table and the peer addresses.
// Input files are downloaded separately, not embedded here.
public class DistributedSubJobSpec implements Serializable {

    private final String jobId;
    private final int attempt;                    // restart generation (0, 1, 2, ...)
    private final int workerIndex;
    private final int workerCount;
    private final HashMap<Long, Integer> routing; // componentId -> owning worker index
    private final List<PeerEndpoint> peers;       // index -> endpoint (peers.get(index))
    private final JobType type;
    private final long endTime;

    public DistributedSubJobSpec(String jobId, int attempt, int workerIndex, int workerCount,
                                 HashMap<Long, Integer> routing, List<PeerEndpoint> peers,
                                 JobType type, long endTime) {
        this.jobId = jobId;
        this.attempt = attempt;
        this.workerIndex = workerIndex;
        this.workerCount = workerCount;
        this.routing = routing;
        this.peers = peers;
        this.type = type;
        this.endTime = endTime;
    }

    public String getJobId() {
        return jobId;
    }

    // Restart generation: distinguishes this run's peer connections from a previous (failed) run
    // that shares the same jobId, so stale peer connections are rejected instead of stalling it.
    public int getAttempt() {
        return attempt;
    }

    public int getWorkerIndex() {
        return workerIndex;
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public HashMap<Long, Integer> getRouting() {
        return routing;
    }

    public List<PeerEndpoint> getPeers() {
        return peers;
    }

    public JobType getType() {
        return type;
    }

    public long getEndTime() {
        return endTime;
    }
}
