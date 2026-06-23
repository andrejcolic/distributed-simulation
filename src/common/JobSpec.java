package common;

import java.io.Serializable;

public class JobSpec implements Serializable {

    private final JobType type;
    private final long endTime;
    private final String outputName;

    public JobSpec(JobType type, long endTime, String outputName) {
        this.type = type;
        this.endTime = endTime;
        this.outputName = outputName;
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
