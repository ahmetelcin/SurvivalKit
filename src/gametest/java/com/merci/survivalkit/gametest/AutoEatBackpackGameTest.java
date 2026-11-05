package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.autoeat.EatingHand;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.util.InventoryUtil;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Food from the backpack, eaten in the main hand, has to go back where it came from — and
 * the thing that was in hand has to come back to the hand. However the meal ends.
 *
 * <p>Checked with the module still on: switching it off undoes the swap by itself, so a
 * test that switches off before looking would pass whatever the end of a meal did.
 *
 * <p>The whole inventory is compared, slot by slot, against how it was before, on the
 * server — the client can show a swap the server never made.
 */
public class AutoEatBackpackGameTest implements FabricClientGameTest {
	private static final int HUNGRY = 14;
	/** The hotbar slot in hand: a sword, which is what has to come back. */
	private static final int HELD = 0;
	/** A backpack slot, inventory index. {@code inventory.11} in commands. */
	private static final int PACK = 20;
	/** Something else in the hotbar, so a shuffled hotbar would show. */
	private static final int OTHER = 4;
	/** A second stack of bread, further back in the pack. */
	private static final int SPARE = 25;
	private static final int PATIENCE = 200;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(5);
			world.getServer().runCommand("gamemode survival @a");
			scope(context, InventoryUtil.Scope.FULL);
			hand(context, EatingHand.MAIN_HAND);

			theMealEndsWithBreadLeft(context, world);
			theLastBreadIsEaten(context, world);
			theMealIsInterrupted(context, world);
			theLastBreadOfOneStackIsEaten(context, world);
			aChestInSightStaysShut(context, world);
			theMealIsInterruptedByAServerMenu(context, world);

