package com.merci.survivalkit.wdl;

import java.util.function.IntSupplier;

/**
 * Tracks client ticks and signals when a periodic disk flush is due.
 *
 * <p>Pure decision logic: operates purely on tick counts and interval settings,
 * fully testable with JUnit without Minecraft runtime.
 */
public final class FlushTicker {
	public static final int TICKS_PER_SECOND = 20;

	private final IntSupplier intervalSeconds;
	private int ticks;

	public FlushTicker(IntSupplier intervalSeconds) {
		this.intervalSeconds = intervalSeconds;
	}

	/**
	 * Advances the tick count by one.
	 *
	 * @return {@code true} if the interval elapsed on this tick and a flush should occur.
	 */
	public boolean tick() {
		ticks++;
		int threshold = Math.max(1, intervalSeconds.getAsInt()) * TICKS_PER_SECOND;
		if (ticks >= threshold) {
			ticks = 0;
			return true;
		}
		return false;
	}

	public int currentTicks() {
		return ticks;
	}

	public void reset() {
		ticks = 0;
	}
}
