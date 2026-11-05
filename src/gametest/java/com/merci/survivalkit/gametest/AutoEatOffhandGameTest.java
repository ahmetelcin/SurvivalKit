package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.autoeat.EatingHand;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.EnumSetting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Eating out of the off hand, in a running game.
 *
 * <p>The state machine is tested on its own; what only a real client can show is what the
 * game does around it. Holding the use key is the part that bites: with a block in the main
 * hand the game places it, with an axe it strips the log it is pointed at, with a shield it
 * raises the shield — all before it ever looks at the food in the off hand. So the bite is
 * started for the off hand directly and the key is only held to keep it going.
 *
 * <p>Each claim here is paired with a negative control, because every one of these is the
 * kind of thing that passes by doing nothing at all.
 */
public class AutoEatOffhandGameTest implements FabricClientGameTest {
	private static final int HUNGRY = 10;
	private static final int BREAD = 5;
	/** The hotbar slot the player is holding something precious in. */
	private static final int HELD = 0;
	/** Where the bread is put, which is not the held slot. */
	private static final int FOOD_SLOT = 3;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(5);
			world.getServer().runCommand("gamemode survival @a");

			aSwordInTheMainHandIsNeverPutDown(context, world);
			theMainHandModeDoesDisturbIt(context, world);

			aBlockInTheMainHandIsNotPlaced(context, world);
			theSameSetupWithTheUseKeyDoesPlaceOne(context, world);

			aTotemInTheOffHandIsNotMoved(context, world);
			withoutTheRuleTheTotemIsMoved(context, world);

			context.takeScreenshot("autoeat-diger-el");

