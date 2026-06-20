package rs.ac.bg.etf.sleep.simulation;

import java.io.*;

public class SimEndpoint implements Serializable, Comparable<SimEndpoint> {
	private static final long serialVersionUID = 1L;
	public static final long HOSTUNKNOWN = -1;
	public static final int PORTUNKNOWN = -1;

	public long componentID;
	public int componentPort;

	public SimEndpoint(long id, int port) {
		componentID = id;
		componentPort = port;
	}

	public SimEndpoint() {
		this(SimEndpoint.HOSTUNKNOWN, SimEndpoint.PORTUNKNOWN);
	}

	public long getComponentID() {
		return componentID;
	}

	public void setComponentID(long componentID) {
		this.componentID = componentID;
	}

	public int getComponentPort() {
		return componentPort;
	}

	public void setComponentPort(int componentPort) {
		this.componentPort = componentPort;
	}

	public boolean equals(Object o) {
		if (o == null || !(o instanceof SimEndpoint))
			return false;
		else
			return (componentID == ((SimEndpoint) o).getComponentID())
					&& (componentPort == ((SimEndpoint) o).getComponentPort());
	}

	public int hashCode() {
		return (int) (componentID * 517 + componentPort);
	}

	public String toString() {
		String result = "";
		result += componentID + ", " + componentPort;
		return result;
	}

	public int compareTo(SimEndpoint arg) {
		return componentID > arg.componentID ? +1
				: componentID < arg.componentID ? -1
						: componentPort > arg.componentPort ? +1
								: componentPort < arg.componentPort ? -1 : 0;
	}
}