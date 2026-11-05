package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.autototem.TotemMode;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.settings.RegistryListSetting;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * Keeping a totem in the off hand, in a running game.
 *
 * <p>The number that matters is how fast the next one arrives. A totem is spent at the
 * moment of a hit you would otherwise have died to; the seconds after it are the ones where
 * a second hit lands. So the replacement is timed in ticks, not eyeballed.
 */
public class AutoTotemGameTest implements FabricClientGameTest {
	/** How long the next totem may take to reach the off hand, in client ticks. */
	private static final int BUDGET_TICKS = 2;

	/** Long enough for a swap that is never coming to have obviously not come. */
	private static final int PATIENCE_TICKS = 40;

	private static final int TOTEMS = 3;

	/** Hotbar slot the fallback shield is put in, clear of the totems. */
	private static final int SHIELD_SLOT = 5;

	/** Every chat line seen since {@link #watchChat}; a set, so one message counts once. */
	private static final Set<GuiMessage> CHAT = new LinkedHashSet<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(5);
			world.getServer().runCommand("gamemode survival @a");

			aTotemIsPutInTheEmptyOffHand(context, world);
			int ticks = theNextTotemArrivesBeforeTheNextHitCan(context, world);
			withTheModuleOffTheHandStaysEmpty(context, world);

			theHandIsOnlyBorrowedInTheHealthMode(context, world);
			andGivenBackWhenHealthRecovers(context, world);

			whenTheTotemsRunOutTheFallbackItemArrives(context, world);
			withNoFallbackListTheHandIsLeftEmpty(context, world);
			withTheWarningOffNothingIsSaid(context, world);

			context.takeScreenshot("autototem");
			off(context);

