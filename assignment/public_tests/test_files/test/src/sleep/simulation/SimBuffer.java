package rs.ac.bg.etf.sleep.simulation;

import java.util.*;

public interface SimBuffer<V> {

	public void putEvent(Event<V> event);

	public void putEvents(List<Event<V>> events);

	public Event<V> getEvent();

	public List<Event<V>> getEvents();

	public boolean isEmpty();

	public long getMinrank();
}