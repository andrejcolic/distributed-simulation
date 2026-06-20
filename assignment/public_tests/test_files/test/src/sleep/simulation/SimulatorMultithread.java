package rs.ac.bg.etf.sleep.simulation;

public class SimulatorMultithread<T> extends Simulator<T> {

	public SimulatorMultithread(int id) {
		super(id);
	}

	private void synchronize() {
		// TODO Auto-generated method stub
	}

	private boolean isTimeInTheRange(Event<T> m) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void execute() {
		Event<T> m = queue.getEvent();
		if (!isTimeInTheRange(m)) {
			queue.putEvent(m);
			synchronize();
			m = queue.getEvent();
		}
		lastEvent = m;
		lTime = lastEvent.lTime;
		if (lastEvent.ok()) {
			work(lastEvent);
		}
	}
}
