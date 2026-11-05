package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.autoeat.ThresholdMode;
import com.merci.survivalkit.menu.SurvivalKitScreen;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.settings.IntSetting;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * When a meal starts, in a running game: the automatic line waits for room for the whole
 * food, low health does not wait at all, and the menu only shows the fixed line while it
 * is the one in use.
 *
 * <p>Every "it does not eat yet" is followed by the same setup one point further down that
 * does eat, so a quiet stretch is a decision and not a module that never does anything.
 */
public class AutoEatTriggerGameTest implements FabricClientGameTest {
	private static final InputConstants.Key MENU_KEY =
		InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT);
	/** Long enough for a bite to start and finish, so "nothing happened" means it. */
	private static final int QUIET = 80;
	private static final int PATIENCE = 200;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestLanguage.ensureTurkish(context);
			context.waitTicks(5);
			world.getServer().runCommand("gamemode survival @a");

			theFixedLineIsOnlyShownInFixedMode(context);
			theEarlyHealthIsOnlyShownWhileTheRuleIsOn(context);

			steakWaitsForTwelve(context, world);
			breadWaitsForFifteen(context, world);

			lowHealthEatsWithoutWaitingForHunger(context, world);
			withTheRuleOffLowHealthWaits(context, world);
			theGoldenAppleRuleGoesFirst(context, world);

			resetEverything(context);
			SurvivalKit.LOGGER.info("[gametest] eşik modu doğrulandı (menüde sabit eşik yalnız Sabit'te, "
					+ "otomatikte biftek 13'te değil 12'de, ekmek 16'da değil 15'te; düşük canda 19'da yer, "
					+ "kural kapalıyken yemez; altın elma kuralı önce çalışır)");
		}
	}

	// --- the menu, through real clicks ---

	private void theFixedLineIsOnlyShownInFixedMode(ClientGameTestContext context) {
		openMenuOnAutoEat(context);

		if (screen(context).settingCenterY("threshold").isEmpty()) {
			throw new AssertionError("Sabit modda açlık eşiği görünmeliydi");
		}

		pick(context, "threshold_mode", ThresholdMode.AUTO);

		if (thresholdMode().get() != ThresholdMode.AUTO) {
			throw new AssertionError("tık Otomatik'i seçmeliydi, bulunan " + thresholdMode().get());
		}

		if (screen(context).settingCenterY("threshold").isPresent()) {
			throw new AssertionError("Otomatik modda açlık eşiği gizlenmeliydi");
		}

		pick(context, "threshold_mode", ThresholdMode.FIXED);

		if (thresholdMode().get() != ThresholdMode.FIXED || screen(context).settingCenterY("threshold").isEmpty()) {
			throw new AssertionError("Sabit'e dönünce açlık eşiği geri gelmeliydi");
		}
	}

	private void theEarlyHealthIsOnlyShownWhileTheRuleIsOn(ClientGameTestContext context) {
		if (screen(context).settingCenterY("early_eating_health").isPresent()) {
			throw new AssertionError("kural kapalıyken erken yeme can eşiği görünmemeliydi");
		}

		clickSetting(context, "eat_early_when_hurt", screen(context).controlToggleCenterX());

		if (!bool("eat_early_when_hurt").get()) {
			throw new AssertionError("tık erken yemeyi açmalıydı");
		}

		if (screen(context).settingCenterY("early_eating_health").isEmpty()) {
			throw new AssertionError("kural açılınca erken yeme can eşiği görünmeliydi");
		}

		clickSetting(context, "eat_early_when_hurt", screen(context).controlToggleCenterX());

		if (bool("eat_early_when_hurt").get() || screen(context).settingCenterY("early_eating_health").isPresent()) {
			throw new AssertionError("kural kapanınca erken yeme can eşiği yine gizlenmeliydi");
		}

		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);
	}

	// --- automatic: the food draws the line ---

	private void steakWaitsForTwelve(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, "minecraft:cooked_beef 3");
		context.runOnClient(client -> thresholdMode().set(ThresholdMode.AUTO));

		expectNoMeal(context, world, 13, 20.0f, Items.COOKED_BEEF, "otomatik, biftek, açlık 13");
		expectMeal(context, world, 12, 20.0f, Items.COOKED_BEEF, "otomatik, biftek, açlık 12");
	}

	private void breadWaitsForFifteen(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, "minecraft:bread 3");
		context.runOnClient(client -> thresholdMode().set(ThresholdMode.AUTO));

		expectNoMeal(context, world, 16, 20.0f, Items.BREAD, "otomatik, ekmek, açlık 16");
		expectMeal(context, world, 15, 20.0f, Items.BREAD, "otomatik, ekmek, açlık 15");
	}

	// --- hurt: eat now ---

	private void lowHealthEatsWithoutWaitingForHunger(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, "minecraft:bread 3");
		earlyRule(context, true, 14);

		expectMeal(context, world, 19, 10.0f, Items.BREAD, "erken yeme açık, can 10, açlık 19");
	}

	/** Negative control: the same hunger and health, the rule off, and the bread stays. */
	private void withTheRuleOffLowHealthWaits(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, "minecraft:bread 3");
		earlyRule(context, false, 14);

		expectNoMeal(context, world, 19, 10.0f, Items.BREAD, "erken yeme kapalı, can 10, açlık 19");
	}

	/** Below both lines: the golden apple goes, the bread next to it does not. */
	private void theGoldenAppleRuleGoesFirst(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, "minecraft:bread 3");
		world.getServer().runCommand("item replace entity @a hotbar.5 with minecraft:golden_apple 2");
		earlyRule(context, true, 14);
		context.runOnClient(client -> {
			bool("golden_apple").set(true);
			number("health_threshold").set(8);
		});

		expectMeal(context, world, 19, 6.0f, Items.GOLDEN_APPLE, "iki kural da tutuyor, can 6");

		if (count(world, Items.BREAD) != 3) {
			throw new AssertionError("altın elma kuralı önce çalışmalıydı, ekmek yenmiş: " + count(world, Items.BREAD));
		}
	}

	// --- plumbing ---

	private static void setUp(ClientGameTestContext context, TestSingleplayerContext world, String food) {
		setEnabled(context, false);
		resetEverything(context);
		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("effect clear @a");
		world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:diamond_sword");
		world.getServer().runCommand("item replace entity @a hotbar.3 with " + food);
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		context.waitTicks(5);
	}

	/** Puts the player at that hunger and health, lets the module run, and expects nothing. */
	private static void expectNoMeal(ClientGameTestContext context, TestSingleplayerContext world,
			int hunger, float health, Item food, String when) {
		int before = count(world, food);
		setState(context, world, hunger, health);

		setEnabled(context, true);
		context.waitTicks(QUIET);
		setEnabled(context, false);
		context.waitTicks(5);

		if (count(world, food) != before) {
			throw new AssertionError(when + ": yenmemeliydi, " + before + " -> " + count(world, food));
		}
	}

	private static void expectMeal(ClientGameTestContext context, TestSingleplayerContext world,
			int hunger, float health, Item food, String when) {
		int before = count(world, food);
		setState(context, world, hunger, health);

		setEnabled(context, true);
		boolean eaten = waitForOnServer(world, server -> count(server, food) < before, PATIENCE);
		setEnabled(context, false);
		context.waitTicks(20);

		if (!eaten) {
			throw new AssertionError(when + ": yenmeliydi, hâlâ " + count(world, food));
		}
	}

	private static void setState(ClientGameTestContext context, TestSingleplayerContext world, int hunger, float health) {
		world.getServer().runOnServer(server -> {
			player(server).getFoodData().setFoodLevel(hunger);
			player(server).setHealth(health);
		});
		context.waitTicks(5);
	}

	private static boolean waitForOnServer(TestSingleplayerContext world,
			java.util.function.Predicate<MinecraftServer> wanted, int ticks) {
		try {
			world.getServer().waitFor(wanted, ticks);
			return true;
		} catch (AssertionError timedOut) {
			return false;
		}
	}

	private static void earlyRule(ClientGameTestContext context, boolean on, int health) {
		context.runOnClient(client -> {
			bool("eat_early_when_hurt").set(on);
			number("early_eating_health").set(health);
		});
	}

	/** Two values drawn side by side are picked by their half; a list is opened and picked from. */
	private static void pick(ClientGameTestContext context, String settingId, Enum<?> wanted) {
		bringIntoView(context, settingId);
		EnumSetting<?> setting = autoEat().settings().<EnumSetting<?>>byId(settingId).orElseThrow();
		int index = setting.options().indexOf(wanted);
		int count = setting.options().size();
		Rect2i button = context.computeOnClient(client -> screen(context).choiceButton(settingId)
				.orElseThrow(() -> new AssertionError("ayarın butonu ekranda değil: " + settingId)));

		if (!context.computeOnClient(client -> screen(context).choiceOpensAList(settingId))) {
			int x = button.getX() + button.getWidth() * (2 * index + 1) / (2 * count);
			clickAt(context, x, button.getY() + button.getHeight() / 2);
			return;
		}

		clickAt(context, button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2);
		Rect2i option = context.computeOnClient(client -> screen(context).openDropdownOption(index));
		clickAt(context, option.getX() + option.getWidth() / 2, option.getY() + option.getHeight() / 2);
	}

	private static void openMenuOnAutoEat(ClientGameTestContext context) {
		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);

		int y = screen(context).rowCenterY(autoEat())
				.orElseThrow(() -> new AssertionError("modül listede yok: autoeat"));
		clickAt(context, screen(context).nameCenterX(), y);
	}

	private static void clickSetting(ClientGameTestContext context, String settingId, int x) {
		bringIntoView(context, settingId);

		int y = screen(context).settingCenterY(settingId)
				.orElseThrow(() -> new AssertionError("ayar ekranda değil: " + settingId));
		clickAt(context, x, y);
	}

	/** A row outside the visible band refuses clicks, so it is wheeled in the way a player would. */
	private static void bringIntoView(ClientGameTestContext context, String settingId) {
		double scale = context.computeOnClient(AutoEatTriggerGameTest::guiScale);
		int cursorX = context.computeOnClient(client -> screen(context).controlToggleCenterX());

		for (int attempt = 0; attempt < 60; attempt++) {
			int y = screen(context).settingCenterY(settingId)
					.orElseThrow(() -> new AssertionError("ayar ekranda değil: " + settingId));
			int top = context.computeOnClient(client -> screen(context).settingsViewTop());
			int bottom = context.computeOnClient(client -> screen(context).settingsViewBottom());

			if (y >= top && y <= bottom) {
				return;
			}

			context.getInput().setCursorPos(cursorX * scale, (top + bottom) / 2.0 * scale);
			context.getInput().scroll(y < top ? 1 : -1);
			context.waitTick();
		}

		throw new AssertionError("ayar görünür alana kaydırılamadı: " + settingId);
	}

	private static void clickAt(ClientGameTestContext context, int guiX, int guiY) {
		double scale = context.computeOnClient(AutoEatTriggerGameTest::guiScale);

		context.getInput().setCursorPos(guiX * scale, guiY * scale);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static double guiScale(Minecraft client) {
		return client.getWindow().getGuiScale();
	}

	private static SurvivalKitScreen screen(ClientGameTestContext context) {
		SurvivalKitScreen menu = context.computeOnClient(
				client -> client.gui.screen() instanceof SurvivalKitScreen open ? open : null);

		if (menu == null) {
			throw new AssertionError("menü açık olmalıydı");
		}

		return menu;
	}

	/** Leaves the module the way the other tests expect to find it. */
	private static void resetEverything(ClientGameTestContext context) {
		context.runOnClient(client -> {
			thresholdMode().set(ThresholdMode.FIXED);
			bool("eat_early_when_hurt").set(false);
			number("early_eating_health").reset();
			bool("golden_apple").set(false);
			number("health_threshold").reset();
		});
	}

	@SuppressWarnings("unchecked")
	private static EnumSetting<ThresholdMode> thresholdMode() {
		return autoEat().settings().<EnumSetting<ThresholdMode>>byId("threshold_mode")
				.orElseThrow(() -> new AssertionError("eşik modu ayarı yok"));
	}

	private static BoolSetting bool(String id) {
		return autoEat().settings().<BoolSetting>byId(id).orElseThrow(() -> new AssertionError("ayar yok: " + id));
	}

	private static IntSetting number(String id) {
		return autoEat().settings().<IntSetting>byId(id).orElseThrow(() -> new AssertionError("ayar yok: " + id));
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

	private static int count(TestSingleplayerContext world, Item item) {
		return world.getServer().computeOnServer(server -> count(server, item));
	}

	private static int count(MinecraftServer server, Item item) {
		return player(server).getInventory().getNonEquipmentItems().stream()
				.filter(stack -> stack.is(item))
				.mapToInt(ItemStack::getCount)
				.sum();
	}
}
