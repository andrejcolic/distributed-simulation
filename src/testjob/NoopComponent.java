package testjob;

import java.util.LinkedList;
import java.util.List;

import sleep.simulation.Event;
import sleep.simulation.SimComponent;

// A trivial component that produces no events — useful as a load test (a job of many NoopComponents
// runs the whole pipeline on large inputs without a heavy simulation). Keeps only its id.
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
