package mc;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import sleep.simulation.Event;

// The heavy worker of the Monte Carlo job (component ids 3..n+2): prices a European call by
// simulating `count` GBM price paths of `steps` steps each, returning the summed discounted payoff.
public class MCWorker extends MCBase {

    @Override
    public List<Event<Sample>> execute(Event<Sample> msg) {
        List<Event<Sample>> result = new LinkedList<Event<Sample>>();
        if (msg.getSrcID() != id) {
            lTime = msg.getlTime();
            Sample task = msg.getData();
            double partial = priceSum(task);

            Sample res = new Sample();
            res.partial = partial;
            res.count = task.count;
            res.iteration = task.iteration;
            res.interval = task.interval;

            Event<Sample> e = new Event<Sample>();
            e.setData(res);
            e.setId(msg.getId() + 1);
            e.setSrcID(id);
            e.setSrcPort(1);            // routed to the collector via the links file
            e.setDstID(2);
            e.setDstPort(id - 2);
            e.setlTime(lTime + 1);          // next barrier step: the collector aggregates
            e.setlTimeCreated(lTime);
            result.add(e);
        }
        return result;
    }

    // Sum of discounted payoffs over `count` Monte Carlo paths (the expensive part).
    private double priceSum(Sample t) {
        double dtStep = t.t / t.steps;
        double drift = (t.r - 0.5 * t.sigma * t.sigma) * dtStep;
        double vol = t.sigma * Math.sqrt(dtStep);
        double disc = Math.exp(-t.r * t.t);
        Random rng = new Random(t.seed);
        double total = 0.0;
        for (long i = 0; i < t.count; i++) {
            double s = t.s0;
            for (int j = 0; j < t.steps; j++) {
                double z = rng.nextGaussian();
                s *= Math.exp(drift + vol * z);
            }
            double payoff = s - t.k;
            if (payoff > 0) {
                total += payoff;
            }
        }
        return disc * total;
    }

    @Override
    public String[] getState() {
        return new String[]{"" + id, this.getClass().getName(), name, "" + id};
    }

    @Override
    public void setState(String[] args) {
        name = args[2];
        id = Integer.parseInt(args[3]);
    }
}
