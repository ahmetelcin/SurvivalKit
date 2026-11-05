package com.merci.survivalkit.text;

import com.merci.survivalkit.settings.Setting;
import java.util.HashMap;
import java.util.Map;

/**
 * Words for tests that are about wording — sorting, searching, tooltips.
 *
 * <p>The mod itself only holds translation keys, so a test that wants a setting to be called
 * "Açlık eşiği" says so here and gets a language with exactly that in it. Real language files
 * are the business of {@link LanguageFilesTest}; this is for made-up modules.
 */
public final class TestNames {
	private static final Map<String, String> TEXT = new HashMap<>();

	private TestNames() {
	}

	/** Starts an empty language and installs it. Call from a test's setup. */
	public static void install() {
		TEXT.clear();
		Lang.use(key -> TEXT.getOrDefault(key, key));
	}

	/** Names a setting and its tooltip, handing the setting back so it can be added inline. */
	public static <T extends Setting<?>> T named(T setting, String name, String tooltip) {
		TEXT.put(Keys.setting(setting.key()), name);
		TEXT.put(Keys.tooltip(setting.key()), tooltip);
		return setting;
	}

	/** Names a module and gives it a description, which every module must have. */
	public static void module(String id, String name) {
		TEXT.put(Keys.module(id), name);
		TEXT.put(Keys.moduleDescription(id), name + " ne yapar");
	}

	public static void put(String key, String text) {
		TEXT.put(key, text);
	}
}
