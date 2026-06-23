package testjob;

import java.util.LinkedList;
import java.util.List;

import sleep.simulation.Event;
import sleep.simulation.SimComponent;

/**
 * A trivial component that produces no events. Used as a job-independent load test: a job made of
 * many NoopComponents lets the full pipeline run to completion on large input files (Test 7)
 * without a heavy simulation. It keeps only its id, so its state stays small regardless of how
 * large the input line was.
 */
public class NoopComponent implements SimComponent<Object> {

    private String id = "0";

    @Override
    public List<Event<Object>> execute(Event<Object> msg) {
        return new LinkedList<>();
    }

    @Override
    public List<Event<Object>> init() {
        return new LinkedList<>();
    }

    @Override
    public String[] getState() {
        return new String[]{id, "testjob.NoopComponent"};
    }

    @Override
    public void setState(String[] args) {
        if (args != null && args.length > 0) {
            this.id = args[0];
        }
    }

    @Override
    public void restart(long time) {
        // stateless
    }
}
