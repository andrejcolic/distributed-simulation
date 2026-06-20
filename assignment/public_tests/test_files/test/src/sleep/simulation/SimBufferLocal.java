package rs.ac.bg.etf.sleep.simulation;

import java.util.*;

public class SimBufferLocal<T> implements SimBuffer<T> {
	PriorityQueue<Event<T>> queue;
	Netlist<T> simulation;

	public SimBufferLocal() {
		queue = new PriorityQueue<Event<T>>();
	}

	public void cancel(long time) {
		for (Event<T> msg : queue) {
			if (msg.lTime < time) {// ??
				queue.remove(msg);
			}
		}
	}

	public void setSimulation(Netlist<T> simulation) {
		this.simulation = simulation;
	}

	@Override
	public void putEvent(Event<T> event) {
		queue.add(event);
	}

	@Override
	public void putEvents(List<Event<T>> events) {
		for (Event<T> event : events) {
			putEvent(event);
		}
	}

	@Override
	public Event<T> getEvent() {
		return queue.remove();
	}

	@Override
	public List<Event<T>> getEvents() {
		List<Event<T>> list = new LinkedList<Event<T>>();
		list.add(getEvent());
		return list;
	}

	@Override
	public boolean isEmpty() {
		return queue.isEmpty();
	}

	@Override
	public long getMinrank() {
		return 0;
	}

}
