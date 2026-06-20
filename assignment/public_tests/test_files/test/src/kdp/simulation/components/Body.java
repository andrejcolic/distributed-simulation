package rs.ac.bg.etf.kdp.simulation.components;

import java.io.*;

public class Body implements Serializable {
	private static final long serialVersionUID = 1L;
	int id;
	double x, y, z;
	double vx, vy, vz;
	double ax, ay, az;
	double m;
}