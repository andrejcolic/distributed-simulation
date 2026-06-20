package rs.ac.bg.etf.kdp.simulation.components;

import java.util.*;

import rs.ac.bg.etf.sleep.simulation.*;

public class Worker extends G {
	public Worker() {
		id = 0;
		name = "";
		lTime = 0;
	}

	@Override
	public List<Event<Field>> execute(Event<Field> msg) {
		List<Event<Field>> result = new LinkedList<Event<Field>>();
		if (msg.getSrcID() != id) {
			lTime = msg.getlTime();
			Field field = msg.getData();
			Field resultField = field.calculate();
			Event<Field> resultMsg = new Event<Field>();
			resultMsg.setData(resultField);
			resultMsg.setId(msg.getId() + 1);
			resultMsg.setSrcID(id);
			resultMsg.setSrcPort(1);
			resultMsg.setDstID(2);
			resultMsg.setDstPort(id - 1);
			resultMsg.setlTime(lTime + field.interval);
			resultMsg.setlTimeCreated(lTime);
			result.add(resultMsg);
		}
		if (result.size() == 0) {
			// result.add(createForItself());
		}
		return result;
	}

	@Override
	public String[] getState() {
		String[] result = new String[4];
		result[0] = "" + id;
		result[1] = this.getClass().getName();
		result[2] = name;
		result[3] = "" + id;
		return result;
	}

	@Override
	public void setState(String[] args) {
		name = args[2];
		id = Integer.parseInt(args[3]);

	}

	@Override
	public void restart(long time) {
		lTime = time;
	}

}
