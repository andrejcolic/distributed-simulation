package rs.ac.bg.etf.sleep.simulation;

public class SimulatorSinglethread<V> extends Simulator<V> {

	public SimulatorSinglethread(int id) {
		super(id);
	}

	public void execute() {
		lastEvent = queue.getEvent();
		lTime = lastEvent.lTime;
		if (lastEvent.ok()) {
			work(lastEvent);
			pastEvents(lastEvent);
		}
	}

}