			scope(context, InventoryUtil.Scope.HOTBAR);
			SurvivalKit.LOGGER.info("[gametest] sırt çantasından yeme: altı bitişin hepsinde (yığın kaldı, son ekmek, E ile kesildi, "
					+ "ikinci yığın varken son ekmek, sandığa bakarken, sunucu menüsü) kılıç slot {}'da, ekmek slot {}'de", HELD, PACK);
		}
	}

	/** One loaf takes hunger from 14 past the threshold; four of the five stay in the pack. */
	private void theMealEndsWithBreadLeft(ClientGameTestContext context, TestSingleplayerContext world) {
		List<String> before = setUp(context, world, 5);

		setEnabled(context, true);
		world.getServer().waitFor(server -> player(server).getFoodData().getFoodLevel() > HUNGRY, PATIENCE);
		context.waitTicks(20);

		List<String> expected = new ArrayList<>(before);
		expected.set(PACK, "minecraft:bread x4");
		assertLayout(context, world, "yığın bitmeden bitiş", expected);
		setEnabled(context, false);
	}

	/** The only loaf goes; the pack slot is left empty and the sword is back in hand. */
	private void theLastBreadIsEaten(ClientGameTestContext context, TestSingleplayerContext world) {
		List<String> before = setUp(context, world, 1);

		setEnabled(context, true);
		world.getServer().waitFor(server -> player(server).getFoodData().getFoodLevel() > HUNGRY, PATIENCE);
		context.waitTicks(20);

		List<String> expected = new ArrayList<>(before);
		expected.set(PACK, "");
		assertLayout(context, world, "son yiyecek yendi", expected);
		setEnabled(context, false);
	}

	/** The inventory key pressed mid-bite calls the meal off: nothing eaten, everything back. */
	private void theMealIsInterrupted(ClientGameTestContext context, TestSingleplayerContext world) {
		List<String> before = setUp(context, world, 5);

		setEnabled(context, true);
		context.waitFor(client -> client.player.isUsingItem(), PATIENCE);
		context.waitTicks(5);
		context.getInput().pressKey(options -> options.keyInventory);
		context.waitTicks(20);

		assertLayout(context, world, "yemek yarıda kesildi", before);

		context.runOnClient(client -> client.gui.setScreen(null));
		setEnabled(context, false);
		context.waitTicks(20);
	}

	/**
	 * The last loaf of one stack, with a second stack further back. Whatever an inventory mod
	 * does to refill an emptied hand, the second stack stays where it was.
	 */
	private void theLastBreadOfOneStackIsEaten(ClientGameTestContext context, TestSingleplayerContext world) {
		List<String> before = setUp(context, world, 1);
		world.getServer().runCommand("item replace entity @a inventory." + (SPARE - Inventory.getSelectionSize())
				+ " with minecraft:bread 5");
		context.waitTicks(5);
		before = layout(world);

		setEnabled(context, true);
		world.getServer().waitFor(server -> player(server).getFoodData().getFoodLevel() > HUNGRY, PATIENCE);
		context.waitTicks(20);

		List<String> expected = new ArrayList<>(before);
		expected.set(PACK, "");
		assertLayout(context, world, "yığının son yiyeceği yendi, başka yığın var", expected);
		setEnabled(context, false);
	}

	/**
	 * A menu the server opens mid-bite — a plugin's GUI, say — calls the meal off like any
	 * screen. The swap back cannot go through the player's own menu while another one is
	 * open, so it has to wait for it to close; it must not be dropped.
	 */
	private void theMealIsInterruptedByAServerMenu(ClientGameTestContext context, TestSingleplayerContext world) {
		List<String> before = setUp(context, world, 5);

		setEnabled(context, true);
		context.waitFor(client -> client.player.isUsingItem(), PATIENCE);
		context.waitTicks(5);
		world.getServer().runOnServer(server -> player(server).openMenu(new SimpleMenuProvider(
				(id, inventory, player) -> ChestMenu.threeRows(id, inventory), Component.literal("menu"))));
		context.waitFor(client -> client.player.containerMenu != client.player.inventoryMenu, PATIENCE);
		// Fed while the menu is open, so closing it does not start the next attempt.
		world.getServer().runOnServer(server -> player(server).getFoodData().setFoodLevel(20));
		context.waitTicks(10);
		context.runOnClient(client -> client.player.closeContainer());
		context.waitTicks(20);

		assertLayout(context, world, "sunucu menüsüyle kesildi", before);
		setEnabled(context, false);
		context.waitTicks(20);
	}

	/**
	 * Food from the backpack eaten while the crosshair is on a chest. Before the bite was
	 * started directly, the use key opened the chest instead, with the bread already in hand.
	 * Now the chest stays shut, the bread is eaten and the layout comes back as it was.
	 */
	private void aChestInSightStaysShut(ClientGameTestContext context, TestSingleplayerContext world) {
		List<String> before = setUp(context, world, 5);
		world.getServer().runCommand("execute at @a run fill ~-2 ~ ~-2 ~2 ~2 ~3 minecraft:air");
		world.getServer().runCommand("execute at @a run fill ~-2 ~-1 ~-2 ~2 ~-1 ~3 minecraft:stone");
		world.getServer().runCommand("execute at @a run setblock ~ ~ ~2 minecraft:chest");
		world.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 30");
		context.waitTicks(10);

		setEnabled(context, true);

		for (int tick = 0; tick < PATIENCE; tick++) {
			if (context.computeOnClient(client -> client.gui.screen() instanceof ContainerScreen)) {
				throw new AssertionError("çantadan yerken sandık açıldı (tick " + tick + ")");
			}

			if (world.getServer().computeOnServer(server -> player(server).getFoodData().getFoodLevel() > HUNGRY)) {
				break;
			}

			context.waitTick();
		}

		context.waitTicks(20);

		List<String> expected = new ArrayList<>(before);
		expected.set(PACK, "minecraft:bread x4");
		assertLayout(context, world, "sandığa bakarken yendi", expected);
		setEnabled(context, false);
		world.getServer().runCommand("execute at @a run setblock ~ ~ ~2 minecraft:air");
		context.waitTicks(5);
	}

	// --- plumbing ---

	/** Sword in hand, a pickaxe elsewhere on the hotbar, bread in the pack; returns the layout. */
	private static List<String> setUp(ClientGameTestContext context, TestSingleplayerContext world, int bread) {
		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("item replace entity @a hotbar." + HELD + " with minecraft:diamond_sword");
		world.getServer().runCommand("item replace entity @a hotbar." + OTHER + " with minecraft:iron_pickaxe");
		world.getServer().runCommand("item replace entity @a inventory." + (PACK - Inventory.getSelectionSize())
				+ " with minecraft:bread " + bread);
		world.getServer().runOnServer(server -> player(server).getFoodData().setFoodLevel(HUNGRY));
		context.waitTicks(5);
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(HELD));
		context.waitTicks(5);

		List<String> layout = layout(world);

		if (!layout.get(PACK).equals("minecraft:bread x" + bread) || !layout.get(HELD).equals("minecraft:diamond_sword x1")) {
			throw new AssertionError("hazırlık başarısız: " + layout);
		}

		return layout;
	}

	private static void assertLayout(ClientGameTestContext context, TestSingleplayerContext world,
			String when, List<String> expected) {
		List<String> actual = layout(world);

		if (!actual.equals(expected)) {
			throw new AssertionError(when + ": envanter düzeni geri gelmedi\n  beklenen " + describe(expected)
					+ "\n  olan     " + describe(actual));
		}

		int selected = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());

		if (selected != HELD) {
			throw new AssertionError(when + ": seçili slot " + HELD + " olmalıydı, bulunan " + selected);
		}

		if (context.computeOnClient(client -> client.options.keyUse.isDown())) {
			throw new AssertionError(when + ": kullanım tuşu bırakılmalıydı");
		}
	}

	/** Every hotbar and backpack slot, as "id xcount", on the server. */
	private static List<String> layout(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> {
			Inventory inventory = player(server).getInventory();
			List<String> slots = new ArrayList<>();

			for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
				ItemStack stack = inventory.getItem(slot);
				slots.add(stack.isEmpty() ? "" : InventoryUtil.idOf(stack) + " x" + stack.getCount());
			}

			return slots;
		});
	}

	/** Only the filled slots, so a failure message is readable. */
	private static String describe(List<String> layout) {
		StringBuilder out = new StringBuilder("{");

		for (int slot = 0; slot < layout.size(); slot++) {
			if (!layout.get(slot).isEmpty()) {
				out.append(' ').append(slot).append('=').append(layout.get(slot));
			}
		}

		return out.append(" }").toString();
	}

	@SuppressWarnings("unchecked")
	private static void scope(ClientGameTestContext context, InventoryUtil.Scope scope) {
		context.runOnClient(client -> autoEat().settings()
				.<EnumSetting<InventoryUtil.Scope>>byId("scope")
				.orElseThrow(() -> new AssertionError("kapsam ayarı yok"))
				.set(scope));
	}

	@SuppressWarnings("unchecked")
	private static void hand(ClientGameTestContext context, EatingHand hand) {
		context.runOnClient(client -> autoEat().settings()
				.<EnumSetting<EatingHand>>byId("eating_hand")
				.orElseThrow(() -> new AssertionError("yeme eli ayarı yok"))
				.set(hand));
	}

	private static void setEnabled(ClientGameTestContext context, boolean enabled) {
		context.runOnClient(client -> autoEat().setEnabled(enabled));
	}

	private static Module autoEat() {
		return SurvivalKit.modules().byId("autoeat")
				.orElseThrow(() -> new AssertionError("autoeat modülü kayıtlı değil"));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}
}