			SurvivalKit.LOGGER.info("[gametest] diğer elde yeme doğrulandı (kılıç elde kaldı, blok konmadı, totem yerinden oynamadı; üçünün de negatif kontrolü tersini gösterdi)");
		}
	}

	// --- the main hand keeps what it was holding ---

	private void aSwordInTheMainHandIsNeverPutDown(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, "minecraft:diamond_sword");
		offhandMode(context, EatingHand.OFF_HAND);

		eatOnce(context, world);

		int selected = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());

		if (selected != HELD) {
			throw new AssertionError("ana el slotu değişmemeliydi, bulunan " + selected);
		}

		if (!mainHandIs(context, Items.DIAMOND_SWORD)) {
			throw new AssertionError("ana elde hâlâ kılıç olmalıydı, bulunan: " + mainHandName(context));
		}

		if (!offhandIsEmpty(context)) {
			throw new AssertionError("yemek bitince diğer el boşalmalıydı, bulunan: " + offhandName(context));
		}
	}

	/**
	 * Negative control. In main-hand mode the held slot does change during the meal — which
	 * is the behaviour the off-hand mode exists to avoid, and proof that the check above is
	 * looking at something that can move.
	 */
	private void theMainHandModeDoesDisturbIt(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, "minecraft:diamond_sword");
		offhandMode(context, EatingHand.MAIN_HAND);

		setEnabled(context, true);
		boolean moved = waitForOnClient(context,
				client -> client.player.getInventory().getSelectedSlot() != HELD, 200);
		setEnabled(context, false);
		context.waitTicks(10);

		if (!moved) {
			throw new AssertionError("negatif kontrol başarısız: ana el modunda seçili slot değişmeliydi");
		}
	}

	// --- nothing is placed, built or stripped along the way ---

	private void aBlockInTheMainHandIsNotPlaced(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, "minecraft:dirt 16");
		lookAtTheGround(context, world);
		offhandMode(context, EatingHand.OFF_HAND);

		int before = count(world, Items.DIRT);
		eatOnce(context, world);
		int after = count(world, Items.DIRT);

		if (after != before) {
			throw new AssertionError("yemek yerken blok konmamalıydı, toprak " + before + " -> " + after);
		}
	}

	/**
	 * Negative control. The same block, the same ground, the use key held the way the naive
	 * version of this feature would hold it: a block goes down. So the count above is a
	 * count that notices.
	 */
	private void theSameSetupWithTheUseKeyDoesPlaceOne(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, "minecraft:dirt 16");
		lookAtTheGround(context, world);

		int before = count(world, Items.DIRT);

		context.runOnClient(client -> client.options.keyUse.setDown(true));
		context.waitTicks(20);
		context.runOnClient(client -> client.options.keyUse.setDown(false));
		context.waitTicks(10);

		int after = count(world, Items.DIRT);

		if (after >= before) {
			throw new AssertionError("negatif kontrol başarısız: kullanım tuşu basılıyken blok konmalıydı, toprak "
					+ before + " -> " + after);
		}
	}

	// --- the totem ---

	private void aTotemInTheOffHandIsNotMoved(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, "minecraft:diamond_sword");
		giveTotem(context, world);
		offhandMode(context, EatingHand.OFF_HAND);
		totemRule(context, true);

		eatOnce(context, world);

		if (!offhandIs(context, Items.TOTEM_OF_UNDYING)) {
			throw new AssertionError("totem diğer elde kalmalıydı, bulunan: " + offhandName(context));
		}
	}

	/** Negative control: with the rule off, the totem does leave the off hand for the meal. */
	private void withoutTheRuleTheTotemIsMoved(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, "minecraft:diamond_sword");
		giveTotem(context, world);
		offhandMode(context, EatingHand.OFF_HAND);
		totemRule(context, false);

		setEnabled(context, true);
		boolean left = waitForOnClient(context,
				client -> !client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), 200);
		setEnabled(context, false);
		context.waitTicks(20);

		if (!left) {
			throw new AssertionError("negatif kontrol başarısız: kural kapalıyken totem diğer elden çıkmalıydı");
		}

		// Even then it is put back: the rule decides where the meal happens, not whether the
		// off hand is handed back.
		if (!offhandIs(context, Items.TOTEM_OF_UNDYING)) {
			throw new AssertionError("kural kapalı olsa da totem geri konmalıydı, bulunan: " + offhandName(context));
		}

		totemRule(context, true);
	}

	// --- plumbing ---

	/** A clean player: the held slot, the food, an empty off hand and an empty stomach. */
	private static void setUp(ClientGameTestContext context, TestSingleplayerContext world, String held) {
		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("item replace entity @a hotbar." + HELD + " with " + held);
		world.getServer().runCommand("item replace entity @a hotbar." + FOOD_SLOT + " with minecraft:bread " + BREAD);
		world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:air");
		world.getServer().runOnServer(server -> player(server).getFoodData().setFoodLevel(HUNGRY));
		context.waitTicks(5);
	}

	private static void giveTotem(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:totem_of_undying");
		context.waitTicks(5);
	}

	/**
	 * A flat floor with clear air over it, and the player looking down it at an angle.
	 *
	 * <p>Not straight down, which is the trap: a block aimed at the player's own feet is
	 * refused by the game because the player is standing in the space it would fill. A test
	 * set up that way can never place a block, so "no block was placed" would pass whatever
	 * the mod did. At forty-five degrees the aim lands a block and a half ahead, where there
	 * is nothing in the way — the negative control below is what proves it.
	 */
	private static void lookAtTheGround(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("execute at @a run fill ~-3 ~ ~-3 ~3 ~3 ~3 minecraft:air");
		world.getServer().runCommand("execute at @a run fill ~-3 ~-1 ~-3 ~3 ~-1 ~3 minecraft:stone");
		world.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 45");
		context.waitTicks(10);
	}

	/** One whole meal: switched on, fed, switched off again. */
	private static void eatOnce(ClientGameTestContext context, TestSingleplayerContext world) {
		selectHeldSlot(context);
		setEnabled(context, true);
		world.getServer().waitFor(server -> player(server).getFoodData().getFoodLevel() > HUNGRY, 200);
		setEnabled(context, false);
		context.waitTicks(20);

		if (context.computeOnClient(client -> client.options.keyUse.isDown())) {
			throw new AssertionError("yemek bitince kullanım tuşu bırakılmalıydı");
		}
	}

	private static void selectHeldSlot(ClientGameTestContext context) {
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(HELD));
		context.waitTicks(2);
	}

	/** Polls the client, because what is in a hand is a client-side fact here. */
	private static boolean waitForOnClient(ClientGameTestContext context,
			java.util.function.Predicate<net.minecraft.client.Minecraft> wanted, int ticks) {
		for (int tick = 0; tick < ticks; tick++) {
			if (context.computeOnClient(wanted::test)) {
				return true;
			}

			context.waitTick();
		}

		return false;
	}

	private static boolean mainHandIs(ClientGameTestContext context, net.minecraft.world.item.Item item) {
		return context.computeOnClient(client -> client.player.getMainHandItem().is(item));
	}

	private static String mainHandName(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getMainHandItem().getHoverName().getString());
	}

	private static boolean offhandIs(ClientGameTestContext context, net.minecraft.world.item.Item item) {
		return context.computeOnClient(client -> client.player.getOffhandItem().is(item));
	}

	private static boolean offhandIsEmpty(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getOffhandItem().isEmpty());
	}

	private static String offhandName(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getOffhandItem().getHoverName().getString());
	}

	private static int count(TestSingleplayerContext world, net.minecraft.world.item.Item item) {
		return world.getServer().computeOnServer(server -> player(server).getInventory()
				.getNonEquipmentItems()
				.stream()
				.filter(stack -> stack.is(item))
				.mapToInt(ItemStack::getCount)
				.sum());
	}

	@SuppressWarnings("unchecked")
	private static void offhandMode(ClientGameTestContext context, EatingHand hand) {
		context.runOnClient(client -> autoEat().settings()
				.<EnumSetting<EatingHand>>byId("eating_hand")
				.orElseThrow(() -> new AssertionError("yeme eli ayarı yok"))
				.set(hand));
		context.waitTicks(2);
	}

	private static void totemRule(ClientGameTestContext context, boolean on) {
		context.runOnClient(client -> autoEat().settings()
				.<BoolSetting>byId("keep_offhand_totem")
				.orElseThrow(() -> new AssertionError("totem ayarı yok"))
				.set(on));
		context.waitTicks(2);
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
