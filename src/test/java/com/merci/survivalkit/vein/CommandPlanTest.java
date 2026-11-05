package com.merci.survivalkit.vein;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommandPlanTest {
	private static final String IRON = "minecraft:iron_ore";
	private static final String DEEP_IRON = "minecraft:deepslate_iron_ore";

	@Test
	void aColumnIsCutAtGapsAndWhereTheBlockChanges() {
		Map<Pos, String> world = Map.of(
			new Pos(0, 0, 0), IRON, new Pos(0, 1, 0), IRON,
			new Pos(0, 2, 0), DEEP_IRON,
			new Pos(0, 4, 0), IRON);

		List<String> commands = CommandPlan.of(List.of(new Pos(0, 4, 0), new Pos(0, 1, 0), new Pos(0, 0, 0), new Pos(0, 2, 0)),
				world::get, Drops.INVENTORY, CommandPlan.Route.LOOT, true).stream().map(CommandPlan.Step::command).toList();

		assertEquals(List.of(
			"loot give @s mine 0 0 0 mainhand",
			"loot give @s mine 0 1 0 mainhand",
			"fill 0 0 0 0 1 0 minecraft:air replace minecraft:iron_ore",
			"loot give @s mine 0 2 0 mainhand",
			"fill 0 2 0 0 2 0 minecraft:air replace minecraft:deepslate_iron_ore",
			"loot give @s mine 0 4 0 mainhand",
			"fill 0 4 0 0 4 0 minecraft:air replace minecraft:iron_ore"), commands);
	}

	@Test
	void columnsGoInTheOrderTheyWereFound() {
		Map<Pos, String> world = Map.of(new Pos(5, 0, 5), IRON, new Pos(-5, 0, -5), IRON);

		List<String> commands = CommandPlan.of(List.of(new Pos(5, 0, 5), new Pos(-5, 0, -5)), world::get, Drops.INVENTORY, CommandPlan.Route.LOOT, true)
				.stream().map(CommandPlan.Step::command).toList();

		assertEquals("loot give @s mine 5 0 5 mainhand", commands.getFirst());
	}

	@Test
	void theMiddleOfABlockIsWrittenWithoutAnExponent() {
		assertEquals("0.5", CommandPlan.middle(0));
		assertEquals("-0.5", CommandPlan.middle(-1));
		assertEquals("-2.5", CommandPlan.middle(-3));
		assertEquals("12500000.5", CommandPlan.middle(12_500_000));
		assertEquals("-29999999.5", CommandPlan.middle(-30_000_000));
	}
}
