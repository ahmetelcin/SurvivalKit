package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.BoolSetting;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * Every tool, not only the pickaxe: each block is met with its own tool, all of them in the
 * hotbar at once, the way a player carries them. Otomatik Alet is driven with the attack key
 * really held, Alet Seçici with a real middle click.
 *
 * <p>Each pair runs twice: with bread in hand, and with a Silk Touch pickaxe in hand. The
 * second is the one that went wrong in play — the pickaxe was kept for every block that drops
 * to anything, so wood, dirt, wool and hay never got their tool.
 */
public class ToolPairsGameTest implements FabricClientGameTest {
	private static final int HAND_SLOT = 0;

	private static final String BREAD = "minecraft:bread";
	private static final String EMPTY = "minecraft:air";
	private static final String SILK_PICKAXE = "minecraft:iron_pickaxe[minecraft:enchantments={\"minecraft:silk_touch\":1}]";

	/** The tools, one per hotbar slot after the item in hand. */
	private static final List<String> TOOLS = List.of(
		"minecraft:iron_pickaxe", "minecraft:iron_axe", "minecraft:iron_shovel", "minecraft:shears", "minecraft:iron_hoe");

	/** Each block and the slot of the tool that should come out for it. */
	private static final Map<String, Integer> PAIRS = new LinkedHashMap<>();

	static {
		PAIRS.put("minecraft:stone", 1);
		PAIRS.put("minecraft:oak_log", 2);
		PAIRS.put("minecraft:dirt", 3);
		PAIRS.put("minecraft:white_wool", 4);
		PAIRS.put("minecraft:hay_block", 5);
	}

	private static final int PATIENCE_TICKS = 300;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(5);
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("effect give @a minecraft:saturation 600 0 true");

			Map<String, List<Integer>> mined = new LinkedHashMap<>();
			Map<String, List<Integer>> picked = new LinkedHashMap<>();
			List<String> wrong = new ArrayList<>();

			for (String hand : List.of(BREAD, SILK_PICKAXE)) {
				pairs(context, world, hand, mined, picked, wrong);
			}

			Map<String, List<Integer>> onlyWithTool = onlyWithToolInHand(context, world, wrong);

			if (!wrong.isEmpty()) {
				throw new AssertionError("yanlış alet: " + String.join(" | ", wrong));
			}

