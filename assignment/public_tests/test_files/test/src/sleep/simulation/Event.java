package rs.ac.bg.etf.sleep.simulation;

import java.io.*;

public class Event<V> implements Comparable<Event<V>>, Serializable {
	private static final long serialVersionUID = 1L;
	public static final int OK = 0;
	public static final int NOK = 1;
	public static long ID = 0;

	long id;
	int status;

	long lTimeCreated;
	long lTime;

	long srcID;
	int srcPort;

	long dstID;
	int dstPort;

	V data;

	public Event(long lTimeCreated, long lTime, long srcID, int srcPort,
			long dstID, int dstPort) {
		this.lTimeCreated = lTimeCreated;
		this.lTime = lTime;
		this.srcID = srcID;
		this.srcPort = srcPort;
		this.dstID = dstID;
		this.dstPort = dstPort;
		this.status = OK;
		this.id = ID++;
	}

	public Event() {
		this(0, 0, 0, 0, 0, 0);
	}

	public int compareTo(Event<V> e) {
		if (e == null)
			return -1;
		int result = lTime < e.lTime ? -1
				: lTime > e.lTime ? 1
						: srcID == dstID ? -1
								: srcID < e.srcID ? -1
										: srcID > e.srcID ? 1
												: dstID < e.dstID ? -1
														: dstID > e.dstID ? 1
																: data != null && data.equals(e.data) ? 0 : -1;
		return result;
	}

	public boolean ok() {
		return status == OK;
	}

	public Event<V> copy() {
		Event<V> result = new Event<V>();
		result.srcID = srcID;
		result.srcPort = srcPort;
		result.lTime = lTime;
		result.status = status;
		result.dstID = dstID;
		result.dstPort = dstPort;

		result.lTimeCreated = lTimeCreated;
		result.id = id;
		result.data = data;

		return result;
	}

	public String toString() {
		String result = "" + srcID + ", " + srcPort + ", " + lTime + ", "
				+ status + ", " + dstID + ", " + dstPort + ", " + lTimeCreated
				+ ", " + id + ", " + data.toString();
		return result;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public long getlTimeCreated() {
		return lTimeCreated;
	}

	public void setlTimeCreated(long lTimeCreated) {
		this.lTimeCreated = lTimeCreated;
	}

	public long getlTime() {
		return lTime;
	}

	public void setlTime(long lTime) {
		this.lTime = lTime;
	}

	public long getSrcID() {
		return srcID;
	}

	public void setSrcID(long srcID) {
		this.srcID = srcID;
	}

	public int getSrcPort() {
		return srcPort;
	}

	public void setSrcPort(int srcPort) {
		this.srcPort = srcPort;
	}

	public long getDstID() {
		return dstID;
	}

	public void setDstID(long dstID) {
		this.dstID = dstID;
	}

	public int getDstPort() {
		return dstPort;
	}

	public void setDstPort(int dstPort) {
		this.dstPort = dstPort;
	}

	public V getData() {
		return data;
	}

	public void setData(V data) {
		this.data = data;
	}

}
