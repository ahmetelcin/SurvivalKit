package com.merci.survivalkit.area;

import com.merci.survivalkit.vein.Pos;

/**
 * A box of blocks, both corners included. Built from any two opposite corners; kept as the
 * lowest and the highest, so asking whether a block is inside is six comparisons.
 */
public record Area(Pos min, Pos max) {
	public static Area between(Pos a, Pos b) {
		return new Area(
			new Pos(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z())),
			new Pos(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z())));
	}

	public boolean contains(Pos pos) {
		return pos.x() >= min.x() && pos.x() <= max.x()
			&& pos.y() >= min.y() && pos.y() <= max.y()
			&& pos.z() >= min.z() && pos.z() <= max.z();
	}

	public int sizeX() {
		return max.x() - min.x() + 1;
	}

	public int sizeY() {
		return max.y() - min.y() + 1;
	}

	public int sizeZ() {
		return max.z() - min.z() + 1;
	}

	/** In blocks. A long: a box from bedrock to the build limit across a few thousand blocks does not fit an int. */
	public long volume() {
		return (long) sizeX() * sizeY() * sizeZ();
	}
}
