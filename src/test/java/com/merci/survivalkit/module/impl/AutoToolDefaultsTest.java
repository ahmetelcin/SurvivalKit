package com.merci.survivalkit.module.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.settings.RegistryListSetting;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What Otomatik Alet ships with. That every id is a real block is checked in the game
 * (AutoToolGameTest), where the registry exists; here, that the right ones are on the lists.
 */
class AutoToolDefaultsTest {
	private static List<String> list(String id) {
		return new AutoToolModule().settings().<RegistryListSetting>byId(id).orElseThrow().get();
	}

	@Test
	void silkTouchSavesTheEnderChest() {
		assertTrue(list("silk_touch_blocks").contains("minecraft:ender_chest"));
	}

	@Test
	void silkTouchCoversEveryGlassAndPane() {
		List<String> silk = list("silk_touch_blocks");

		assertTrue(silk.contains("minecraft:glass"));
		assertTrue(silk.contains("minecraft:glass_pane"));
		assertEquals(16, silk.stream().filter(id -> id.endsWith("_stained_glass")).count());
		assertEquals(16, silk.stream().filter(id -> id.endsWith("_stained_glass_pane")).count());
	}

	@Test
	void silkTouchCoversEveryIce() {
		assertTrue(list("silk_touch_blocks").containsAll(List.of("minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice")));
	}

	@Test
	void tintedGlassIsLeftOffBecauseItDropsItselfAnyway() {
		assertFalse(list("silk_touch_blocks").contains("minecraft:tinted_glass"));
	}

	@Test
	void fortuneCoversTheRawMetalOres() {
		assertTrue(list("fortune_blocks").containsAll(List.of(
			"minecraft:iron_ore", "minecraft:deepslate_iron_ore",
			"minecraft:gold_ore", "minecraft:deepslate_gold_ore",
			"minecraft:copper_ore", "minecraft:deepslate_copper_ore")));
	}

	@Test
	void noBlockIsOnBothLists() {
		HashSet<String> both = new HashSet<>(list("silk_touch_blocks"));
		both.retainAll(list("fortune_blocks"));

		assertEquals(0, both.size(), "iki listede birden: " + both);
	}

	@Test
	void onlyWithToolInHandIsOnByDefault() {
		assertTrue(new AutoToolModule().onlyWithToolInHand());
	}

	@Test
	void keepSilkTouchIsOnByDefault() {
		assertTrue(new AutoToolModule().keepSilkTouch());
	}
}
