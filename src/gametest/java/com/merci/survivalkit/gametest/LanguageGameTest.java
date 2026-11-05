package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.menu.SurvivalKitScreen;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.settings.Setting;
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.settings.SettingNode;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.locale.Language;

/**
 * The menu in a language it was not written in.
 *
 * <p>The rest of the suite runs in Turkish, which is exactly why this one runs in English:
 * a key nobody translated reads back as "survivalkit.something" rather than as a word, and
 * that is the one thing that cannot be seen from a Turkish screenshot. Everything the menu
 * would draw is walked here — module names, descriptions, every setting and its tooltip,
 * every enum value, and the menu's own chrome — and none of it may come back raw.
 */
public class LanguageGameTest implements FabricClientGameTest {
	private static final InputConstants.Key MENU_KEY =
		InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT);
	private static final String ENGLISH = "en_us";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestLanguage.ensure(context, ENGLISH);
			context.waitTicks(5);

			openMenu(context);
			nothingOnScreenIsARawKey(context);
			everyKeyThisBuildNeedsHasEnglishBehindIt(context);
			context.takeScreenshot("menu-english");

			closeMenu(context);

			// The rest of the suite reads Turkish; leave the client as it was found.
			TestLanguage.ensureTurkish(context);
			openMenu(context);
			nothingOnScreenIsARawKey(context);
			context.takeScreenshot("menu-turkce");
			closeMenu(context);

			SurvivalKit.LOGGER.info("[gametest] menü iki dilde de sözcüklerle okundu (ham anahtar yok)");
		}
	}

	/** Everything the menu puts on screen, asked for the way the menu asks for it. */
	private static void nothingOnScreenIsARawKey(ClientGameTestContext context) {
		List<String> shown = context.computeOnClient(client -> {
			List<String> text = new ArrayList<>();

			for (String key : Keys.needed(SurvivalKit.modules().modules())) {
				text.add(Lang.of(key));
			}

			for (Module module : SurvivalKit.modules().modules()) {
				text.add(module.displayName());
				text.add(module.description());
				text.add(module.category().displayName());
				collect(module.settings(), text);
			}

			text.add(SurvivalKitScreen.missingMark());
			return text;
		});

		List<String> raw = shown.stream().filter(Lang::isRaw).toList();

		if (!raw.isEmpty()) {
			throw new AssertionError("ekranda çevrilmemiş anahtar kaldı: " + raw);
		}

		List<String> blank = shown.stream().filter(String::isBlank).toList();

		if (!blank.isEmpty()) {
			throw new AssertionError("ekranda boş metin var, " + blank.size() + " tane");
		}
	}

	private static void collect(SettingGroup group, List<String> text) {
		for (SettingNode child : group.children()) {
			if (child instanceof SettingGroup nested) {
				collect(nested, text);
				continue;
			}

			text.add(child.displayName());
			text.add(child.tooltip());

			if (child instanceof EnumSetting<?> choice) {
				Setting<?> setting = (Setting<?>) child;
				choice.options().forEach(option -> text.add(Lang.of(Keys.option(setting.key(), option))));
			}
		}
	}

	/** The same list again, but through the game's own lookup rather than the mod's. */
	private static void everyKeyThisBuildNeedsHasEnglishBehindIt(ClientGameTestContext context) {
		List<String> missing = context.computeOnClient(client ->
			Keys.needed(SurvivalKit.modules().modules()).stream()
				.filter(key -> !Language.getInstance().has(key))
				.toList());

		if (!missing.isEmpty()) {
			throw new AssertionError(ENGLISH + " dilinde karşılığı olmayan anahtar: " + missing);
		}
	}

	private static void openMenu(ClientGameTestContext context) {
		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);

		if (context.computeOnClient(client -> !(client.gui.screen() instanceof SurvivalKitScreen))) {
			throw new AssertionError("menü açılmadı");
		}
	}

	private static void closeMenu(ClientGameTestContext context) {
		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);
	}
}
