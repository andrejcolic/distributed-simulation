package rs.ac.bg.etf.sleep.simulation;

import java.util.*;

public interface SimComponent<V> {
	public List<Event<V>> execute(Event<V> msg);

	public List<Event<V>> init();

	public String[] getState();

	public void setState(String[] args);

	public void restart(long time);

}