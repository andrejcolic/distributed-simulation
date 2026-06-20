package rs.ac.bg.etf.kdp.simulation.components;

import java.io.*;
import java.util.*;

public class Field implements Serializable {
	private static final long serialVersionUID = 1L;
	private static final double GAMA = 6.674 / 100000000000l;
	long iteration;
	long time;
	long interval;
	List<Body> coordinates;
	List<Integer> indexes;

	public Field() {
		iteration = 0;
		time = 0;
		interval = 0;
		coordinates = new LinkedList<Body>();
		indexes = new LinkedList<Integer>();
	}

	public Field calculate() {
		Field result = new Field();
		List<Body> bodies = new LinkedList<Body>();
		for (int i : indexes) {
			bodies.add(move(coordinates, coordinates.get(i)));
		}
		result.indexes = indexes;
		result.time = time + interval;
		result.interval = interval;
		result.coordinates = bodies;
		return result;
	}

	public Body move(List<Body> coordinates, Body body) {
		Body result = new Body();
		double ax = 0;
		double ay = 0;
		double az = 0;
		for (Body b : coordinates) {
			if (b != body) {
				double r = distance(body, b);
				r = r * r * r;
				ax += b.m * (b.x - body.x) / r;
				ay += b.m * (b.y - body.y) / r;
				az += b.m * (b.z - body.z) / r;
			}
		}
		result.ax = -ax * GAMA;
		result.ay = -ay * GAMA;
		result.az = -az * GAMA;
		result.m = body.m;
		result.id = body.id;
		result.vx = body.vx + ax * interval;
		result.vy = body.vy + ay * interval;
		result.vz = body.vz + az * interval;
		result.x += body.x + body.vx * interval + ax * interval * interval / 2;
		result.y += body.y + body.vx * interval + ay * interval * interval / 2;
		result.z += body.z + body.vx * interval + az * interval * interval / 2;
		return result;
	}

	public double distance(Body a, Body b) {
		double r = 0;
		double a1 = a.x - b.x;
		double a2 = a.y - b.y;
		double a3 = a.z - b.z;
		r = Math.sqrt(a1 * a1 + a2 * a2 + a3 * a3);
		if (r < 10E-10)
			r = 10E-10;
		return r;
	}

	public void addBody(Body b) {
		coordinates.add(b);
	}

	public void addIndex(int index) {
		indexes.add(index);
	}

}
