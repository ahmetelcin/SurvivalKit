package com.merci.survivalkit.scaffold;

import com.merci.survivalkit.vein.Pos;

/** One of a block's six faces, as the step from the block to the neighbour it looks at. */
public enum Face {
	DOWN(0, -1, 0),
	UP(0, 1, 0),
	NORTH(0, 0, -1),
	SOUTH(0, 0, 1),
	WEST(-1, 0, 0),
	EAST(1, 0, 0);

	private final int dx;
	private final int dy;
	private final int dz;

	Face(int dx, int dy, int dz) {
		this.dx = dx;
		this.dy = dy;
		this.dz = dz;
	}

	public int dx() {
		return dx;
	}

	public int dy() {
		return dy;
	}

	public int dz() {
		return dz;
	}

	/** The block on this side of {@code pos}. */
	public Pos from(Pos pos) {
		return new Pos(pos.x() + dx, pos.y() + dy, pos.z() + dz);
	}

	public Face opposite() {
		return switch (this) {
			case DOWN -> UP;
			case UP -> DOWN;
			case NORTH -> SOUTH;
			case SOUTH -> NORTH;
			case WEST -> EAST;
			case EAST -> WEST;
		};
	}
}
