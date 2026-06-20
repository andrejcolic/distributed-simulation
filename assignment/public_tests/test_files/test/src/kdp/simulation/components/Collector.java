package rs.ac.bg.etf.kdp.simulation.components;

import java.util.LinkedList;
import java.util.List;

import rs.ac.bg.etf.sleep.simulation.*;

public class Collector extends G {
	int cnt;
	List<Field> filds;
	boolean start;

	int n;
	int m;

	public Collector() {
		id = 0;
		name = "";
		lTime = 0;
		cnt = 0;
		filds = new LinkedList<Field>();
		start = true;
		n = 0;
		m = 0;
	}

	@Override
	public List<Event<Field>> execute(Event<Field> msg) {
		List<Event<Field>> result = new LinkedList<Event<Field>>();
		if (msg.getSrcID() != id) {
			if (start) {
				lTime = msg.getlTime();
				start = false;
			}
			Field field = msg.getData();
			if (field != null) {
				cnt = cnt + field.indexes.size();
				filds.add(field);
				if (cnt == m) {
					Field resultField = new Field();
					for (Field f : filds) {
						for (Body b : f.coordinates) {
							resultField.addBody(b);
							resultField.addIndex(b.id);
						}
					}
					resultField.interval = field.interval;
					resultField.iteration = field.iteration + 1;
					resultField.time = lTime + field.interval;
					Event<Field> resultMsg = new Event<Field>();
					resultMsg.setData(resultField);
					resultMsg.setId(msg.getId() + 1);
					resultMsg.setSrcID(id);
					resultMsg.setSrcPort(1);
					resultMsg.setDstID(1);
					resultMsg.setDstPort(0);
					resultMsg.setlTime(lTime);
					resultMsg.setlTimeCreated(lTime);
					result.add(resultMsg);
					start = true;
					cnt = 0;
					filds = new LinkedList<Field>();
				}
			}
		}
		if (result.size() == 0) {
			// result.add(createForItself());
		}
		return result;
	}

	@Override
	public String[] getState() {
		String[] result = new String[6];
		result[0] = "" + id;
		result[1] = this.getClass().getName();
		result[2] = name;
		result[3] = "" + id;
		result[4] = "" + n;
		result[5] = "" + m;
		return result;
	}

	@Override
	public void setState(String[] args) {
		name = args[2];
		id = Integer.parseInt(args[3]);
		n = Integer.parseInt(args[4]);
		m = Integer.parseInt(args[5]);
	}

	@Override
	public void restart(long time) {
		lTime = time;
	}

}
