package mc;

import java.util.LinkedList;
import java.util.List;

import sleep.simulation.Event;
import sleep.simulation.SimComponent;

// Common base for the Monte Carlo components: each bootstraps with one self-event at lTime 0.
public abstract class MCBase implements SimComponent<Sample> {
    protected String name = "";
    protected int id = 0;
    protected long lTime = 0;

    // Self-event that kicks off the pipeline (routed back via the "id 0 id 0" connection).
    protected Event<Sample> createForItself() {
        Event<Sample> msg = new Event<Sample>();
        msg.setData(null);
        msg.setId(id);
        msg.setSrcID(id);
        msg.setSrcPort(0);
        msg.setDstID(id);
        msg.setDstPort(0);
        msg.setlTime(lTime);
        msg.setlTimeCreated(lTime);
        return msg;
    }

    @Override
    public List<Event<Sample>> init() {
        List<Event<Sample>> result = new LinkedList<Event<Sample>>();
        result.add(createForItself());
        return result;
    }

    @Override
    public void restart(long time) {
        lTime = time;
    }
}
