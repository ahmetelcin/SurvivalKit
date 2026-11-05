package com.merci.survivalkit.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.module.impl.AutoEatModule;
import com.merci.survivalkit.module.impl.AutoTotemModule;
import com.merci.survivalkit.module.impl.AutoToolModule;
import com.merci.survivalkit.module.impl.AutoWeaponModule;
import com.merci.survivalkit.module.impl.ScaffoldModule;
import com.merci.survivalkit.module.impl.HudModule;
import com.merci.survivalkit.module.impl.BuildModeModule;
import com.merci.survivalkit.module.impl.ToolPickerModule;
import com.merci.survivalkit.module.impl.VeinMinerModule;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The language files, checked against what the mod actually asks for.
 *
 * <p>This is the whole of the "every word is translated" rule. It walks the real modules, so
 * a setting added next year is covered without anyone remembering to come back here: it
 * simply fails the build until both files have a line for it.
 */
class LanguageFilesTest {
	private static final List<String> LANGUAGES = List.of("en_us", "tr_tr");

	/** Built fresh: these carry the keys, and nothing here depends on a running game. */
	private static List<Module> modules() {
		return List.of(new AutoEatModule(), new AutoTotemModule(), new AutoToolModule(), new ToolPickerModule(new AutoToolModule()), new AutoWeaponModule(), new BuildModeModule(), new VeinMinerModule(), new ScaffoldModule(), new WorldDownloaderModule(), new HudModule());
	}

	@Test
	void everyKeyTheMenuAsksForIsTranslatedInEveryLanguage() {
		Set<String> needed = Keys.needed(modules());

		for (String language : LANGUAGES) {
			Map<String, String> entries = TestLang.load(language);
			Set<String> missing = new TreeSet<>(needed);
			missing.removeAll(entries.keySet());

			assertTrue(missing.isEmpty(), language + " dilinde eksik çeviri: " + missing);
		}
	}

	@Test
	void theTwoLanguagesCarryTheSameKeys() {
		Set<String> english = new TreeSet<>(TestLang.load("en_us").keySet());
		Set<String> turkish = new TreeSet<>(TestLang.load("tr_tr").keySet());

		Set<String> onlyEnglish = new TreeSet<>(english);
		onlyEnglish.removeAll(turkish);
		Set<String> onlyTurkish = new TreeSet<>(turkish);
		onlyTurkish.removeAll(english);

		assertEquals(Set.of(), onlyEnglish, "yalnızca en_us'ta olan anahtarlar");
		assertEquals(Set.of(), onlyTurkish, "yalnızca tr_tr'de olan anahtarlar");
	}

	/** A key with nothing behind it reads as the key itself on screen, which is no translation. */
	@Test
	void noTranslationIsLeftBlank() {
		for (String language : LANGUAGES) {
			TestLang.load(language).forEach((key, text) ->
				assertFalse(text.isBlank(), language + " dilinde boş çeviri: " + key));
		}
	}

	/** What the modules are called has to read as words once a language is installed. */
	@Test
	void moduleNamesReadAsWordsOnceALanguageIsInstalled() {
		TestLang.install("en_us");

		for (Module module : modules()) {
			assertFalse(Lang.isRaw(module.displayName()), "çevrilmemiş modül adı: " + module.id());
			assertFalse(Lang.isRaw(module.description()), "çevrilmemiş modül açıklaması: " + module.id());
		}
	}
}
