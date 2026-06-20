package rs.ac.bg.etf.sleep.simulation;

import java.util.*;

public class Netlist<V> {
	HashMap<Long, SimComponent<V>> components;
	HashMap<SimEndpoint, List<SimEndpoint>> connections;

	public Netlist() {
		components = new HashMap<Long, SimComponent<V>>();
		connections = new HashMap<SimEndpoint, List<SimEndpoint>>();
	}

	public List<Event<V>> transform(List<Event<V>> events) {
		List<Event<V>> result = new LinkedList<Event<V>>();
		for (Event<V> event : events) {
			List<SimEndpoint> endPoints = getEndpoins(event.srcID,
					event.srcPort);
			for (SimEndpoint endPoint : endPoints) {
				Event<V> e = event.copy();
				e.setDstID(endPoint.getComponentID());
				e.setDstPort(endPoint.getComponentPort());
				result.add(e);
			}
		}
		return result;
	}

	public List<SimEndpoint> getEndpoins(long srcID, int srcPort) {
		SimEndpoint endPoint = new SimEndpoint(srcID, srcPort);
		return connections.get(endPoint);
	}

	public HashMap<Long, SimComponent<V>> getComponents() {
		return components;
	}

	public void setComponents(HashMap<Long, SimComponent<V>> components) {
		this.components = components;
	}

	public SimComponent<V> getComponent(long id) {
		return components.get(id);
	}

	public void setComponent(long id, SimComponent<V> component) {
		addComponent(id, component);
	}

	public HashMap<SimEndpoint, List<SimEndpoint>> getConnections() {
		return connections;
	}

	public void setConnections(
			HashMap<SimEndpoint, List<SimEndpoint>> connections) {
		this.connections = connections;
	}

	public void addComponent(long id, SimComponent<V> component) {
		components.put(id, component);
	}

	public void addComponent(String[][] data) {
		for (String[] x : data) {
			addComponent(x);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void addComponent(String[] data) {
		try {
			long id = Long.parseLong(data[0]);
			Class c = Class.forName(data[1]);
			SimComponent<V> component = (SimComponent<V>) c.getDeclaredConstructor().newInstance(); // Java 9+
			// SimComponent<V> component = (SimComponent<V>) c.newInstance(); // do Java 9
			component.setState(data);
			components.put(id, component);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void addConnection(String[][] data) {
		for (String[] x : data) {
			addConnection(Long.parseLong(x[0]), Integer.parseInt(x[1]), Long
					.parseLong(x[2]), Integer.parseInt(x[3]));
		}
	}

	public void addConnection(long srcID, int srcPort, long dstID, int dstPort) {
		SimEndpoint srcEndPoint = new SimEndpoint(srcID, srcPort);
		SimEndpoint dstEndPoint = new SimEndpoint(dstID, dstPort);
		List<SimEndpoint> endPoints = connections.get(srcEndPoint);
		if (endPoints == null) {
			endPoints = new LinkedList<SimEndpoint>();
			connections.put(srcEndPoint, endPoints);
		}
		endPoints.add(dstEndPoint);
	}

	public String[][] getState() {
		String[][] state = new String[components.keySet().size()][];
		int i = 0;
		for (Long l : components.keySet()) {
			state[i++] = components.get(l).getState();
		}
		return state;
	}

}
