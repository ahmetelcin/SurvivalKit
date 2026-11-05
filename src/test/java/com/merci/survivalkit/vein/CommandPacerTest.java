package com.merci.survivalkit.vein;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandPacerTest {
	/** Asks as often as it may for 400 ticks; returns how many went in each tick. */
	private static List<Integer> greedy(int perSecond) {
		CommandPacer pacer = new CommandPacer();
		List<Integer> perTick = new ArrayList<>();

		for (long tick = 1; tick <= 400; tick++) {
			int sent = 0;

			while (pacer.tryAcquire(tick, perSecond)) {
				sent++;
			}

			perTick.add(sent);
		}

		return perTick;
	}

	@Test
	void noSecondEverHoldsMoreThanTheRate() {
		for (int rate : List.of(1, 3, 7, 10, 19, 20, 21, 30, 100)) {
			List<Integer> perTick = greedy(rate);

			for (int start = 0; start + 20 <= perTick.size(); start++) {
				int inSecond = perTick.subList(start, start + 20).stream().mapToInt(Integer::intValue).sum();
				assertTrue(inSecond <= rate, "hız " + rate + ": bir saniyede " + inSecond);
			}
		}
	}

	@Test
	void theRateIsActuallyReached() {
		for (int rate : List.of(1, 3, 10, 20, 30, 100)) {
			int total = greedy(rate).stream().mapToInt(Integer::intValue).sum();
			// 400 ticks are 20 seconds.
			assertTrue(total >= rate * 20 - rate, "hız " + rate + " ama 20 saniyede yalnız " + total);
		}
	}

	@Test
	void tenASecondIsOneEveryOtherTick() {
		List<Integer> firstSecond = greedy(10).subList(0, 20);

		assertEquals(List.of(1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0), firstSecond);
	}

	@Test
	void afterAPauseThereIsNoSavedUpBurst() {
		CommandPacer pacer = new CommandPacer();
		assertTrue(pacer.tryAcquire(1, 10));

		int atOnce = 0;

		while (pacer.tryAcquire(500, 10)) {
			atOnce++;
		}

		assertEquals(1, atOnce);
	}
}
