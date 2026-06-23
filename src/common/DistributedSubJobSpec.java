package common;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

// One worker's part of a distributed run: its index, the routing table and the peer addresses.
// Input files are downloaded separately, not embedded here.
public class DistributedSubJobSpec implements Serializable {

    private final String jobId;
    private final int workerIndex;
    private final int workerCount;
    private final HashMap<Long, Integer> routing; // componentId -> owning worker index
    private final List<PeerEndpoint> peers;       // index -> endpoint (peers.get(index))
    private final JobType type;
    private final long endTime;

    public DistributedSubJobSpec(String jobId, int workerIndex, int workerCount,
                                 HashMap<Long, Integer> routing, List<PeerEndpoint> peers,
                                 JobType type, long endTime) {
        this.jobId = jobId;
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
