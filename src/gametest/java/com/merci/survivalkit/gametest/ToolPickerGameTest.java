package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.autotool.PickMode;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.EnumSetting;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;

/**
 * Alet Seçici in a running game, with a real middle click on a block of stone.
 *
 * <p>A stack of stone sits in the hotbar, so the game's own pick block has something to do:
 * if it runs, the selection jumps to the stone. That jump is what "vanilla pick block did not
 * fire" is checked against — and the negative control shows it really does jump when the
 * module is off.
 */
public class ToolPickerGameTest implements FabricClientGameTest {
	private static final int HAND_SLOT = 0;
	private static final int PICKAXE_SLOT = 2;
	private static final int STONE_SLOT = 5;

	/** Long enough for the game's pick block to have gone to the server and back. */
	private static final int SETTLE_TICKS = 20;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(5);
			world.getServer().runCommand("gamemode survival @a");

			List<Integer> always = aMiddleClickTakesTheToolAndNotTheBlock(context, world);
			List<Integer> vanilla = withTheModuleOffTheGamePicksTheBlock(context, world);
			whenNotInInventoryACarriedBlockIsPickedAsUsual(context, world);
			whenNotInInventoryAMissingBlockGetsTheTool(context, world);
			offLeavesThePickToTheGame(context, world);

			mode(context, PickMode.ALWAYS);
			off(context);
			SurvivalKit.LOGGER.info("[gametest] Alet Seçici doğrulandı (orta tık: seçim {} — kazma elde, taşa hiç geçilmedi; modül kapalıyken oyun taşı seçti {}; 'blok yoksa alet' taş üstündeyken taşı, değilken kazmayı seçti; 'kapalı' oyuna bıraktı)", always, vanilla);
		}
	}

	private List<Integer> aMiddleClickTakesTheToolAndNotTheBlock(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, true);
		mode(context, PickMode.ALWAYS);
		on(context);

		List<Integer> history = middleClick(context);

		if (!history.equals(List.of(HAND_SLOT, PICKAXE_SLOT))) {
			throw new AssertionError("orta tıkla kazmaya geçilmeli, taşa hiç geçilmemeliydi (0 → 2), olan: " + history);
		}

		if (!context.computeOnClient(client -> client.player.getMainHandItem().is(Items.IRON_PICKAXE))) {
			throw new AssertionError("elde demir kazma olmalıydı, elde: " + held(context));
		}

		if (!context.computeOnClient(client -> client.player.getInventory().getItem(STONE_SLOT).is(Items.STONE))) {
			throw new AssertionError("taş yerinden oynamamalıydı");
		}

		return history;
	}

	/**
	 * Negative control. The same click with the module off picks the stone: the game's pick
	 * block is alive in this setup, so its silence above is the module's doing.
	 */
	private List<Integer> withTheModuleOffTheGamePicksTheBlock(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, true);
		off(context);

		List<Integer> history = middleClick(context);

		if (!history.equals(List.of(HAND_SLOT, STONE_SLOT))) {
			throw new AssertionError("negatif kontrol başarısız: modül kapalıyken oyun taşı seçmeliydi (0 → 5), olan: " + history);
		}

		return history;
	}

	private void whenNotInInventoryACarriedBlockIsPickedAsUsual(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, true);
		mode(context, PickMode.WHEN_BLOCK_NOT_IN_INVENTORY);
		on(context);

		List<Integer> history = middleClick(context);

		if (!history.equals(List.of(HAND_SLOT, STONE_SLOT))) {
			throw new AssertionError("'blok yoksa alet': taş üstündeyken oyun taşı seçmeliydi (0 → 5), olan: " + history);
		}
	}

	private void whenNotInInventoryAMissingBlockGetsTheTool(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, false);
		mode(context, PickMode.WHEN_BLOCK_NOT_IN_INVENTORY);
		on(context);

		List<Integer> history = middleClick(context);

		if (!history.equals(List.of(HAND_SLOT, PICKAXE_SLOT))) {
			throw new AssertionError("'blok yoksa alet': taş yokken kazmaya geçilmeliydi (0 → 2), olan: " + history);
		}
	}

	private void offLeavesThePickToTheGame(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, true);
		mode(context, PickMode.OFF);
		on(context);

		List<Integer> history = middleClick(context);

		if (!history.equals(List.of(HAND_SLOT, STONE_SLOT))) {
			throw new AssertionError("'kapalı': oyun taşı seçmeliydi (0 → 5), olan: " + history);
		}
	}

	// --- helpers ---

	/** Bread in hand, an iron pickaxe two slots over, stone underfoot and looked at; stone in the hotbar if asked. */
	private static void setUp(ClientGameTestContext context, TestSingleplayerContext world, boolean carryStone) {
		off(context);

		BlockPos feet = context.computeOnClient(client -> client.player.blockPosition());
		int x = feet.getX();
		int y = feet.getY() + 20;
		int z = feet.getZ();

		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("fill " + (x - 2) + " " + (y - 1) + " " + (z - 2) + " " + (x + 2) + " " + (y + 4) + " " + (z + 2) + " air");
		world.getServer().runCommand("setblock " + x + " " + y + " " + z + " minecraft:stone");
		world.getServer().runCommand("item replace entity @a hotbar." + HAND_SLOT + " with minecraft:bread");
		world.getServer().runCommand("item replace entity @a hotbar." + PICKAXE_SLOT + " with minecraft:iron_pickaxe");

		if (carryStone) {
			world.getServer().runCommand("item replace entity @a hotbar." + STONE_SLOT + " with minecraft:stone 16");
		}

		world.getServer().runCommand("tp @a " + (x + 0.5) + " " + (y + 1) + " " + (z + 0.5) + " 0 90");
		context.waitTicks(10);

		context.runOnClient(client -> {
			client.player.getInventory().setSelectedSlot(HAND_SLOT);
			client.player.setXRot(90.0f);
		});
		context.waitTicks(5);
	}

	/** One real middle click, then the selection watched every tick until it has settled; repeats folded. */
	private static List<Integer> middleClick(ClientGameTestContext context) {
		List<Integer> history = new ArrayList<>();
		history.add(selected(context));
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);

		for (int i = 0; i < SETTLE_TICKS; i++) {
			int slot = selected(context);

			if (history.getLast() != slot) {
				history.add(slot);
			}

			context.waitTick();
		}

		return history;
	}

	private static int selected(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
	}

	private static String held(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getMainHandItem().getHoverName().getString());
	}

	private static void mode(ClientGameTestContext context, PickMode value) {
		context.runOnClient(client -> module().settings()
				.<EnumSetting<PickMode>>byId("mode")
				.orElseThrow(() -> new AssertionError("orta tık ayarı yok"))
				.set(value));
	}

	private static void on(ClientGameTestContext context) {
		context.runOnClient(client -> module().setEnabled(true));
		context.waitTicks(2);
	}

	private static void off(ClientGameTestContext context) {
		context.runOnClient(client -> module().setEnabled(false));
		context.waitTicks(2);
	}

	private static Module module() {
		return SurvivalKit.modules().byId("toolpicker")
				.orElseThrow(() -> new AssertionError("toolpicker modülü kayıtlı değil"));
	}
}
