package rs.ac.bg.etf.sleep.simulation;

import java.util.*;

public abstract class Simulator<V> {
	int id;

	long pTime;
	boolean end;
	long lTime;

	Netlist<V> netlist;
	SimBuffer<V> queue;
	List<Event<V>> pastReceivedEvent;
	List<Event<V>> pastCreatedEvent;
	Event<V> lastEvent;

	long iteration;
	long iterationTime;

	public Simulator(int id) {
		pTime = 0;
		end = false;
		queue = new SimBufferLocal<V>();
		lTime = 0;
		pastReceivedEvent = new LinkedList<Event<V>>();
		pastCreatedEvent = new LinkedList<Event<V>>();
		lastEvent = null;
		iteration = 0;
		netlist = new Netlist<V>();
		iterationTime = 0;
	}

	public void init() {
		Long[] keys = netlist.getComponents().keySet().toArray(
				new Long[netlist.getComponents().size()]);
		for (Long key : keys) {
			SimComponent<V> comp = netlist.getComponents().get(key);
			List<Event<V>> events = comp.init();
			queue.putEvents(events);
			pastCreatedEvent.addAll(0, events);
		}
	}

	public void simulate() {
		while (!end) {
			long start = System.currentTimeMillis();
			execute();
			long end = System.currentTimeMillis();
			iterationTime = end - start;
		}
	}

	public boolean loop() {
		if (queue.isEmpty()) {
			return false;
		}

		execute();

		pTime += calculateTime();
		return true;
	}

	public abstract void execute();

	protected void pastEvents(Event<V> lastEvent) {
		pastReceivedEvent.add(0, lastEvent.copy());
	}

	public void work(Event<V> event) {
		SimComponent<V> comp = netlist.getComponent(event.dstID);
		List<Event<V>> events = comp.execute(event);
		queue.putEvents(netlist.transform(events));
		pastCreatedEvent.addAll(0, events);
		iteration++;
	}

	public long calculateTime() {
		return iterationTime;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public long getpTime() {
		return pTime;
	}

	public void setpTime(long pTime) {
		this.pTime = pTime;
	}

	public boolean isEnd() {
		return end;
	}

	public void setEnd(boolean end) {
		this.end = end;
	}

	public long getlTime() {
		return lTime;
	}

	public void setlTime(long lTime) {
		this.lTime = lTime;
	}

	public Netlist<V> getNetlist() {
		return netlist;
	}

	public void setNetlist(Netlist<V> netlist) {
		this.netlist = netlist;
	}

	public SimBuffer<V> getQueue() {
		return queue;
	}

	public void setQueue(SimBuffer<V> queue) {
		this.queue = queue;
	}

	public List<Event<V>> getPastReceivedEvent() {
		return pastReceivedEvent;
	}

	public void setPastReceivedEvent(List<Event<V>> pastReceivedEvent) {
		this.pastReceivedEvent = pastReceivedEvent;
	}

	public List<Event<V>> getPastCreatedEvent() {
		return pastCreatedEvent;
	}

	public void setPastCreatedEvent(List<Event<V>> pastCreatedEvent) {
		this.pastCreatedEvent = pastCreatedEvent;
	}

	public Event<V> getLastEvent() {
		return lastEvent;
	}

	public void setLastEvent(Event<V> lastEvent) {
		this.lastEvent = lastEvent;
	}

	public long getIteration() {
		return iteration;
	}

	public void setIteration(long iteration) {
		this.iteration = iteration;
	}

}
