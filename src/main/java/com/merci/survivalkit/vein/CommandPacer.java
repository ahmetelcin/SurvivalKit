package com.merci.survivalkit.vein;

import java.util.ArrayDeque;

/**
 * Keeps the commands under a rate. Two rules, both always on:
 *
 * <ul>
 *   <li>The hard one: in any 20 ticks (one second) at most {@code perSecond} commands. A sliding
 *       window, not a per-second bucket, so a burst cannot straddle a second's edge and come out
 *       at twice the rate.</li>
 *   <li>The polite one: the commands are spread out — at 10 a second, one every other tick —
 *       instead of all ten in the second's first tick.</li>
 * </ul>
 */
public final class CommandPacer {
	static final int TICKS_PER_SECOND = 20;

	private final ArrayDeque<Long> sent = new ArrayDeque<>();
	/** The earliest moment the next command may go, in ticks; fractions carry over at rates above 20. */
	private double nextAt = Double.NEGATIVE_INFINITY;

	/** Whether one more command may go at {@code tick}; if so it is counted as sent. */
	public boolean tryAcquire(long tick, int perSecond) {
		while (!sent.isEmpty() && sent.peekFirst() <= tick - TICKS_PER_SECOND) {
			sent.pollFirst();
		}

		if (sent.size() >= perSecond || tick < nextAt) {
			return false;
		}

		// After a pause the spacing starts over from now rather than paying out saved-up time.
		nextAt = (nextAt < tick - 1 ? tick : nextAt) + (double) TICKS_PER_SECOND / perSecond;
		sent.addLast(tick);
		return true;
	}

	public void reset() {
		sent.clear();
		nextAt = Double.NEGATIVE_INFINITY;
	}
}