			SurvivalKit.LOGGER.info("[gametest] blok-alet çiftleri doğrulandı (Otomatik Alet {}; Alet Seçici {})", mined, picked);
			SurvivalKit.LOGGER.info("[gametest] 'sadece elde alet varken' doğrulandı {}", onlyWithTool);
		}
	}

	/**
	 * "Only with a tool in hand", on as it ships: bread or a bare hand on stone leaves the hand
	 * alone, a pickaxe in hand on wood still gets the axe. The same bread on the same stone with
	 * the setting off does get the pickaxe — the negative control that the first two are the
	 * setting's doing, not a module that switches nothing.
	 */
	private static Map<String, List<Integer>> onlyWithToolInHand(ClientGameTestContext context, TestSingleplayerContext world,
			List<String> wrong) {
		Map<String, List<Integer>> seen = new LinkedHashMap<>();

		enable("toolpicker", context, false);
		enable("autotool", context, true);
		onlyWithTool(context, true);

		check(context, world, "açık, elde ekmek, taş", BREAD, "minecraft:stone", List.of(HAND_SLOT), seen, wrong);
		check(context, world, "açık, el boş, taş", EMPTY, "minecraft:stone", List.of(HAND_SLOT), seen, wrong);
		check(context, world, "açık, elde kazma, odun", "minecraft:iron_pickaxe", "minecraft:oak_log",
			List.of(HAND_SLOT, 2, HAND_SLOT), seen, wrong);

		onlyWithTool(context, false);
		check(context, world, "kapalı, elde ekmek, taş", BREAD, "minecraft:stone", List.of(HAND_SLOT, 1, HAND_SLOT), seen, wrong);

		onlyWithTool(context, true);
		enable("autotool", context, false);
		return seen;
	}

	private static void check(ClientGameTestContext context, TestSingleplayerContext world, String label, String hand,
			String block, List<Integer> expected, Map<String, List<Integer>> seen, List<String> wrong) {
		List<Integer> history = breakIt(context, setUp(context, world, hand, block));
		seen.put(label, history);

		if (!history.equals(expected)) {
			wrong.add("Otomatik Alet, " + label + ": beklenen " + expected + ", olan " + history);
		}
	}

	private static void onlyWithTool(ClientGameTestContext context, boolean on) {
		context.runOnClient(client -> module("autotool").settings()
			.<BoolSetting>byId("only_with_tool")
			.orElseThrow(() -> new AssertionError("'sadece elde alet varken' ayarı yok"))
			.set(on));
	}

	/**
	 * Every pair once with each module. With a Silk Touch pickaxe in hand, stone is the one
	 * block it is kept for: Otomatik Alet leaves it there, and for Alet Seçici it already is
	 * the best tool, so nothing moves.
	 */
	private static void pairs(ClientGameTestContext context, TestSingleplayerContext world, String hand,
			Map<String, List<Integer>> mined, Map<String, List<Integer>> picked, List<String> wrong) {
		String label = hand.equals(BREAD) ? "ekmek" : "ipeksi kazma";
		boolean silkInHand = !hand.equals(BREAD);

		enable("toolpicker", context, false);
		enable("autotool", context, true);
		// Bread is not a tool: with "only with a tool in hand" on, nothing would switch at all.
		// The pairs are about which tool comes out, so it is off for them; the silk pickaxe is
		// a tool and runs with it on, as it ships.
		onlyWithTool(context, silkInHand);

		for (Map.Entry<String, Integer> pair : PAIRS.entrySet()) {
			boolean kept = silkInHand && pair.getValue() == 1;
			List<Integer> expected = kept ? List.of(HAND_SLOT) : List.of(HAND_SLOT, pair.getValue(), HAND_SLOT);

			BlockPos block = setUp(context, world, hand, pair.getKey());
			List<Integer> history = breakIt(context, block);
			mined.put(label + " " + pair.getKey(), history);

			if (!history.equals(expected)) {
				wrong.add("Otomatik Alet, elde " + label + ", " + pair.getKey() + ": beklenen " + expected + ", olan " + history);
			}
		}

		enable("autotool", context, false);
		enable("toolpicker", context, true);

		for (Map.Entry<String, Integer> pair : PAIRS.entrySet()) {
			boolean alreadyBest = silkInHand && pair.getValue() == 1;
			List<Integer> expected = alreadyBest ? List.of(HAND_SLOT) : List.of(HAND_SLOT, pair.getValue());

			setUp(context, world, hand, pair.getKey());
			List<Integer> history = middleClick(context);
			picked.put(label + " " + pair.getKey(), history);

			if (!history.equals(expected)) {
				wrong.add("Alet Seçici, elde " + label + ", " + pair.getKey() + ": beklenen " + expected + ", olan " + history);
			}
		}

		enable("toolpicker", context, false);
	}

	/**
	 * The block under the player's feet with bedrock beneath it, the given item in hand and
	 * every tool in the hotbar; the player looks straight down at the block.
	 */
	private static BlockPos setUp(ClientGameTestContext context, TestSingleplayerContext world, String hand, String block) {
		BlockPos feet = context.computeOnClient(client -> client.player.blockPosition());
		int x = feet.getX();
		int y = 100;
		int z = feet.getZ();

		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("fill " + (x - 2) + " " + (y - 1) + " " + (z - 2) + " " + (x + 2) + " " + (y + 4) + " " + (z + 2) + " air");
		world.getServer().runCommand("setblock " + x + " " + y + " " + z + " " + block);
		world.getServer().runCommand("setblock " + x + " " + (y - 1) + " " + z + " minecraft:bedrock");
		world.getServer().runCommand("item replace entity @a hotbar." + HAND_SLOT + " with " + hand);

		for (int i = 0; i < TOOLS.size(); i++) {
			world.getServer().runCommand("item replace entity @a hotbar." + (i + 1) + " with " + TOOLS.get(i));
		}

		world.getServer().runCommand("tp @a " + (x + 0.5) + " " + (y + 1) + " " + (z + 0.5) + " 0 90");
		context.waitTicks(10);
		context.runOnClient(client -> {
			client.player.getInventory().setSelectedSlot(HAND_SLOT);
			client.player.setXRot(90.0f);
		});
		context.waitTicks(5);

		// The silk pickaxe once failed to arrive through a mangled command and the empty slot
		// passed for bread; a set-up that did not happen is not a result.
		boolean empty = context.computeOnClient(client -> client.player.getInventory().getItem(HAND_SLOT).isEmpty());

		if (empty != hand.equals(EMPTY)) {
			throw new AssertionError("hazırlık: elde " + hand + " olmalıydı, slot " + (empty ? "boş" : "dolu"));
		}

		return new BlockPos(x, y, z);
	}

	/** Holds attack until the block is gone, lets go, and returns the selection's moves. */
	private static List<Integer> breakIt(ClientGameTestContext context, BlockPos block) {
		List<Integer> history = new ArrayList<>();
		context.getInput().holdKey(options -> options.keyAttack);

		for (int tick = 0; tick < PATIENCE_TICKS && !context.computeOnClient(client -> client.level.getBlockState(block).isAir()); tick++) {
			record(context, history);
			context.waitTick();
		}

		context.getInput().releaseKey(options -> options.keyAttack);

		for (int i = 0; i < 10; i++) {
			context.waitTick();
			record(context, history);
		}

		return history;
	}

	private static List<Integer> middleClick(ClientGameTestContext context) {
		List<Integer> history = new ArrayList<>();
		record(context, history);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);

		for (int i = 0; i < 20; i++) {
			record(context, history);
			context.waitTick();
		}

		return history;
	}

	/** Adds the selected slot, folding repeats, so the list is the selection's moves. */
	private static void record(ClientGameTestContext context, List<Integer> history) {
		int slot = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());

		if (history.isEmpty() || history.getLast() != slot) {
			history.add(slot);
		}
	}

	private static void enable(String id, ClientGameTestContext context, boolean on) {
		context.runOnClient(client -> module(id).setEnabled(on));
		context.waitTicks(2);
	}

	private static Module module(String id) {
		return SurvivalKit.modules().byId(id).orElseThrow(() -> new AssertionError("modül kayıtlı değil: " + id));
	}
}
