package com.merci.survivalkit.vein;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class ToolWearTest {
	/** An iron axe (250) in hotbar slot 2, 10 damage taken. */
	private static final HeldTool AXE = new HeldTool(2, 10, 250, 1, 0);

	@Test
	void withoutUnbreakingEveryBlockCostsTheToolsDamagePerBlock() {
		assertEquals(7, ToolWear.roll(7, AXE, new Random(1)));
		assertEquals(14, ToolWear.roll(7, new HeldTool(0, 0, 59, 2, 0), new Random(1)), "kılıç blok başına 2");
	}

	/** Vanilla's chance for anything not armour: a point is kept with 1 / (level + 1). */
	@Test
	void unbreakingSparesTheShareTheGameSpares() {
		HeldTool unbreakingThree = new HeldTool(0, 0, 2031, 1, 3);
		Random random = new Random(7);
		int blocks = 40_000;

		double share = ToolWear.roll(blocks, unbreakingThree, random) / (double) blocks;

		assertEquals(0.25, share, 0.01);
	}

	@Test
	void theToolMustKeepAtLeastOnePoint() {
		HeldTool fiveLeft = new HeldTool(0, 245, 250, 1, 0);

		assertTrue(ToolWear.affordable(fiveLeft, 4, 0));
		assertFalse(ToolWear.affordable(fiveLeft, 5, 0), "0 kalırdı: kırılır");
		assertFalse(ToolWear.affordable(fiveLeft, 4, 1), "elle kırılan bloğun hasarı da gelecek");
	}

	@Test
	void theCommandAddsTheWearInTheSlotItWasIn() {
		assertEquals("item modify entity @s hotbar.2 {type:\"minecraft:set_damage\",damage:-0.030000000,add:true}",
			ToolWear.command(AXE, 7));
	}

	/**
	 * What the server makes of the command, worked the way {@code SetItemDamageFunction} works it, in
	 * floats: for every tool's durability, every damage it can have and every wear it can take, the
	 * damage comes out exactly that much higher. Without the half point it would land one short.
	 */
	@Test
	void theServerLandsOnExactlyTheWear() {
		for (int max : new int[] {59, 131, 250, 1561, 2031}) {
			for (int damage = 0; damage < max; damage += max > 300 ? 7 : 1) {
				for (int wear = 1; damage + wear <= max - 1; wear += wear < 20 ? 1 : 37) {
					HeldTool tool = new HeldTool(0, damage, max, 1, 0);
					String command = ToolWear.command(tool, wear);
					float delta = (float) Double.parseDouble(command.substring(command.indexOf("damage:") + 7, command.indexOf(",add")));

					float base = 1.0F - (float) damage / max;
					float pct = 1.0F - Math.clamp(delta + base, 0.0F, 1.0F);
					int after = (int) Math.floor(pct * max);

					assertEquals(damage + wear, after, max + " dayanıklılık, " + damage + " hasar, " + wear + " aşınma: " + command);
				}
			}
		}
	}
}
