package com.merci.survivalkit.gametest;

import com.google.gson.JsonPrimitive;
import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.KeybindSetting;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.server.level.ServerPlayer;

/**
 * Drives a real client in a throwaway world to prove the module core works end to end:
 * the module's own keybind setting toggles it, an enabled module is ticked, and an open
 * screen swallows the key.
 *
 * <p>AutoEat stands in for "some module": a hunger bar that climbs is the observable
 * proof that client ticks actually reach an enabled module.
 */
public class ModuleCoreGameTest implements FabricClientGameTest {
	private static final int HUNGRY = 10;
	private static final int TICKS_TO_EAT = 120;
	// J is free in vanilla; O is the friends list and L is advancements, so neither can be used here.
	private static final String REBOUND_KEY = "key.keyboard.j";
	// Keys are sampled once a tick now, so a press has to be held long enough to be seen.
	private static final int PRESS_TICKS = 2;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(5);

			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("clear @a");
			world.getServer().runCommand("give @a minecraft:bread 64");
			starve(world);
			context.waitTicks(5);

			// The key is not a constant any more: it is whatever the module's setting holds.
			String toggleKey = context.computeOnClient(client -> SurvivalKit.modules()
					.byId("autoeat")
					.orElseThrow(() -> new AssertionError("autoeat modülü kayıtlı değil"))
					.keybind()
					.get());

			assertEnabled(context, false, "modül başlangıçta kapalı olmalı");

			// 1. the module's own bound key turns it on
			press(context, toggleKey);
			assertEnabled(context, true, "bağlı tuşa basınca modül açılmalı");

			// 2. an enabled module is driven by the client tick: it eats without further input
			world.getServer().waitFor(server -> hunger(server) > HUNGRY, TICKS_TO_EAT);

			// 3. the same key turns it back off
			press(context, toggleKey);
			assertEnabled(context, false, "tekrar basınca modül kapanmalı");

			// 4. with a screen open the key must not reach any module
			context.setScreen(() -> new PauseScreen(true));
			context.waitForScreen(PauseScreen.class);
			press(context, toggleKey);
			context.waitTicks(5);
			assertEnabled(context, false, "ekran açıkken tuş modülü açmamalı");

			context.runOnClient(client -> client.gui.setScreen(null));
			context.waitTicks(5);
			assertEnabled(context, false, "ekran kapanınca bekleyen tuş modülü açmamalı");

			// 5. a key still held down as the screen closes must not leak a toggle either
			context.setScreen(() -> new PauseScreen(true));
			context.waitForScreen(PauseScreen.class);
			context.getInput().holdKey(InputConstants.getKey(toggleKey));
			context.waitTicks(2);
			context.runOnClient(client -> client.gui.setScreen(null));
			context.waitTicks(10);
			assertEnabled(context, false, "ekran kapanırken basılı tutulan tuş modülü açmamalı");

			context.getInput().releaseKey(InputConstants.getKey(toggleKey));
			context.waitTicks(5);
			assertEnabled(context, false, "basılı tuş bırakılınca da modül açılmamalı");

			// 6. the key still works normally afterwards, so nothing above jammed it
			press(context, toggleKey);
			assertEnabled(context, true, "bunlardan sonra tuş hâlâ çalışmalı");

			press(context, toggleKey);
			assertEnabled(context, false, "modül tekrar kapanmalı");

			// 7. a disabled module is not ticked: hunger has to stay where we put it
			starve(world);
			context.waitTicks(40);

			int idleHunger = world.getServer().computeOnServer(ModuleCoreGameTest::hunger);

			if (idleHunger != HUNGRY) {
				throw new AssertionError("kapalı modül tick almamalı, açlık " + HUNGRY + " -> " + idleHunger);
			}

			// 8. the module that ships unbound really is unbound
			boolean downloaderBound = context.computeOnClient(client -> SurvivalKit.modules()
					.byId("wdl")
					.orElseThrow(() -> new AssertionError("wdl modülü kayıtlı değil"))
					.keybind()
					.isBound());

			if (downloaderBound) {
				throw new AssertionError("WDL varsayılanı atanmamış olmalıydı");
			}

			// 9. the setting drives the key: change it and the game has to follow, old key dead
			context.runOnClient(client -> keybind(client).set(REBOUND_KEY));
			context.waitTicks(3);

			press(context, REBOUND_KEY);
			assertEnabled(context, true, "ayara yazılan yeni tuş modülü açmalı");

			press(context, toggleKey);
			assertEnabled(context, true, "eski tuş artık hiçbir şey yapmamalı");

			press(context, REBOUND_KEY);
			assertEnabled(context, false, "yeni tuş modülü kapatmalı");

			// Leave the key as we found it, so a rerun starts from the same place.
			context.runOnClient(client -> keybind(client).set(toggleKey));

			// 10. an old config holding a raw GLFW code migrates through the game's own key table
			String migrated = context.computeOnClient(client -> {
				KeybindSetting probe = new KeybindSetting("probe", "test.probe", KeybindSetting.UNBOUND);
				probe.read(new JsonPrimitive(75)); // GLFW's K
				return probe.get();
			});

			if (!"key.keyboard.k".equals(migrated)) {
				throw new AssertionError("eski tuş kodu 75 çevrilmeliydi, bulunan " + migrated);
			}

			String migratedUnbound = context.computeOnClient(client -> {
				KeybindSetting probe = new KeybindSetting("probe", "test.probe", "key.keyboard.k");
				probe.read(new JsonPrimitive(-1));
				return probe.get();
			});

			if (!KeybindSetting.UNBOUND.equals(migratedUnbound)) {
				throw new AssertionError("eski -1 atanmamışa çevrilmeliydi, bulunan " + migratedUnbound);
			}

			SurvivalKit.LOGGER.info("[gametest] modül çekirdeği doğrulandı (tuş {}, tick, ekran koruması, 75 -> {})",
					toggleKey, migrated);
		}
	}

	private static KeybindSetting keybind(net.minecraft.client.Minecraft client) {
		return SurvivalKit.modules()
				.byId("autoeat")
				.orElseThrow(() -> new AssertionError("autoeat modülü kayıtlı değil"))
				.keybind();
	}

	/** Holds the key long enough for the once-a-tick sampling to see the edge. */
	private static void press(ClientGameTestContext context, String keyName) {
		context.getInput().holdKeyFor(InputConstants.getKey(keyName), PRESS_TICKS);
		context.waitTicks(2);
	}

	private static Module module(ClientGameTestContext context) {
		return context.computeOnClient(client -> SurvivalKit.modules()
				.byId("autoeat")
				.orElseThrow(() -> new AssertionError("autoeat modülü kayıtlı değil")));
	}

	private static void assertEnabled(ClientGameTestContext context, boolean expected, String why) {
		boolean actual = module(context).isEnabled();

		if (actual != expected) {
			throw new AssertionError(why + " (beklenen " + expected + ", bulunan " + actual + ")");
		}
	}

	private static void starve(TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> player(server).getFoodData().setFoodLevel(HUNGRY));
	}

	private static int hunger(net.minecraft.server.MinecraftServer server) {
		return player(server).getFoodData().getFoodLevel();
	}

	private static ServerPlayer player(net.minecraft.server.MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}
}
