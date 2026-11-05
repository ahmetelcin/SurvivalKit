package com.merci.survivalkit.vein;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VeinScanTest {
	@Test
	void anOreAndItsDeepslateTwinAreOneFamily() {
		assertEquals("minecraft:iron_ore", VeinScan.family("minecraft:deepslate_iron_ore"));
		assertEquals("minecraft:iron_ore", VeinScan.family("minecraft:iron_ore"));
		assertEquals("minecraft:oak_log", VeinScan.family("minecraft:oak_log"));
		assertEquals("minecraft:deepslate", VeinScan.family("minecraft:deepslate"));
	}

	@Test
	void cornersJoin() {
		Set<Pos> vein = Set.of(new Pos(0, 0, 0), new Pos(1, 1, 1), new Pos(2, 2, 2));

		assertEquals(3, VeinScan.connected(new Pos(0, 0, 0), vein::contains, 100).size());
	}

	@Test
	void aGapOfOneBlockSeparates() {
		Set<Pos> two = Set.of(new Pos(0, 0, 0), new Pos(2, 0, 0));

		assertEquals(List.of(new Pos(0, 0, 0)), VeinScan.connected(new Pos(0, 0, 0), two::contains, 100));
	}

	@Test
	void foliageMustTouch() {
		List<Pos> logs = List.of(new Pos(0, 0, 0));

		assertTrue(VeinScan.touchesFoliage(logs, new Pos(1, 1, 1)::equals));
		assertFalse(VeinScan.touchesFoliage(logs, new Pos(0, 2, 0)::equals));
	}
}
