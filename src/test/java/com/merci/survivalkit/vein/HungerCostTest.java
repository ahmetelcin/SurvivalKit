package com.merci.survivalkit.vein;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HungerCostTest {
	@Test
	void tenBlocksAPointWithTheRestCarried() {
		HungerCost cost = new HungerCost();

		assertEquals(0, cost.points(7, 10));
		cost.pay(7, 10);
		assertEquals(1, cost.points(7, 10), "7 artı 7 = 14: bir puan, 4 kalır");
		cost.pay(7, 10);
		assertEquals(2, cost.points(16, 10), "4 artı 16 = 20");
	}

	@Test
	void zeroIsFree() {
		HungerCost cost = new HungerCost();

		assertEquals(0, cost.points(1000, 0));
		cost.pay(1000, 0);
		assertEquals(1, cost.points(10, 10));
	}

	@Test
	void resetForgetsTheCarry() {
		HungerCost cost = new HungerCost();
		cost.pay(9, 10);
		cost.reset();

		assertEquals(0, cost.points(9, 10));
	}

	@Test
	void saturationPaysFirstAndTheBarStaysAtSevenOrMore() {
		assertTrue(HungerCost.affordable(9, 0, 2), "9 - 2 = 7");
		assertFalse(HungerCost.affordable(8, 0, 2), "8 - 2 = 6: koşamaz");
		assertTrue(HungerCost.affordable(8, 1, 2), "doygunluk bir puanı öder");
		assertTrue(HungerCost.affordable(8, 0.2f, 2), "doygunluğun bir parçası bütün bir puan sayılır");
		assertTrue(HungerCost.affordable(7, 5, 5));
	}

	/**
	 * The effect's exhaustion has to pass 4 per point — the game drains only past 4 — and stay well
	 * under one point more: 0.1 a second per level, so the units of 0.1 are seconds × levels.
	 */
	@Test
	void theEffectGivesJustOverFourExhaustionAPoint() {
		for (int points = 1; points <= 40; points++) {
			HungerCost.Charge charge = HungerCost.charge(points);
			String[] words = charge.command().split(" ");
			int seconds = Integer.parseInt(words[4]);
			int level = Integer.parseInt(words[5]) + 1;
			int units = seconds * level;

			assertEquals("effect give @s minecraft:hunger", String.join(" ", words[0], words[1], words[2], words[3]));
			assertEquals(seconds, charge.seconds());
			assertTrue(level <= 256, charge.command());
			assertTrue(units >= points * 40 + 5, points + " puan: " + charge.command());
			assertTrue(units < points * 40 + 5 + seconds, points + " puan: " + charge.command());
		}
	}

	@Test
	void aSmallChargeTakesOneSecond() {
		assertEquals(new HungerCost.Charge("effect give @s minecraft:hunger 1 84 true", 1), HungerCost.charge(2));
	}

	@Test
	void theMostABarCanPayTakesSixSecondsAtMost() {
		// 20 food and 20 saturation, down to 7: 33 points.
		assertTrue(HungerCost.charge(33).seconds() <= 6, HungerCost.charge(33).command());
	}
}
