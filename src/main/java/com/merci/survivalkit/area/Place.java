package com.merci.survivalkit.area;

import java.util.Objects;

/**
 * Where an area belongs: one server (or one single-player world) and one dimension in it. The
 * same coordinates in the Nether are another place.
 *
 * @param server    "mp:" and the address for a server, "sp:" and the save's folder for single player
 * @param dimension the dimension's id ("minecraft:overworld")
 */
public record Place(String server, String dimension) {
	public Place {
		Objects.requireNonNull(server, "server");
		Objects.requireNonNull(dimension, "dimension");
	}
}
