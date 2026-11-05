package com.merci.survivalkit.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.merci.survivalkit.module.Category;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.text.TestLang;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The modules that are on, by their names in the player's language. */
class ActiveModulesTest {
	private final Module autoEat = module("autoeat");
	private final Module scaffold = module("scaffold");
	private final Module downloader = module("wdl");
	private final Module totem = module("autototem");
	private final Module hud = module("hud");
	private final List<Module> all = List.of(autoEat, scaffold, downloader, totem, hud);

	private static Module module(String id) {
		return new Module(id, Category.MISC, KeybindSetting.UNBOUND) {
		};
	}

	@BeforeEach
	void english() {
		TestLang.install("en_us");
		autoEat.setEnabled(true);
		scaffold.setEnabled(true);
		downloader.setEnabled(true);
		hud.setEnabled(true);
	}

	@Test
	void onlyTheModulesThatAreOnAndNeverTheHudItself() {
		assertEquals(List.of("Auto Eat", "Scaffold", "World Downloader"), ActiveModules.names(all, ActiveSort.MENU, "hud"));
	}

	@Test
	void byName() {
		downloader.setEnabled(false);
		totem.setEnabled(true);

		assertEquals(List.of("Auto Eat", "Auto Totem", "Scaffold"), ActiveModules.names(all, ActiveSort.NAME, "hud"));
	}

	/** Longest first; the same length goes by name. */
	@Test
	void longestFirst() {
		assertEquals(List.of("World Downloader", "Auto Eat", "Scaffold"), ActiveModules.names(all, ActiveSort.LENGTH, "hud"));
	}

	@Test
	void switchedOffItLeavesTheList() {
		scaffold.setEnabled(false);

		assertEquals(List.of("Auto Eat", "World Downloader"), ActiveModules.names(all, ActiveSort.MENU, "hud"));
	}

	@Test
	void inTurkishTheNamesAreTurkish() {
		TestLang.install("tr_tr");

		assertEquals(List.of("Dünya İndirici", "Otomatik Yeme", "İskele"),
			ActiveModules.names(all, ActiveSort.LENGTH, "hud"));
	}
}
