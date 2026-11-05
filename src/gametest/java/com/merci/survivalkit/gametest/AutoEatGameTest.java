package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;

/** Proves AutoEat really feeds the player in a running game, not just in the state machine. */
public class AutoEatGameTest implements FabricClientGameTest {
	private static final int STARTING_HUNGER = 10;
	private static final int STARTING_BREAD = 5;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(5);

			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("clear @a");
			world.getServer().runCommand("give @a minecraft:bread " + STARTING_BREAD);
			world.getServer().runOnServer(server -> player(server).getFoodData().setFoodLevel(STARTING_HUNGER));
			context.waitTicks(5);

			// The player holds nothing edible in hand, so a slot swap has to happen.
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(8));

			int breadBefore = countBread(world);

			if (breadBefore != STARTING_BREAD) {
				throw new AssertionError("hazırlık başarısız, ekmek sayısı " + breadBefore);
			}

			setEnabled(context, true);
			// Bread takes 32 ticks to eat; allow for the slot swap and server round trips.
			world.getServer().waitFor(server -> player(server).getFoodData().getFoodLevel() > STARTING_HUNGER, 200);

			int hungerAfter = world.getServer().computeOnServer(server -> player(server).getFoodData().getFoodLevel());
			int breadAfter = countBread(world);

			if (breadAfter >= breadBefore) {
				throw new AssertionError("ekmek tüketilmeliydi, " + breadBefore + " -> " + breadAfter);
			}

			setEnabled(context, false);
			context.waitTicks(5);

			int selected = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());

			if (selected != 8) {
				throw new AssertionError("yemek bitince eski slota dönülmeliydi, bulunan " + selected);
			}

			boolean useKeyStuck = context.computeOnClient(client -> client.options.keyUse.isDown());

			if (useKeyStuck) {
				throw new AssertionError("modül kapanınca kullanma tuşu bırakılmalıydı");
			}

			SurvivalKit.LOGGER.info("[gametest] AutoEat doğrulandı (açlık {} -> {}, ekmek {} -> {})",
					STARTING_HUNGER, hungerAfter, breadBefore, breadAfter);
		}
	}

	private static ServerPlayer player(net.minecraft.server.MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static int countBread(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> player(server).getInventory()
				.getNonEquipmentItems()
				.stream()
				.filter(stack -> stack.is(net.minecraft.world.item.Items.BREAD))
				.mapToInt(net.minecraft.world.item.ItemStack::getCount)
				.sum());
	}

	private static void setEnabled(ClientGameTestContext context, boolean enabled) {
		context.runOnClient(client -> SurvivalKit.modules()
				.byId("autoeat")
				.orElseThrow(() -> new AssertionError("autoeat modülü kayıtlı değil"))
				.setEnabled(enabled));
	}
}
