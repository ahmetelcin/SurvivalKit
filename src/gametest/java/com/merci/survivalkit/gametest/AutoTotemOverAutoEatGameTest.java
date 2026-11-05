package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.autoeat.EatingHand;
import com.merci.survivalkit.autototem.TotemMode;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.settings.IntSetting;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The two modules reaching for the same hand in a running game.
 *
 * <p>Eating in the off hand is the least urgent thing the mod does and keeping a totem there
 * is the most urgent. What has to be true is not only that the totem wins: the meal has to be
 * put back the way it was found, and AutoEat has to carry on afterwards rather than be left
 * broken by having been interrupted.
 */
public class AutoTotemOverAutoEatGameTest implements FabricClientGameTest {
	private static final int HUNGRY = 10;
	private static final int LOAVES = 5;

	/** The hotbar slot holding a sword, which the meal must not disturb. */
	private static final int HELD = 0;
	private static final int FOOD_SLOT = 3;
	private static final int TOTEM_SLOT = 5;

	private static final int THRESHOLD = 10;
	private static final float HURT_TO = 6.0f;

	/** Long enough for a swap that is never coming to have obviously not come. */
	private static final int PATIENCE_TICKS = 100;

	/**
	 * How long the totem may take to reach the hand a meal was using. Short on purpose: it
	 * has to be under the 32 ticks a bite takes, or a meal finishing by itself would look
	 * exactly like an interruption.
	 */
	private static final int INTERRUPT_BUDGET_TICKS = 10;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(5);
			world.getServer().runCommand("gamemode survival @a");

			theTotemTakesTheHandOutOfTheMealsMouth(context, world);
			andTheMealCarriesOnInTheMainHand(context, world);
			withHealthLeftAloneTheMealIsNotInterrupted(context, world);

			context.takeScreenshot("autototem-autoeat");
			bothOff(context);