			SurvivalKit.LOGGER.info("[gametest] AutoTotem doğrulandı (boş ele totem kondu, patlayan totemin yerine yenisi {} tick'te geldi, modül kapalıyken el boş kaldı; can modunda el ödünç alınıp geri verildi; totem bitince yedek eşya geldi ve uyarı bir kez çıktı)", ticks);
		}
	}

	/** The plain case: a totem in the bag, an empty off hand, and a moment later it is filled. */
	private void aTotemIsPutInTheEmptyOffHand(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, TotemMode.ALWAYS);
		on(context);

		if (!waitForTotemInOffhand(context, PATIENCE_TICKS)) {
			throw new AssertionError("boş diğer ele totem konmalıydı, bulunan: " + offhandName(context));
		}
	}

	/** The one the module exists for, and the only assertion here with a clock on it. */
	private int theNextTotemArrivesBeforeTheNextHitCan(ClientGameTestContext context, TestSingleplayerContext world) {
		int before = count(context);
		popTheTotem(context, world);

		// Measured from the hit landing, not from the hand being seen empty: the module can
		// refill it inside the same tick, and then an empty hand is never on screen at all.
		int ticks = ticksFromTheHitToTheNextTotem(context);

		if (ticks < 0) {
			throw new AssertionError("patlayan totemin yerine yenisi konmalıydı, elde: " + offhandName(context));
		}

		if (ticks > BUDGET_TICKS) {
			throw new AssertionError("yeni totem en fazla " + BUDGET_TICKS + " tick'te gelmeliydi, sürdü: " + ticks);
		}

		int after = count(context);

		if (after >= before) {
			throw new AssertionError("bir totem harcanmalıydı, " + before + " -> " + after);
		}

		return ticks;
	}

	/**
	 * Negative control. The same hit with the module off leaves the hand empty, so the test
	 * above is watching the module rather than something the game does by itself.
	 */
	private void withTheModuleOffTheHandStaysEmpty(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, TotemMode.ALWAYS);
		on(context);

		if (!waitForTotemInOffhand(context, PATIENCE_TICKS)) {
			throw new AssertionError("hazırlık: totem ele girmeliydi");
		}

		off(context);
		popTheTotem(context, world);

		if (!waitForEmptyOffhand(context, PATIENCE_TICKS)) {
			throw new AssertionError("hazırlık: totem patlamalıydı");
		}

		context.waitTicks(20);

		if (offhandIsTotem(context)) {
			throw new AssertionError("negatif kontrol başarısız: modül kapalıyken el boş kalmalıydı");
		}
	}

	// --- the health threshold ---

	private void theHandIsOnlyBorrowedInTheHealthMode(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, TotemMode.HEALTH_THRESHOLD);
		threshold(context, 10);
		restore(context, true);
		world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:shield");
		context.waitTicks(5);
		on(context);
		context.waitTicks(10);

		// Unhurt: the shield is the player's and stays theirs.
		if (offhandIsTotem(context)) {
			throw new AssertionError("can tamken el ödünç alınmamalıydı, elde: " + offhandName(context));
		}

		world.getServer().runOnServer(server -> player(server).setHealth(6.0f));

		if (!waitForTotemInOffhand(context, PATIENCE_TICKS)) {
			throw new AssertionError("can düşünce totem ele girmeliydi, elde: " + offhandName(context));
		}
	}

	private void andGivenBackWhenHealthRecovers(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> player(server).setHealth(20.0f));

		boolean back = waitFor(context, client -> client.player.getOffhandItem().is(Items.SHIELD), PATIENCE_TICKS);

		if (!back) {
			throw new AssertionError("can gelince kalkan geri konmalıydı, elde: " + offhandName(context));
		}
	}

	// --- running out of totems ---

	/**
	 * The last totem is spent and there is no next one. The hand does not stay empty: the
	 * first item on the user's own list takes the place, and chat says why — once.
	 */
	private void whenTheTotemsRunOutTheFallbackItemArrives(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, TotemMode.ALWAYS, 1);
		world.getServer().runCommand("item replace entity @a hotbar." + SHIELD_SLOT + " with minecraft:shield");
		fallback(context, List.of("minecraft:shield"));
		warnWhenOut(context, true);
		context.waitTicks(5);
		on(context);

		if (!waitForTotemInOffhand(context, PATIENCE_TICKS)) {
			throw new AssertionError("hazırlık: son totem ele girmeliydi, elde: " + offhandName(context));
		}

		watchChat(context);
		popTheTotem(context, world);

		if (!waitFor(context, client -> client.player.getOffhandItem().is(Items.SHIELD), PATIENCE_TICKS)) {
			throw new AssertionError("totem bitince listedeki ilk eşya ele girmeliydi, elde: " + offhandName(context));
		}

		// Long enough for a module that says it every tick to have said it many times over.
		context.waitTicks(40);
		int warnings = warnings(context);

		if (warnings != 1) {
			throw new AssertionError("uyarı tam bir kez çıkmalıydı, çıkan: " + warnings);
		}
	}

	/**
	 * Negative control for the list: with nothing on it the hand really is left empty, so the
	 * test above is watching the setting rather than something the game does anyway.
	 */
	private void withNoFallbackListTheHandIsLeftEmpty(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, TotemMode.ALWAYS, 1);
		world.getServer().runCommand("item replace entity @a hotbar." + SHIELD_SLOT + " with minecraft:shield");
		fallback(context, List.of());
		context.waitTicks(5);
		on(context);

		if (!waitForTotemInOffhand(context, PATIENCE_TICKS)) {
			throw new AssertionError("hazırlık: son totem ele girmeliydi");
		}

		popTheTotem(context, world);
		context.waitTicks(20);

		if (!context.computeOnClient(client -> client.player.getOffhandItem().isEmpty())) {
			throw new AssertionError("liste boşken el boş kalmalıydı, elde: " + offhandName(context));
		}
	}

	/** Negative control for the warning: off means silent. */
	private void withTheWarningOffNothingIsSaid(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, TotemMode.ALWAYS, 1);
		warnWhenOut(context, false);
		context.waitTicks(5);
		on(context);

		if (!waitForTotemInOffhand(context, PATIENCE_TICKS)) {
			throw new AssertionError("hazırlık: son totem ele girmeliydi");
		}

		watchChat(context);
		popTheTotem(context, world);
		context.waitTicks(40);

		if (warnings(context) != 0) {
			throw new AssertionError("uyarı kapalıyken hiç çıkmamalıydı, çıkan: " + warnings(context));
		}
	}

	// --- plumbing ---

	/**
	 * Totems one to a slot, never a stack.
	 *
	 * <p>A stack of three in the off hand survives its own use — one is spent and two are
	 * still in hand — so there is nothing for the module to do and the test would prove
	 * nothing. Separate slots make the hand actually empty when a totem is spent.
	 */
	private static void setUp(ClientGameTestContext context, TestSingleplayerContext world, TotemMode mode) {
		setUp(context, world, mode, TOTEMS);
	}

	private static void setUp(ClientGameTestContext context, TestSingleplayerContext world, TotemMode mode, int totems) {
		off(context);
		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:air");
		fallback(context, List.of());

		for (int slot = 1; slot <= totems; slot++) {
			world.getServer().runCommand("item replace entity @a hotbar." + slot + " with minecraft:totem_of_undying 1");
		}

		// An empty main hand, so nothing here depends on what is held.
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		world.getServer().runOnServer(server -> player(server).setHealth(20.0f));
		mode(context, mode);
		context.waitTicks(5);
	}

	/**
	 * A hit nothing survives without one, so the totem in hand is spent.
	 *
	 * <p>Dealt through the server rather than by the {@code damage} command, which this
	 * version's parser would not take, and never by setting health to zero: that kills
	 * without ever asking the totem, which is the whole thing being tested.
	 */
	private static void popTheTotem(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.hurtServer(player.level(), player.damageSources().magic(), 1000.0f);
		});
		context.waitTicks(2);
	}

	private static boolean waitForTotemInOffhand(ClientGameTestContext context, int ticks) {
		return waitFor(context, client -> client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), ticks);
	}

	private static boolean waitForEmptyOffhand(ClientGameTestContext context, int ticks) {
		return waitFor(context, client -> !client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), ticks);
	}

	/**
	 * Client ticks between the hit being survived and a totem being back in the off hand;
	 * -1 if it never came back.
	 *
	 * <p>The hit is spotted by the health the totem leaves behind — one heart — because that
	 * is the first sign of it the client has. Zero is a real answer and the good one: it
	 * means the hand was filled again before the player could see it empty.
	 */
	private static int ticksFromTheHitToTheNextTotem(ClientGameTestContext context) {
		int hitAt = -1;

		for (int tick = 0; tick <= PATIENCE_TICKS; tick++) {
			if (hitAt < 0 && context.computeOnClient(client -> client.player.getHealth() <= 2.0f)) {
				hitAt = tick;
			}

			if (hitAt >= 0 && offhandIsTotem(context)) {
				return tick - hitAt;
			}

			context.waitTick();
		}

		return -1;
	}

	private static boolean waitFor(ClientGameTestContext context,
			java.util.function.Predicate<Minecraft> wanted, int ticks) {
		for (int tick = 0; tick < ticks; tick++) {
			if (context.computeOnClient(wanted::test)) {
				return true;
			}

			context.waitTick();
		}

		return false;
	}

	private static boolean offhandIsTotem(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING));
	}

	private static String offhandName(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getOffhandItem().getHoverName().getString());
	}

	private static int count(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getInventory().getNonEquipmentItems().stream()
				.filter(stack -> stack.is(Items.TOTEM_OF_UNDYING))
				.mapToInt(net.minecraft.world.item.ItemStack::getCount)
				.sum());
	}

	private static void on(ClientGameTestContext context) {
		context.runOnClient(client -> module().setEnabled(true));
		context.waitTicks(2);
	}

	private static void off(ClientGameTestContext context) {
		context.runOnClient(client -> module().setEnabled(false));
		context.waitTicks(2);
	}

	private static void mode(ClientGameTestContext context, TotemMode mode) {
		context.runOnClient(client -> module().settings()
				.<EnumSetting<TotemMode>>byId("mode")
				.orElseThrow(() -> new AssertionError("mod ayarı yok"))
				.set(mode));
	}

	private static void threshold(ClientGameTestContext context, int value) {
		context.runOnClient(client -> module().settings()
				.<IntSetting>byId("health_threshold")
				.orElseThrow(() -> new AssertionError("can eşiği ayarı yok"))
				.set(value));
	}

	private static void restore(ClientGameTestContext context, boolean on) {
		context.runOnClient(client -> module().settings()
				.<BoolSetting>byId("restore_offhand")
				.orElseThrow(() -> new AssertionError("geri koyma ayarı yok"))
				.set(on));
	}

	private static void fallback(ClientGameTestContext context, List<String> ids) {
		context.runOnClient(client -> module().settings()
				.<RegistryListSetting>byId("fallback")
				.orElseThrow(() -> new AssertionError("yedek eşya ayarı yok"))
				.set(ids));
	}

	private static void warnWhenOut(ClientGameTestContext context, boolean on) {
		context.runOnClient(client -> module().settings()
				.<BoolSetting>byId("warn_when_out")
				.orElseThrow(() -> new AssertionError("uyarı ayarı yok"))
				.set(on));
	}

	/**
	 * Starts recording the real chat, from the tick it is called.
	 *
	 * <p>Chat has no reading API, so this borrows the one hook that sees every line: the
	 * filter the chat asks about each message. It answers yes to everything and keeps what it
	 * was shown. The lines land in a set because the chat asks about the same message more
	 * than once — on every refresh — and {@link GuiMessage} is a record, so asking twice about
	 * one message counts once.
	 *
	 * <p>The chat itself is emptied first, and that is not tidiness: those refreshes re-offer
	 * every line still on screen, so a warning from an earlier scenario would be counted as
	 * this one's. It was, until the chat was cleared here.
	 */
	private static void watchChat(ClientGameTestContext context) {
		CHAT.clear();
		context.runOnClient(client -> {
			client.gui.hud.getChat().clearMessages(true);
			client.gui.hud.getChat().setVisibleMessageFilter(message -> {
				CHAT.add(message);
				return true;
			});
		});
	}

	/** How many of the recorded lines are AutoTotem's warning. */
	private static int warnings(ClientGameTestContext context) {
		String expected = context.computeOnClient(client -> Lang.of(Keys.TOTEM_OUT));

		return (int) CHAT.stream()
				.map(message -> message.content().getString())
				.filter(line -> line.equals(expected))
				.count();
	}

	private static Module module() {
		return SurvivalKit.modules().byId("autototem")
				.orElseThrow(() -> new AssertionError("autototem modülü kayıtlı değil"));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}
}
