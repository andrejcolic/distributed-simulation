package rs.ac.bg.etf.kdp.simulation.components;

import java.util.*;

import rs.ac.bg.etf.sleep.simulation.*;

public class Bag extends G {
	public static int NUM = 1000;
	int n;
	int m;

	List<Body> bodies;
	List<List<Body>> results;
	int cnt;
	int next;
	long start;

	public Bag() {
		name = "";
		id = 0;
		n = 1;
		m = 1;
		lTime = 0;
		dt = 0;
		iteration = 0;
		bodies = new LinkedList<Body>();
		results = new LinkedList<List<Body>>();
		cnt = 0;
		next = 1;
		waitPeriod = 10;
		start = System.currentTimeMillis();
	}

	@Override
	public List<Event<Field>> execute(Event<Field> msg) {
		List<Event<Field>> result = new LinkedList<Event<Field>>();
		if (msg.getSrcID() != id) {
			bodies = msg.getData().coordinates;
			lTime += dt;
			iteration++;
			results.add(bodies);
			waitPeriod = (waitPeriod + (System.currentTimeMillis() - start) / (n + 1)) / 2;
			start = System.currentTimeMillis();
			cnt = 0;
			next = 1;
		}
		result.addAll(createTasksForWorker());
		if (result.size() == n) {
			result.add(createForItself());
		}
		return result;
	}

	public List<Event<Field>> createTasksForWorker() {
		List<Event<Field>> result = new LinkedList<Event<Field>>();
		int i = 0;
		while (cnt < bodies.size() && i < NUM * n) {
			List<Integer> indexes = new LinkedList<Integer>();
			long num = Math.min(NUM, bodies.size() - cnt);
			i += num;
			if (num != 0) {
				Field resultField = new Field();
				for (int j = 0; j < num; j++) {
					indexes.add(cnt++);
				}
				resultField.coordinates = bodies;
				resultField.indexes = indexes;
				resultField.interval = dt;
				resultField.iteration = iteration;
				resultField.time = lTime;

				Event<Field> resultMsg = new Event<Field>();
				resultMsg.setData(resultField);
				resultMsg.setId(id);
				resultMsg.setSrcID(id);
				resultMsg.setSrcPort(next);
				resultMsg.setDstID(next + 2);
				resultMsg.setDstPort(0);
				resultMsg.setlTime(lTime);
				resultMsg.setlTimeCreated(lTime);
				result.add(resultMsg);
				next = (next % n) + 1;
			} else {
				break;
			}
		}
		return result;
	}

	@Override
	public String[] getState() {
		String[] result = new String[8 + bodies.size() * 7];
		int i = 0;
		result[i++] = "" + id;
		result[i++] = this.getClass().getName();
		result[i++] = name;
		result[i++] = "" + id;
		result[i++] = "" + n;
		result[i++] = "" + m;
		result[i++] = "" + lTime;
		result[i++] = "" + dt;
		for (Body b : bodies) {
			result[i++] = "" + b.m;
			result[i++] = "" + b.x;
			result[i++] = "" + b.y;
			result[i++] = "" + b.z;
			result[i++] = "" + b.vx;
			result[i++] = "" + b.vy;
			result[i++] = "" + b.vz;
		}
		return result;
	}

	@Override
	public void setState(String[] args) {
		name = args[2];
		id = Integer.parseInt(args[3]);
		n = Integer.parseInt(args[4]);
		m = Integer.parseInt(args[5]);
		lTime = Long.parseLong(args[6]);
		dt = Long.parseLong(args[7]);
		for (int j = 0, i = 8; j < m; j++) {
		// for (int i = 8; i < args.length;) {
			Body b = new Body();
			b.id = Integer.parseInt(args[i++]);
			b.m = Double.parseDouble(args[i++]);
			b.x = Double.parseDouble(args[i++]);
			b.y = Double.parseDouble(args[i++]);
			b.z = Double.parseDouble(args[i++]);
			b.vx = Double.parseDouble(args[i++]);
			b.vy = Double.parseDouble(args[i++]);
			b.vz = Double.parseDouble(args[i++]);
			bodies.add(b);
		}
	}

	@Override
	public void restart(long time) {
		lTime = time;
	}

}
