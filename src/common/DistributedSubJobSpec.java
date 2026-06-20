package rs.ac.bg.etf.kdp.common;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

/**
 * A sub-job assigned to one worker as part of a distributed run. The worker instantiates only
 * <b>its own</b> components but receives <b>all</b> connections (so {@code Netlist.transform}
 * can route across workers) plus a routing table and the peer endpoints.
 */
public class DistributedSubJobSpec implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String jobId;
    private final int workerIndex;
    private final int workerCount;
    private final List<String> componentLines;   // this worker's components only
    private final List<String> connectionLines;  // all connections (with header)
    private final HashMap<Long, Integer> routing; // componentId -> owning worker index
    private final List<PeerEndpoint> peers;       // index -> endpoint (peers.get(index))
    private final JobType type;
    private final long endTime;

    public DistributedSubJobSpec(String jobId, int workerIndex, int workerCount,
                                 List<String> componentLines, List<String> connectionLines,
                                 HashMap<Long, Integer> routing, List<PeerEndpoint> peers,
                                 JobType type, long endTime) {
        this.jobId = jobId;
        this.workerIndex = workerIndex;
        this.workerCount = workerCount;
        this.componentLines = componentLines;
        this.connectionLines = connectionLines;
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

    public List<String> getComponentLines() {
        return componentLines;
    }

    public List<String> getConnectionLines() {
        return connectionLines;
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
