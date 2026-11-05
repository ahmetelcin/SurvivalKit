package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.RegistryListSetting;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The AutoEat settings, in a running game: the priority list is obeyed, the block list is
 * obeyed, and the hotbar slot comes back when the bite ends — while the module is still on,
 * which is what tells this apart from the slot restore on shutdown.
 */
public class AutoEatSettingsGameTest implements FabricClientGameTest {
	private static final int HUNGRY = 15;
	private static final int PATIENCE_TICKS = 200;
	/** Long enough for several bites, so "nothing happened" means it really did not. */
	private static final int LONG_ENOUGH_TO_EAT = 80;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(5);
			world.getServer().runCommand("gamemode survival @a");

			thePriorityListIsObeyed(context, world);
			theListFallsThroughToItsSecondEntry(context, world);
			theBlockListIsObeyed(context, world);
			theSlotComesBackWhenTheBiteEndsNotWhenTheModuleStops(context, world);
		}
	}

	/** Bread is listed and steak is not, so the steak must still be there afterwards. */
	private void thePriorityListIsObeyed(ClientGameTestContext context, TestSingleplayerContext world) {
		reset(context, world);
		give(world, "minecraft:bread", 3);
		give(world, "minecraft:cooked_beef", 3);
		setList(context, "priority", List.of("minecraft:bread"));
		starve(context, world);

		setEnabled(context, true);
		world.getServer().waitFor(server -> hunger(server) > HUNGRY, PATIENCE_TICKS);
		setEnabled(context, false);

		int bread = count(world, Items.BREAD);
		int beef = count(world, Items.COOKED_BEEF);

		if (bread != 2 || beef != 3) {
			throw new AssertionError("listedeki ekmek yenmeliydi, biftek durmalıydı; ekmek " + bread + ", biftek " + beef);
		}
	}

	/** Nothing of the first entry is in reach, so the second one has to carry it. */
	private void theListFallsThroughToItsSecondEntry(ClientGameTestContext context, TestSingleplayerContext world) {
		reset(context, world);
		give(world, "minecraft:cooked_beef", 3);
		setList(context, "priority", List.of("minecraft:cooked_porkchop", "minecraft:cooked_beef"));
		starve(context, world);

		setEnabled(context, true);
		world.getServer().waitFor(server -> hunger(server) > HUNGRY, PATIENCE_TICKS);
		setEnabled(context, false);

		int beef = count(world, Items.COOKED_BEEF);

		if (beef != 2) {
			throw new AssertionError("listenin ikinci girdisi yenmeliydi, biftek " + beef);
		}
	}

	/** The only food there is sits on the block list, so the player stays hungry. */
	private void theBlockListIsObeyed(ClientGameTestContext context, TestSingleplayerContext world) {
		reset(context, world);
		give(world, "minecraft:bread", 3);
		setList(context, "blocked", List.of("minecraft:bread"));
		starve(context, world);

		setEnabled(context, true);
		context.waitTicks(LONG_ENOUGH_TO_EAT);
		setEnabled(context, false);

		int bread = count(world, Items.BREAD);
		int hunger = world.getServer().computeOnServer(AutoEatSettingsGameTest::hunger);

		// Hunger may drift down on its own; only eating can push it up, so that is what is checked.
		if (bread != 3 || hunger > HUNGRY) {
			throw new AssertionError("kara listedeki ekmek yenmemeliydi; ekmek " + bread + ", açlık " + hunger);
		}
	}

	private void theSlotComesBackWhenTheBiteEndsNotWhenTheModuleStops(ClientGameTestContext context, TestSingleplayerContext world) {
		reset(context, world);
		give(world, "minecraft:bread", 3);
		starve(context, world);
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(8));

		setEnabled(context, true);
		world.getServer().waitFor(server -> hunger(server) > HUNGRY, PATIENCE_TICKS);
		context.waitTicks(10);

		boolean stillOn = context.computeOnClient(client -> module().isEnabled());
		int selected = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		setEnabled(context, false);

		if (!stillOn) {
			throw new AssertionError("modül hâlâ açık olmalıydı, yoksa test kapanıştaki dönüşü ölçer");
		}

		if (selected != 8) {
			throw new AssertionError("yemek bitince eski slota dönülmeliydi, bulunan " + selected);
		}

		SurvivalKit.LOGGER.info("[gametest] AutoEat ayarları doğrulandı (tercih sırası, yedeğe düşme, kara liste, slot dönüşü)");
	}

	// --- plumbing ---

	private static Module module() {
		return SurvivalKit.modules().byId("autoeat").orElseThrow(() -> new AssertionError("autoeat modülü kayıtlı değil"));
	}

	private static void setEnabled(ClientGameTestContext context, boolean enabled) {
		context.runOnClient(client -> module().setEnabled(enabled));
	}

	private static void setList(ClientGameTestContext context, String settingId, List<String> ids) {
		context.runOnClient(client -> module().settings()
				.<RegistryListSetting>byId(settingId)
				.orElseThrow(() -> new AssertionError("ayar yok: " + settingId))
				.set(ids));
	}

	/** Back to shipped defaults and an empty inventory, so each case starts from the same place. */
	private static void reset(ClientGameTestContext context, TestSingleplayerContext world) {
		setEnabled(context, false);
		context.runOnClient(client -> module().settings().reset());
		world.getServer().runCommand("clear @a");
		context.waitTicks(2);
	}

	private static void starve(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> player(server).getFoodData().setFoodLevel(HUNGRY));
		context.waitTicks(2);
	}

	private static void give(TestSingleplayerContext world, String id, int count) {
		world.getServer().runCommand("give @a " + id + " " + count);
	}

	private static int hunger(MinecraftServer server) {
		return player(server).getFoodData().getFoodLevel();
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static int count(TestSingleplayerContext world, Item item) {
		return world.getServer().computeOnServer(server -> player(server).getInventory()
				.getNonEquipmentItems()
				.stream()
				.filter(stack -> stack.is(item))
				.mapToInt(ItemStack::getCount)
				.sum());
	}
}
