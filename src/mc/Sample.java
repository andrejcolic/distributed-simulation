package mc;

import java.io.Serializable;

// Tiny serializable payload for the Monte Carlo job: a batch of work to do, or a partial result
// (heavy computation, almost no data on the wire, so the job distributes well).
public class Sample implements Serializable {
    private static final long serialVersionUID = 1L;

    // Work to do (task) — set by the splitter.
    public long count;     // number of Monte Carlo paths in this batch
    public long seed;      // RNG seed (varies per task so batches are independent)
    public int steps;      // time steps per simulated price path (computation knob)

    // Option parameters (European call), passed so the worker stays stateless.
    public double s0;      // initial price
    public double k;       // strike
    public double r;       // risk-free rate
    public double sigma;   // volatility
    public double t;       // maturity (years)

    // Result — set by the worker / aggregated by the collector.
    public double partial; // sum of discounted payoffs over `count` paths

    // Bookkeeping (mirrors the N-body Field).
    public long iteration;
    public long interval;
}
