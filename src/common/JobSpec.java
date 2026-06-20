package rs.ac.bg.etf.kdp.common;

import java.io.Serializable;
import java.util.List;

/**
 * Job definition the client sends to the server: the contents of the components file
 * and the connections file (as text lines — job-independent), the simulation type, the
 * target logical end time, and the output file name.
 *
 * Lines are transferred as text so the solution stays <b>job-independent</b>
 * (the server never instantiates components; the worker does that via reflection).
 */
public class JobSpec implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<String> componentLines;
    private final List<String> connectionLines;
    private final JobType type;
    private final long endTime;
    private final String outputName;

    public JobSpec(List<String> componentLines, List<String> connectionLines,
                   JobType type, long endTime, String outputName) {
        this.componentLines = componentLines;
        this.connectionLines = connectionLines;
        this.type = type;
        this.endTime = endTime;
        this.outputName = outputName;
    }

    public List<String> getComponentLines() {
        return componentLines;
    }

    public List<String> getConnectionLines() {
        return connectionLines;
    }

    public JobType getType() {
        return type;
    }

    public long getEndTime() {
        return endTime;
    }

    public String getOutputName() {
        return outputName;
    }
}
