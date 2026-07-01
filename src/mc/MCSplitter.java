package mc;

import java.util.LinkedList;
import java.util.List;

import sleep.simulation.Event;

// The "Bag" of the Monte Carlo job (component id 1): each round it splits samplesPerRound paths
// into n batches (one per worker), then folds in the collector's combined result.
public class MCSplitter extends MCBase {
    private int n;                 // number of workers
    private long samplesPerRound;  // total paths to split across the workers each round
    private long dt;               // logical-time step per round
    private int steps;             // path steps per sample (forwarded to workers)

    private double sum;            // accumulated sum of discounted payoffs
    private long done;             // accumulated number of paths
    private long rounds;           // completed rounds (also used to vary RNG seeds)

    // Fixed European-call parameters (Black-Scholes price ≈ 10.45 — a sanity check for the result).
    private static final double S0 = 100.0;
    private static final double K = 100.0;
    private static final double R = 0.05;
    private static final double SIGMA = 0.20;
    private static final double T = 1.0;

    @Override
    public List<Event<Sample>> execute(Event<Sample> msg) {
        List<Event<Sample>> result = new LinkedList<Event<Sample>>();
        if (msg.getSrcID() != id) {
            // Response from the collector: fold in this round's result.
            Sample r = msg.getData();
            if (r != null) {
                sum += r.partial;
                done += r.count;
            }
            rounds++;
        }
        // Dispatch one tick ahead so the heavy worker step runs in its own barrier step (all
        // stations in parallel); otherwise the splitter's station runs its share serially first.
        lTime = msg.getlTime() + 1;
        result.addAll(createRound(lTime));
        return result;
    }

    private List<Event<Sample>> createRound(long dispatchTime) {
        List<Event<Sample>> result = new LinkedList<Event<Sample>>();
        long per = samplesPerRound / n;
        for (int w = 1; w <= n; w++) {
            long cnt = (w == n) ? samplesPerRound - per * (n - 1) : per;
            Sample s = new Sample();
            s.count = cnt;
            s.seed = rounds * 1000003L + w;
            s.steps = steps;
            s.s0 = S0;
            s.k = K;
            s.r = R;
            s.sigma = SIGMA;
            s.t = T;
            s.iteration = rounds;
            s.interval = dt;

            Event<Sample> e = new Event<Sample>();
            e.setData(s);
            e.setId(id);
            e.setSrcID(id);
            e.setSrcPort(w);        // routed to worker w (component id w+2) via the links file
            e.setDstID(w + 2);
            e.setDstPort(0);
            e.setlTime(dispatchTime);
            e.setlTimeCreated(dispatchTime);
            result.add(e);
        }
        return result;
    }

    @Override
    public String[] getState() {
        double price = done > 0 ? sum / done : 0.0;
        return new String[]{
            "" + id, this.getClass().getName(), name, "" + id,
            "" + n, "" + samplesPerRound, "" + lTime, "" + dt, "" + steps,
            "price=" + price, "paths=" + done, "rounds=" + rounds
        };
    }

    @Override
    public void setState(String[] args) {
        name = args[2];
        id = Integer.parseInt(args[3]);
        n = Integer.parseInt(args[4]);
        samplesPerRound = Long.parseLong(args[5]);
        lTime = Long.parseLong(args[6]);
        dt = Long.parseLong(args[7]);
        steps = Integer.parseInt(args[8]);
    }
}
