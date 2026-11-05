package com.merci.survivalkit.gametest;

import com.google.gson.JsonObject;
import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.menu.PresetStrip;
import com.merci.survivalkit.menu.SurvivalKitScreen;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.settings.Setting;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.mojang.blaze3d.platform.InputConstants;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.renderer.Rect2i;

/**
 * Presets from the strip at the top of a module's settings, with real clicks, typing and keys:
 * İskele's "Köprü" changes its three settings and nothing else of İskele's or of any other
 * module, and says so on the title's line; ↺ puts two of them back one at a time to what they
 * were before the preset — not to the default; the module's settings are saved as the player's
 * own, a name in the list is refused, the saved one brings its values back and its ✕ deletes it;
 * Oto Silah's and Otomatik Yeme's built-ins set what they say.
 */
public class PresetGameTest implements FabricClientGameTest {
	private static final InputConstants.Key MENU_KEY = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT);
	private static final String OWN = "benim yolum";
	/** Light enough to be the strip's own white text, not the panel or the world behind it. */
	private static final int LIT = 200;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(10);
			context.getInput().pressKey(MENU_KEY);
			context.waitTicks(5);

			try {
				theBridgeChangesItsOwnAndNothingElse(context);
				undoGoesBackToBeforeThePresetOneAtATime(context);
				theModulesOwnIsSavedAppliedAndDeleted(context);
				theOtherModulesBuiltIns(context);
			} catch (AssertionError e) {
				SurvivalKit.LOGGER.error("[gametest-hata] {}", e.getMessage());
				throw e;
			} finally {
				context.runOnClient(client -> {
					SurvivalKit.presets().store().delete("scaffold", OWN);
					client.gui.setScreen(null);
				});
				context.waitTicks(3);
			}

			SurvivalKit.LOGGER.info("[gametest] hazır ayarlar doğrulandı (Köprü yalnız İskele'nin üç ayarını değiştirdi, ↺ ikisini tek tek "
				+ "hazır ayardan önceki değere döndürdü, kendi hazır ayarı kaydedildi/uygulandı/silindi, alınmış ad reddedildi, Oto Silah ve "
				+ "Otomatik Yeme'nin yerleşikleri)");
		}
	}

	private static void theBridgeChangesItsOwnAndNothingElse(ClientGameTestContext context) {
		select(context, "scaffold");
		String bridge = Lang.of(Keys.preset("scaffold", "bridge"));
		Rect2i chip = chip(context, bridge);
		int lit = lit(context, "hazir-ayar-serit", chip, LIT, LIT, LIT);

		if (lit < 20) {
			throw new AssertionError("\"" + bridge + "\" düğmesi ekranda okunmalıydı, açık piksel " + lit);
		}

		context.runOnClient(client -> intSetting("scaffold", "width").set(3));
		Map<String, JsonObject> before = snapshot(context);

		clickAt(context, chip.getX() + chip.getWidth() / 2, chip.getY() + chip.getHeight() / 2);

		expect(context, "scaffold", "width", "1");
		expect(context, "scaffold", "y_lock", "\"start\"");
		expect(context, "scaffold", "safe_walk", "\"at_edge\"");
		expect(context, "scaffold", "ramp", "\"off\"");
		Map<String, JsonObject> after = snapshot(context);

		for (String id : before.keySet()) {
			JsonObject was = before.get(id).deepCopy();
			JsonObject now = after.get(id).deepCopy();

			if (id.equals("scaffold")) {
				for (String changed : List.of("width", "y_lock", "safe_walk")) {
					was.remove(changed);
					now.remove(changed);
				}
			}

			if (!was.equals(now)) {
				throw new AssertionError("Köprü yalnız İskele'nin üç ayarını değiştirmeliydi; " + id + " değişti:\n" + was + "\n" + now);
			}
		}

		if (context.computeOnClient(client -> module("scaffold").isEnabled())) {
			throw new AssertionError("hazır ayar modülü açmamalıydı");
		}

		String said = context.computeOnClient(client -> menu(client).presetStatus());
		String wanted = Lang.of(Keys.MENU_PRESET_APPLIED, bridge, 3);
		Rect2i statusBox = context.computeOnClient(client -> menu(client).presetStatusBox());
		int green = green(context, "hazir-ayar-uygulandi", statusBox);

		if (!wanted.equals(said) || green < 20) {
			throw new AssertionError("başlık satırında \"" + wanted + "\" yazmalıydı: \"" + said + "\", yeşil piksel " + green);
		}

		SurvivalKit.LOGGER.info("[gametest] Köprü: \"{}\", {} yeşil piksel; başka modülde ve İskele'nin başka ayarında değişiklik yok", said, green);
	}

	/** Width was 3 before the bridge: its ↺ gives back 3, not the default 1 — and only it. */
	private static void undoGoesBackToBeforeThePresetOneAtATime(ClientGameTestContext context) {
		bringIntoView(context, "y_lock");
		Rect2i glyph = context.computeOnClient(client -> menu(client).resetGlyph("y_lock").orElseThrow());
		int drawn = lit(context, "hazir-ayar-geri-al", glyph, 0x90, 0x90, 0x90);

		if (drawn < 10) {
			throw new AssertionError("Y kilidinin ↺ işareti çizilmeliydi, piksel " + drawn);
		}

		clickReset(context, "y_lock");
		expect(context, "scaffold", "y_lock", "\"off\"");
		expect(context, "scaffold", "safe_walk", "\"at_edge\"");
		expect(context, "scaffold", "width", "1");

		clickReset(context, "width");
		expect(context, "scaffold", "width", "3");
		expect(context, "scaffold", "safe_walk", "\"at_edge\"");
	}

	private static void theModulesOwnIsSavedAppliedAndDeleted(ClientGameTestContext context) {
		scrollToTop(context);
		click(context, onStrip(context, strip -> strip.saveButton()).orElseThrow());
		context.getInput().typeChars(Lang.of(Keys.preset("scaffold", "bridge")).toLowerCase(java.util.Locale.ROOT));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(3);
		String error = onStrip(context, strip -> strip.error());

		if (!Lang.of(Keys.MENU_PRESET_TAKEN).equals(error) || onStrip(context, strip -> strip.mode()) != PresetStrip.Mode.NAMING) {
			throw new AssertionError("listedeki ad reddedilmeliydi: \"" + error + "\", kip " + onStrip(context, strip -> strip.mode()));
		}

		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(3);

		if (onStrip(context, strip -> strip.mode()) != PresetStrip.Mode.IDLE || !(context.computeOnClient(client -> client.gui.screen()) instanceof SurvivalKitScreen)) {
			throw new AssertionError("Escape adı bırakmalı, menü açık kalmalıydı");
		}

		click(context, onStrip(context, strip -> strip.saveButton()).orElseThrow());
		context.getInput().typeChars(OWN);
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(3);
		Path file = context.computeOnClient(client -> SurvivalKit.presets().store().file());
		String saved = read(file);

		if (!onStrip(context, strip -> strip.names()).contains(OWN) || !saved.contains("\"" + OWN + "\"")) {
			throw new AssertionError("kaydedilen şeritte ve dosyada olmalıydı: " + onStrip(context, strip -> strip.names()) + "\n" + saved);
		}

		context.runOnClient(client -> intSetting("scaffold", "width").set(2));
		click(context, chip(context, OWN));
		expect(context, "scaffold", "width", "3");
		expect(context, "scaffold", "y_lock", "\"off\"");

		click(context, onStrip(context, strip -> strip.cross(OWN)).orElseThrow(() -> new AssertionError("kendi hazır ayarının ✕'i yok")));

		if (onStrip(context, strip -> strip.names()).contains(OWN) || read(file).contains("\"" + OWN + "\"")) {
			throw new AssertionError("✕ hazır ayarı silmeliydi: " + onStrip(context, strip -> strip.names()));
		}
	}

	private static void theOtherModulesBuiltIns(ClientGameTestContext context) {
		select(context, "autoweapon");
		click(context, chip(context, Lang.of(Keys.preset("autoweapon", "aim_only"))));
		expect(context, "autoweapon", "source", "\"aim_only\"");
		click(context, chip(context, Lang.of(Keys.preset("autoweapon", "crowd"))));
		expect(context, "autoweapon", "source", "\"aim_then_crowd\"");

		select(context, "autoeat");
		click(context, chip(context, Lang.of(Keys.preset("autoeat", "always_fed"))));
		expect(context, "autoeat", "threshold_mode", "\"fixed\"");
		expect(context, "autoeat", "threshold", "19");
		expect(context, "autoeat", "eat_early_when_hurt", "true");
		click(context, chip(context, Lang.of(Keys.preset("autoeat", "frugal"))));
		expect(context, "autoeat", "threshold_mode", "\"auto\"");
		expect(context, "autoeat", "fallback", "\"lowest_value_first\"");
	}

	// --- the menu, for real ---

	private static void select(ClientGameTestContext context, String id) {
		int y = context.computeOnClient(client -> menu(client).rowCenterY(module(id)))
			.orElseThrow(() -> new AssertionError("modül listede yok: " + id));
		clickAt(context, context.computeOnClient(client -> menu(client).nameCenterX()), y);
	}

	private static Rect2i chip(ClientGameTestContext context, String name) {
		return onStrip(context, strip -> strip.chip(name))
			.orElseThrow(() -> new AssertionError("şeritte yok: " + name + " (var: " + onStrip(context, strip -> strip.names()) + ")"));
	}

	private static void click(ClientGameTestContext context, Rect2i box) {
		clickAt(context, box.getX() + box.getWidth() / 2, box.getY() + box.getHeight() / 2);
	}

	private static void clickReset(ClientGameTestContext context, String settingId) {
		bringIntoView(context, settingId);
		Rect2i box = context.computeOnClient(client -> menu(client).resetBox(settingId).orElseThrow());
		click(context, box);
	}

	private static void clickAt(ClientGameTestContext context, int guiX, int guiY) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos(guiX * scale, guiY * scale);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static void bringIntoView(ClientGameTestContext context, String settingId) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		int cursorX = context.computeOnClient(client -> menu(client).controlToggleCenterX());

		for (int attempt = 0; attempt < 60; attempt++) {
			int y = context.computeOnClient(client -> menu(client).settingCenterY(settingId))
				.orElseThrow(() -> new AssertionError("ayar ekranda değil: " + settingId));
			int top = context.computeOnClient(client -> menu(client).settingsViewTop());
			int bottom = context.computeOnClient(client -> menu(client).settingsViewBottom());

			if (y - 11 >= top && y + 11 <= bottom) {
				return;
			}

			context.getInput().setCursorPos(cursorX * scale, (top + bottom) / 2.0 * scale);
			context.getInput().scroll(y > bottom - 11 ? -1 : 1);
			context.waitTick();
		}

		throw new AssertionError("ayar görünür alana kaydırılamadı: " + settingId);
	}

	/** The strip is the first thing in the panel: wheeled all the way up. */
	private static void scrollToTop(ClientGameTestContext context) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		int cursorX = context.computeOnClient(client -> menu(client).controlToggleCenterX());
		int top = context.computeOnClient(client -> menu(client).settingsViewTop());

		for (int attempt = 0; attempt < 60 && context.computeOnClient(client -> menu(client).scrollOffset()) > 0; attempt++) {
			context.getInput().setCursorPos(cursorX * scale, (top + 20) * scale);
			context.getInput().scroll(1);
			context.waitTick();
		}

		context.waitTicks(2);
	}

	private static void expect(ClientGameTestContext context, String moduleId, String settingId, String wanted) {
		String value = context.computeOnClient(client -> module(moduleId).settings().<Setting<?>>byId(settingId).orElseThrow().write().toString());

		if (!wanted.equals(value)) {
			throw new AssertionError(moduleId + "." + settingId + " " + wanted + " olmalıydı: " + value);
		}
	}

	private static Map<String, JsonObject> snapshot(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			Map<String, JsonObject> all = new HashMap<>();
			SurvivalKit.modules().modules().forEach(module -> all.put(module.id(), module.settings().write().getAsJsonObject()));
			return all;
		});
	}

	// --- pixels ---

	private static int lit(ClientGameTestContext context, String name, Rect2i box, int r, int g, int b) {
		return count(context, name, box, rgb -> ((rgb >> 16) & 0xFF) >= r && ((rgb >> 8) & 0xFF) >= g && (rgb & 0xFF) >= b);
	}

	private static int green(ClientGameTestContext context, String name, Rect2i box) {
		return count(context, name, box, rgb -> ((rgb >> 8) & 0xFF) > ((rgb >> 16) & 0xFF) + 40 && ((rgb >> 8) & 0xFF) > (rgb & 0xFF) + 40);
	}

	private static int count(ClientGameTestContext context, String name, Rect2i box, java.util.function.IntPredicate wanted) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		Path shot = context.takeScreenshot(name);
		BufferedImage image;

		try {
			image = ImageIO.read(shot.toFile());
		} catch (IOException unreadable) {
			throw new AssertionError("ekran görüntüsü okunamadı: " + shot, unreadable);
		}

		int found = 0;

		for (int y = (int) (box.getY() * scale); y < Math.min(image.getHeight(), (int) ((box.getY() + box.getHeight()) * scale)); y++) {
			for (int x = (int) (box.getX() * scale); x < Math.min(image.getWidth(), (int) ((box.getX() + box.getWidth()) * scale)); x++) {
				if (wanted.test(image.getRGB(x, y))) {
					found++;
				}
			}
		}

		return found;
	}

	// --- plumbing ---

	private static String read(Path file) {
		try {
			return Files.isRegularFile(file) ? Files.readString(file) : "";
		} catch (IOException e) {
			throw new AssertionError("okunamadı: " + file, e);
		}
	}

	private static SurvivalKitScreen menu(net.minecraft.client.Minecraft client) {
		if (!(client.gui.screen() instanceof SurvivalKitScreen open)) {
			throw new AssertionError("menü açık değil, açık olan: " + client.gui.screen());
		}

		return open;
	}

	private static <T> T onStrip(ClientGameTestContext context, java.util.function.Function<PresetStrip, T> read) {
		return context.computeOnClient(client -> read.apply(menu(client).presetStrip()));
	}

	private static IntSetting intSetting(String moduleId, String id) {
		return module(moduleId).settings().<IntSetting>byId(id).orElseThrow();
	}

	private static Module module(String id) {
		return SurvivalKit.modules().byId(id).orElseThrow(() -> new AssertionError("modül yok: " + id));
	}
}
