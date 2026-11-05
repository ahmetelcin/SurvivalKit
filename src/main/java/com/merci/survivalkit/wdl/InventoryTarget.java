package com.merci.survivalkit.wdl;

import java.util.Optional;

/**
 * Works out whose inventory an opened screen is showing, from what the player just did.
 *
 * <p>The screen does not say: the animal or cart sits in a field the game keeps to itself.
 * The animal being ridden is certain — that is whose inventory the inventory key opens. Failing
 * that, the one the player clicked a moment ago and is still standing next to. Anything looser
 * could put a chest's contents into the wrong cart, which is worse than not capturing them.
 */
public final class InventoryTarget {
	/** A second: the screen follows the click by a round trip to the server. */
	public static final int RECENT_TICKS = 20;
	/** Further than this and the player could not have opened it. */
	public static final double NEAR_BLOCKS = 8.0;

	private InventoryTarget() {
	}

	public static <T> Optional<T> choose(Optional<T> ridden, Optional<T> clicked, int ticksSinceClick, double distanceSquared) {
		if (ridden.isPresent()) {
			return ridden;
		}

		if (ticksSinceClick > RECENT_TICKS || distanceSquared > NEAR_BLOCKS * NEAR_BLOCKS) {
			return Optional.empty();
		}

		return clicked;
	}
}
