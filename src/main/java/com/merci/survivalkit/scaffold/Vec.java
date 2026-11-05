package com.merci.survivalkit.scaffold;

/** A point in the world, kept free of Minecraft like {@link com.merci.survivalkit.vein.Pos}. */
public record Vec(double x, double y, double z) {
	public double distanceTo(Vec other) {
		double dx = x - other.x;
		double dy = y - other.y;
		double dz = z - other.z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
