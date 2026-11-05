package com.merci.survivalkit.vein;

import java.util.ArrayList;
import java.util.List;

/** A block position, kept free of Minecraft so the vein rules can be tested without a game. */
public record Pos(int x, int y, int z) {
	/** The 26 blocks around this one: faces, edges and corners. Ore veins and branches touch diagonally. */
	public List<Pos> around() {
		List<Pos> around = new ArrayList<>(26);

		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx != 0 || dy != 0 || dz != 0) {
						around.add(new Pos(x + dx, y + dy, z + dz));
					}
				}
			}
		}

		return around;
	}
}
