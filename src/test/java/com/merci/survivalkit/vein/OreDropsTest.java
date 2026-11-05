package com.merci.survivalkit.vein;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class OreDropsTest {
	private static final int ROLLS = 20_000;

	/** Every count a drop can come to, rolled many times; small ranges are all hit at this many rolls. */
	private static TreeSet<Integer> seen(String block, int fortune) {
		OreDrops.Drop drop = OreDrops.of(block).orElseThrow();
		Random random = new Random(1);
		TreeSet<Integer> seen = new TreeSet<>();

		for (int i = 0; i < ROLLS; i++) {
			seen.add(drop.roll(random, fortune));
		}

		return seen;
	}

	@Test
	void withoutFortuneAnOreGivesItsBaseCount() {
		assertEquals(new TreeSet<>(List.of(1)), seen("minecraft:coal_ore", 0));
		assertEquals(new TreeSet<>(List.of(4, 5, 6, 7, 8, 9)), seen("minecraft:lapis_ore", 0));
		assertEquals(new TreeSet<>(List.of(4, 5)), seen("minecraft:redstone_ore", 0));
	}

	@Test
	void oreDropsMultipliesByOneToFortunePlusOne() {
		assertEquals(new TreeSet<>(List.of(1, 2, 3, 4)), seen("minecraft:diamond_ore", 3));
		assertEquals(new TreeSet<>(List.of(1, 2)), seen("minecraft:diamond_ore", 1));
	}

	@Test
	void redstoneAddsZeroToFortune() {
		assertEquals(new TreeSet<>(List.of(4, 5, 6, 7, 8)), seen("minecraft:redstone_ore", 3));
	}

	@Test
	void copperIsTwoToFiveTimesTheMultiplier() {
		TreeSet<Integer> seen = seen("minecraft:copper_ore", 3);
		assertEquals(2, seen.first());
		assertEquals(20, seen.last());
	}

	/** Fortune III on coal: multiplier 1 with 2/5, then 2, 3, 4 with 1/5 each — mean 2.2. */
	@Test
	void fortuneThreeOnCoalAveragesTwoPointTwo() {
		OreDrops.Drop coal = OreDrops.of("minecraft:coal_ore").orElseThrow();
		Random random = new Random(7);
		long total = 0;

		for (int i = 0; i < ROLLS; i++) {
			total += coal.roll(random, 3);
		}

		double mean = (double) total / ROLLS;
		assertTrue(Math.abs(mean - 2.2) < 0.05, "ortalama " + mean);
	}

	@Test
	void theDeepslateTwinDropsTheSame() {
		assertEquals(OreDrops.of("minecraft:iron_ore"), OreDrops.of("minecraft:deepslate_iron_ore"));
		assertEquals("minecraft:raw_iron", OreDrops.of("minecraft:deepslate_iron_ore").orElseThrow().item());
	}

	@Test
	void logsAndDebrisAreNotInTheTable() {
		assertTrue(OreDrops.of("minecraft:oak_log").isEmpty());
		assertTrue(OreDrops.of("minecraft:ancient_debris").isEmpty());
	}

	@Test
	void experienceStaysInItsRange() {
		OreDrops.Drop diamond = OreDrops.of("minecraft:diamond_ore").orElseThrow();
		Random random = new Random(3);
		TreeSet<Integer> seen = new TreeSet<>();

		for (int i = 0; i < ROLLS; i++) {
			seen.add(diamond.rollXp(random));
		}

		assertEquals(new TreeSet<>(List.of(3, 4, 5, 6, 7)), seen);
	}
}
