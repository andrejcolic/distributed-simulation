package rs.ac.bg.etf.kdp.simulation.components;

import java.util.LinkedList;
import java.util.List;

import rs.ac.bg.etf.sleep.simulation.*;

public abstract class G implements rs.ac.bg.etf.sleep.simulation.SimComponent<Field> {
	String name;
	int id;
	long lTime;
	long dt;
	long iteration;
	long waitPeriod;

	public G() {
		name = "";
		id = 0;
		lTime = 0;
		dt = 0;
		iteration = 0;
		waitPeriod = 1000;
	}

	public Event<Field> createForItself() {
		try {
			Thread.sleep(waitPeriod);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		Event<Field> resultMsg = new Event<Field>();
		resultMsg.setData(null);
		resultMsg.setId(id);
		resultMsg.setSrcID(id);
		resultMsg.setSrcPort(0);
		resultMsg.setDstID(id);
		resultMsg.setDstPort(0);
		resultMsg.setlTime(lTime);
		resultMsg.setlTimeCreated(lTime);
		return resultMsg;
	}

	public List<Event<Field>> init() {
		List<Event<Field>> result = new LinkedList<Event<Field>>();
		result.add(createForItself());
		return result;
	}

}
