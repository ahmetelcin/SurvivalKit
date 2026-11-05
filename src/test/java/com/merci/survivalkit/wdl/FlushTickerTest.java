package com.merci.survivalkit.wdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FlushTickerTest {

	@Test
	void doesNotTriggerBeforeTheIntervalElapses() {
		FlushTicker ticker = new FlushTicker(() -> 1); // 1 second = 20 ticks

		for (int i = 1; i < 20; i++) {
			assertFalse(ticker.tick(), "tick " + i + " should not trigger flush");
			assertEquals(i, ticker.currentTicks());
		}
	}

	@Test
	void triggersExactlyWhenIntervalElapsesAndResets() {
		FlushTicker ticker = new FlushTicker(() -> 1); // 20 ticks

		for (int i = 1; i < 20; i++) {
			ticker.tick();
		}

		// 20th tick triggers flush
		assertTrue(ticker.tick());
		assertEquals(0, ticker.currentTicks());

		// Next tick starts counting again
		assertFalse(ticker.tick());
		assertEquals(1, ticker.currentTicks());
	}

	@Test
	void respectsChangingIntervalSettings() {
		AtomicInteger interval = new AtomicInteger(2); // 2 seconds = 40 ticks
		FlushTicker ticker = new FlushTicker(interval::get);

		for (int i = 0; i < 39; i++) {
			assertFalse(ticker.tick());
		}
		assertTrue(ticker.tick());

		// Change interval to 3 seconds = 60 ticks
		interval.set(3);
		for (int i = 0; i < 59; i++) {
			assertFalse(ticker.tick());
		}
		assertTrue(ticker.tick());
	}

	@Test
	void resetClearsAccumulatedTicks() {
		FlushTicker ticker = new FlushTicker(() -> 10);
		for (int i = 0; i < 15; i++) {
			ticker.tick();
		}
		assertEquals(15, ticker.currentTicks());

		ticker.reset();
		assertEquals(0, ticker.currentTicks());
	}
}
