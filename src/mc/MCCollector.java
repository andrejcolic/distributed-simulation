package mc;

import java.util.LinkedList;
import java.util.List;

import sleep.simulation.Event;

// Collector of the Monte Carlo job (component id 2): sums the n workers' partials and returns the
// combined result to the splitter.
public class MCCollector extends MCBase {
    private int n;             // expected partials per round
    private int responses;     // partials received this round
    private double sum;        // running sum of discounted payoffs
    private long total;        // running path count
    private boolean start = true;

    @Override
    public List<Event<Sample>> execute(Event<Sample> msg) {
        List<Event<Sample>> result = new LinkedList<Event<Sample>>();
        if (msg.getSrcID() != id) {
            if (start) {
                lTime = msg.getlTime();
                start = false;
            }
            Sample f = msg.getData();
            if (f != null) {
                sum += f.partial;
                total += f.count;
                responses++;
                if (responses == n) {
                    Sample combined = new Sample();
                    combined.partial = sum;
                    combined.count = total;
                    combined.iteration = f.iteration + 1;
                    combined.interval = f.interval;

                    Event<Sample> e = new Event<Sample>();
                    e.setData(combined);
                    e.setId(msg.getId() + 1);
                    e.setSrcID(id);
                    e.setSrcPort(1);        // routed back to the splitter via the links file
                    e.setDstID(1);
                    e.setDstPort(0);
                    e.setlTime(lTime + 1);  // next barrier step: the splitter starts the next round
                    e.setlTimeCreated(lTime);
                    result.add(e);

                    start = true;
                    responses = 0;
                    sum = 0.0;
                    total = 0;
                }
            }
        }
        return result;
    }

    @Override
    public String[] getState() {
        return new String[]{"" + id, this.getClass().getName(), name, "" + id, "" + n};
    }

    @Override
    public void setState(String[] args) {
        name = args[2];
        id = Integer.parseInt(args[3]);
        n = Integer.parseInt(args[4]);
    }
}
