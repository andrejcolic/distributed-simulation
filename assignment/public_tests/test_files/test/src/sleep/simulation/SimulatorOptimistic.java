package rs.ac.bg.etf.sleep.simulation;

import java.io.*;

public class SimulatorOptimistic<T extends Serializable> extends Simulator<T> {

	public SimulatorOptimistic(int id) {
		super(id);
	}

	@Override
	public void execute() {
		Event<T> m = queue.getEvent();
		if (lTime > m.lTime) {
			restart(m.lTime);
			return;
		}
		lastEvent = m;
		lTime = lastEvent.lTime;
		if (lastEvent.ok()) {
			work(lastEvent);
			pastEvents(lastEvent);
		}
	}

	public void restart(long endTime) {
		// queue.cancel(endTime);
		for (Long l : netlist.getComponents().keySet()) {
			netlist.getComponents().get(l).restart(endTime);
		}
	}

}