			SurvivalKit.LOGGER.info("[gametest] AutoTotem + AutoEat doğrulandı (can düşünce yemek kesildi, ekmek envantere döndü, totem diğer ele girdi; AutoEat ana elde yemeye devam etti, kılıç geri geldi; can iyiyken yemek kesilmedi)");
		}
	}

	/**
	 * Health falls while the bread is in the off hand. The totem has to end up there, the
	 * bread has to be back in the inventory, and the use key must not be left down.
	 */
	private void theTotemTakesTheHandOutOfTheMealsMouth(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world);
		eatUntilTheBreadIsInTheOffHand(context, world);

		int hungerAtTheHit = hunger(world);
		hurtTo(world, HURT_TO);

		// A budget, not patience, and that is the whole difference. A bite takes 32 ticks, so
		// waiting a hundred would also be satisfied by the meal simply finishing on its own
		// and the hand falling free — which is exactly what an equal-priority mutation was
		// caught doing here, passing this test green. Inside ten ticks nothing has finished.
		if (!waitFor(context, client -> client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), INTERRUPT_BUDGET_TICKS)) {
			throw new AssertionError("can düşünce yemek kesilip totem diğer ele girmeliydi, elde: " + offhandName(context));
		}

		if (hunger(world) != hungerAtTheHit) {
			throw new AssertionError("ısırık bitmeden kesilmeliydi, açlık " + hungerAtTheHit + " -> " + hunger(world));
		}

		if (context.computeOnClient(client -> client.options.keyUse.isDown())) {
			throw new AssertionError("yemek kesilince kullanım tuşu bırakılmalıydı");
		}

		// Back in the slot it came out of, not merely somewhere: the undo is a swap, and a
		// count of the whole inventory cannot tell the two apart.
		if (!breadIsBackInItsSlot(context)) {
			throw new AssertionError("ekmek geldiği slota dönmeliydi, orada: " + itemNameAt(context, FOOD_SLOT));
		}
	}

	/**
	 * Interrupted is not switched off. AutoEat is still hungry and still on, so it eats — in
	 * the main hand, because the totem in the off hand is not something it will move — and
	 * when the meal is over the sword is back in the hand that held it.
	 */
	private void andTheMealCarriesOnInTheMainHand(ClientGameTestContext context, TestSingleplayerContext world) {
		int before = world.getServer().computeOnServer(server -> player(server).getFoodData().getFoodLevel());

		if (!world.getServer().computeOnServer(server -> player(server).getFoodData().getFoodLevel() < 20)) {
			throw new AssertionError("hazırlık: oyuncu hâlâ aç olmalıydı");
		}

		boolean fed = waitForServer(context, world,
				server -> player(server).getFoodData().getFoodLevel() > before, 400);

		if (!fed) {
			throw new AssertionError("kesildikten sonra AutoEat yemeye devam etmeliydi, açlık: " + before);
		}

		if (!offhandIs(context, Items.TOTEM_OF_UNDYING)) {
			throw new AssertionError("ana elde yenirken totem diğer elde kalmalıydı, elde: " + offhandName(context));
		}

		bothOff(context);
		context.waitTicks(20);

		if (!mainHandIs(context, Items.DIAMOND_SWORD)) {
			throw new AssertionError("yemek bitince ana eldeki kılıç geri gelmeliydi, elde: " + mainHandName(context));
		}

		int selected = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());

		if (selected != HELD) {
			throw new AssertionError("seçili slot geri dönmeliydi, bulunan: " + selected);
		}
	}

	/**
	 * Negative control. The same meal with health left alone is not interrupted at all: the
	 * bread stays in the off hand and the totem stays in the bag. Without this, the test
	 * above could be passing on a module that grabs the hand whatever the health.
	 */
	private void withHealthLeftAloneTheMealIsNotInterrupted(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world);
		eatUntilTheBreadIsInTheOffHand(context, world);

		// The same window the claim above is measured in, and nothing takes the hand in it.
		if (waitFor(context, client -> client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING),
				INTERRUPT_BUDGET_TICKS)) {
			throw new AssertionError("negatif kontrol başarısız: can iyiyken totem ele girmemeliydi");
		}

		bothOff(context);
		context.waitTicks(20);
	}

	// --- plumbing ---

	/** A sword in hand, bread in the bag, one totem in the bag, an empty off hand, hungry. */
	private static void setUp(ClientGameTestContext context, TestSingleplayerContext world) {
		bothOff(context);
		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("item replace entity @a hotbar." + HELD + " with minecraft:diamond_sword");
		world.getServer().runCommand("item replace entity @a hotbar." + FOOD_SLOT + " with minecraft:bread " + LOAVES);
		world.getServer().runCommand("item replace entity @a hotbar." + TOTEM_SLOT + " with minecraft:totem_of_undying 1");
		world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:air");
		world.getServer().runOnServer(server -> {
			player(server).getFoodData().setFoodLevel(HUNGRY);
			player(server).setHealth(20.0f);
		});

		eatingHand(context, EatingHand.OFF_HAND);
		keepOffhandTotem(context, true);
		totemMode(context, TotemMode.HEALTH_THRESHOLD);
		threshold(context, THRESHOLD);
		restoreOffhand(context, true);

		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(HELD));
		context.waitTicks(5);
	}

	/**
	 * Both modules on, and the meal actually begun in the off hand.
	 *
	 * <p>AutoTotem is on from the start on purpose: in the health mode it leaves the hand
	 * alone while health is full, which is what lets AutoEat have it first. That the bread
	 * really does get there is the setup this test needs — without it the interruption below
	 * would have nothing to interrupt and would pass by default.
	 */
	private static void eatUntilTheBreadIsInTheOffHand(ClientGameTestContext context, TestSingleplayerContext world) {
		on(context, autoTotem());
		on(context, autoEat());

		if (!waitFor(context, client -> client.player.getOffhandItem().is(Items.BREAD), PATIENCE_TICKS)) {
			throw new AssertionError("hazırlık: ekmek diğer ele girip yenmeye başlamalıydı, elde: "
					+ offhandName(context));
		}
	}

	private static void hurtTo(TestSingleplayerContext world, float health) {
		world.getServer().runOnServer(server -> player(server).setHealth(health));
	}

	private static boolean waitFor(ClientGameTestContext context, Predicate<Minecraft> wanted, int ticks) {
		for (int tick = 0; tick < ticks; tick++) {
			if (context.computeOnClient(wanted::test)) {
				return true;
			}

			context.waitTick();
		}

		return false;
	}

	/**
	 * Polls the server, the way {@link #waitFor} polls the client. Not the API's own
	 * {@code waitFor}, which throws on a timeout: a plain false here is what lets the message
	 * below say which claim it was that ran out of patience.
	 */
	private static boolean waitForServer(ClientGameTestContext context, TestSingleplayerContext world,
			Predicate<MinecraftServer> wanted, int ticks) {
		for (int tick = 0; tick < ticks; tick++) {
			if (world.getServer().computeOnServer(wanted::test)) {
				return true;
			}

			context.waitTick();
		}

		return false;
	}

	private static int hunger(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> player(server).getFoodData().getFoodLevel());
	}

	private static boolean breadIsBackInItsSlot(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			ItemStack there = client.player.getInventory().getItem(FOOD_SLOT);
			return there.is(Items.BREAD) && there.getCount() == LOAVES;
		});
	}

	private static String itemNameAt(ClientGameTestContext context, int slot) {
		return context.computeOnClient(client -> {
			ItemStack there = client.player.getInventory().getItem(slot);
			return there.isEmpty() ? "boş" : there.getCount() + "x " + there.getHoverName().getString();
		});
	}

	private static boolean offhandIs(ClientGameTestContext context, net.minecraft.world.item.Item item) {
		return context.computeOnClient(client -> client.player.getOffhandItem().is(item));
	}

	private static String offhandName(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getOffhandItem().getHoverName().getString());
	}

	private static boolean mainHandIs(ClientGameTestContext context, net.minecraft.world.item.Item item) {
		return context.computeOnClient(client -> client.player.getMainHandItem().is(item));
	}

	private static String mainHandName(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getMainHandItem().getHoverName().getString());
	}

	private static void on(ClientGameTestContext context, Module module) {
		context.runOnClient(client -> module.setEnabled(true));
		context.waitTicks(2);
	}

	private static void bothOff(ClientGameTestContext context) {
		context.runOnClient(client -> {
			autoEat().setEnabled(false);
			autoTotem().setEnabled(false);
		});
		context.waitTicks(2);
	}

	private static void eatingHand(ClientGameTestContext context, EatingHand hand) {
		context.runOnClient(client -> autoEat().settings()
				.<EnumSetting<EatingHand>>byId("eating_hand")
				.orElseThrow(() -> new AssertionError("yeme eli ayarı yok"))
				.set(hand));
	}

	private static void keepOffhandTotem(ClientGameTestContext context, boolean on) {
		context.runOnClient(client -> autoEat().settings()
				.<BoolSetting>byId("keep_offhand_totem")
				.orElseThrow(() -> new AssertionError("totem ayarı yok"))
				.set(on));
	}

	private static void totemMode(ClientGameTestContext context, TotemMode mode) {
		context.runOnClient(client -> autoTotem().settings()
				.<EnumSetting<TotemMode>>byId("mode")
				.orElseThrow(() -> new AssertionError("mod ayarı yok"))
				.set(mode));
	}

	private static void threshold(ClientGameTestContext context, int value) {
		context.runOnClient(client -> autoTotem().settings()
				.<IntSetting>byId("health_threshold")
				.orElseThrow(() -> new AssertionError("can eşiği ayarı yok"))
				.set(value));
	}

	private static void restoreOffhand(ClientGameTestContext context, boolean on) {
		context.runOnClient(client -> autoTotem().settings()
				.<BoolSetting>byId("restore_offhand")
				.orElseThrow(() -> new AssertionError("geri koyma ayarı yok"))
				.set(on));
	}

	private static Module autoEat() {
		return SurvivalKit.modules().byId("autoeat")
				.orElseThrow(() -> new AssertionError("autoeat modülü kayıtlı değil"));
	}

	private static Module autoTotem() {
		return SurvivalKit.modules().byId("autototem")
				.orElseThrow(() -> new AssertionError("autototem modülü kayıtlı değil"));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}
}
