package com.merci.survivalkit.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.module.Category;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.TestNames;
import java.text.Collator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The menu's rules, tried without a game: ordering, selection, visibility, folding, search. */
class MenuModelTest {
	/** Every module carries an "enabled" and a "key" setting from the base class. */
	private static final int INHERITED_ROWS = 2;

	private static final class TestModule extends Module {
		private final BoolSetting master;
		private final BoolSetting detail;

		private TestModule(String id, String displayName, Category category) {
			super(id, category, KeybindSetting.UNBOUND);
			TestNames.module(id, displayName);
			this.master = settings().add(
				TestNames.named(new BoolSetting("master", "test.master", false), "Ana anahtar", "Alt ayarı açar"));
			this.detail = settings().add(
				TestNames.named(new BoolSetting("detail", "test.detail", false), "Ayrıntı", "Ana anahtar açıkken"));
			this.detail.setVisibleWhen(master::get);
		}
	}

	@BeforeEach
	void freshLanguage() {
		TestNames.install();
	}

	/** A heading with a name of its own, so a search can be aimed at it. */
	private static SettingGroup group(TestModule module, String id, String name) {
		SettingGroup nested = module.settings().add(new SettingGroup(id, "test." + id));
		TestNames.put(Keys.setting("test." + id), name);
		return nested;
	}

	private static BoolSetting bool(SettingGroup parent, String id, String name) {
		return parent.add(TestNames.named(new BoolSetting(id, "test." + id, true), name, name + " ayarı"));
	}

	private static TestModule module(String id, String displayName, Category category) {
		return new TestModule(id, displayName, category);
	}

	private static TestModule anyModule() {
		return module("autoeat", "Otomatik Yeme", Category.PLAYER);
	}

	// --- ordering ---

	@Test
	void modulesAreGroupedByCategoryInDeclarationOrder() {
		Module misc = module("wdl", "Dünya İndirici", Category.MISC);
		Module player = module("autoeat", "Otomatik Yeme", Category.PLAYER);
		MenuModel model = new MenuModel(List.of(misc, player));

		// PLAYER is declared before MISC, so it leads whatever order they were registered in.
		assertEquals(List.of(player, misc), model.modules());
	}

	@Test
	void modulesInOneCategoryAreAlphabetical() {
		Module zeta = module("z", "Zırh", Category.MISC);
		Module alpha = module("a", "Alet", Category.MISC);
		MenuModel model = new MenuModel(List.of(zeta, alpha));

		assertEquals(List.of(alpha, zeta), model.modules());
	}

	// --- selection ---

	@Test
	void theFirstModuleIsSelectedToBeginWith() {
		Module first = module("a", "Alet", Category.MISC);
		MenuModel model = new MenuModel(List.of(first, module("z", "Zırh", Category.MISC)));

		assertSame(first, model.selected().orElseThrow());
	}

	@Test
	void anEmptyMenuHasNothingSelected() {
		assertTrue(new MenuModel(List.of()).selected().isEmpty());
	}

	@Test
	void theSelectionIsRemembered() {
		Module second = module("z", "Zırh", Category.MISC);
		MenuModel model = new MenuModel(List.of(module("a", "Alet", Category.MISC), second));

		model.select(second);

		assertSame(second, model.selected().orElseThrow());
	}

	@Test
	void aModuleFromSomewhereElseCannotBeSelected() {
		Module mine = module("a", "Alet", Category.MISC);
		MenuModel model = new MenuModel(List.of(mine));

		model.select(module("other", "Başka", Category.MISC));

		assertSame(mine, model.selected().orElseThrow());
	}

	// --- which rows show ---

	@Test
	void theRowsAreTheSelectedModulesSettings() {
		MenuModel model = new MenuModel(List.of(anyModule()));

		assertEquals(List.of("enabled", "key", "master"), model.rows().stream().map(MenuRow::id).toList());
	}

	@Test
	void aHiddenSettingLeavesNoGapBehind() {
		TestModule module = anyModule();
		MenuModel model = new MenuModel(List.of(module));

		int before = model.rows().size();
		module.master.set(true);

		assertEquals(before + 1, model.rows().size());
		assertTrue(model.rows().stream().anyMatch(row -> row.id().equals("detail")));
	}

	@Test
	void everyRowOfAFlatModuleSitsAtDepthZero() {
		MenuModel model = new MenuModel(List.of(anyModule()));

		assertTrue(model.rows().stream().allMatch(row -> row.depth() == 0));
	}

	// --- folding ---

	@Test
	void aNestedGroupIsAHeadingWithItsChildrenIndented() {
		TestModule module = anyModule();
		SettingGroup nested = group(module, "seed", "Seed");
		bool(nested, "keep", "Koru");
		MenuModel model = new MenuModel(List.of(module));

		List<MenuRow> rows = model.rows();
		MenuRow heading = rows.get(rows.size() - 2);
		MenuRow child = rows.getLast();

		assertTrue(heading.isGroup());
		assertEquals(0, heading.depth());
		assertEquals("keep", child.id());
		assertEquals(1, child.depth());
	}

	@Test
	void foldingAHeadingHidesWhatIsUnderIt() {
		TestModule module = anyModule();
		SettingGroup nested = group(module, "seed", "Seed");
		bool(nested, "keep", "Koru");
		MenuModel model = new MenuModel(List.of(module));

		model.toggleCollapsed(nested);

		assertTrue(model.isCollapsed(nested));
		assertFalse(model.rows().stream().anyMatch(row -> row.id().equals("keep")));
		assertTrue(model.rows().stream().anyMatch(row -> row.id().equals("seed")), "başlık kalmalı");
	}

	@Test
	void aHeadingStartsOpen() {
		TestModule module = anyModule();
		SettingGroup nested = group(module, "seed", "Seed");
		bool(nested, "keep", "Koru");
		MenuModel model = new MenuModel(List.of(module));

		assertFalse(model.isCollapsed(nested));
		assertTrue(model.rows().stream().anyMatch(row -> row.id().equals("keep")));
	}

	/** Every module has a "hands" heading; folding one module's must leave the other's open. */
	@Test
	void headingsOfTheSameIdInTwoModulesFoldApart() {
		TestModule eat = module("autoeat", "Otomatik Yeme", Category.PLAYER);
		TestModule totem = module("autototem", "AutoTotem", Category.COMBAT);
		SettingGroup eatHands = group(eat, "hands", "El");
		SettingGroup totemHands = group(totem, "hands", "El");
		bool(eatHands, "scope", "Kapsam");
		bool(totemHands, "restore", "Geri ver");
		MenuModel model = new MenuModel(List.of(eat, totem));

		model.toggleCollapsed(eatHands);

		assertTrue(model.isCollapsed(eatHands));
		assertFalse(model.isCollapsed(totemHands));
	}

	@Test
	void aHeadingWithNothingVisibleUnderItIsNotDrawn() {
		TestModule module = anyModule();
		SettingGroup nested = group(module, "seed", "Seed");
		BoolSetting only = bool(nested, "keep", "Koru");
		only.setVisibleWhen(() -> false);
		MenuModel model = new MenuModel(List.of(module));

		assertFalse(model.rows().stream().anyMatch(row -> row.id().equals("seed")));
	}

	// --- search ---

	@Test
	void anEmptyQueryLeavesEverythingStanding() {
		MenuModel model = new MenuModel(List.of(anyModule()));
		int before = model.rows().size();

		model.setQuery("   ");

		assertEquals(before, model.rows().size());
	}

	@Test
	void aQueryNarrowsTheRowsToWhatMatches() {
		MenuModel model = new MenuModel(List.of(anyModule()));

		model.setQuery("ana");

		assertEquals(List.of("master"), model.rows().stream().map(MenuRow::id).toList());
	}

	@Test
	void aQueryMatchingTheModuleNameKeepsItInTheList() {
		MenuModel model = new MenuModel(List.of(anyModule()));

		model.setQuery("Otomatik");

		assertEquals(1, model.visibleModules().size());
	}

	@Test
	void aModuleWithNoMatchAnywhereDropsOutOfTheList() {
		MenuModel model = new MenuModel(List.of(anyModule()));

		model.setQuery("bulunmayanbirsey");

		assertEquals(List.of(), model.visibleModules());
	}

	@Test
	void aModuleStaysInTheListWhenOnlyOneOfItsSettingsMatches() {
		MenuModel model = new MenuModel(List.of(anyModule()));

		model.setQuery("Ana anahtar");

		assertEquals(1, model.visibleModules().size());
	}

	/** A match tucked under a folded heading still counts, or search would lie. */
	@Test
	void aMatchInsideAFoldedGroupStillKeepsTheModuleListed() {
		TestModule module = anyModule();
		SettingGroup nested = group(module, "seed", "Seed");
		bool(nested, "keep", "Koru");
		MenuModel model = new MenuModel(List.of(module));
		model.toggleCollapsed(nested);

		model.setQuery("Koru");

		assertEquals(1, model.visibleModules().size());
	}

	@Test
	void aHeadingThatMatchesBringsItsChildrenWithIt() {
		TestModule module = anyModule();
		SettingGroup nested = group(module, "seed", "Seed");
		bool(nested, "keep", "Koru");
		MenuModel model = new MenuModel(List.of(module));

		model.setQuery("Seed");

		assertEquals(List.of("seed", "keep"), model.rows().stream().map(MenuRow::id).toList());
	}

	@Test
	void searchIgnoresLetterCase() {
		MenuModel model = new MenuModel(List.of(anyModule()));

		model.setQuery("ANA ANAHTAR");

		assertEquals(List.of("master"), model.rows().stream().map(MenuRow::id).toList());
	}

	@Test
	void theInheritedSettingsAreThereToBeSearchedToo() {
		MenuModel model = new MenuModel(List.of(anyModule()));

		assertEquals(INHERITED_ROWS + 1, model.rows().size());
	}

	// --- Turkish spelling and ordering ---

	@Test
	void theTurkishSpellingOfAModuleIsFound() {
		MenuModel model = new MenuModel(List.of(module("wdl", "Dünya İndirici", Category.MISC)));

		model.setQuery("indirici");

		assertEquals(1, model.visibleModules().size());
	}

	@Test
	void allCapsFindsTheModuleToo() {
		MenuModel model = new MenuModel(List.of(module("wdl", "Dünya İndirici", Category.MISC)));

		model.setQuery("INDIRICI");

		assertEquals(1, model.visibleModules().size());
	}

	@Test
	void aKeyboardWithoutTurkishLettersFindsTheModule() {
		MenuModel model = new MenuModel(List.of(module("wdl", "Dünya İndirici", Category.MISC)));

		model.setQuery("dunya");

		assertEquals(1, model.visibleModules().size());
	}

	@Test
	void aKeyboardWithoutTurkishLettersFindsTheSettings() {
		TestModule module = anyModule();
		module.settings().add(TestNames.named(new BoolSetting("hunger", "test.hunger", false), "Açlık eşiği", "Ne zaman yesin"));
		module.settings().add(TestNames.named(new BoolSetting("health", "test.health", false), "Can eşiği", "Ne zaman elma çıkarsın"));
		MenuModel model = new MenuModel(List.of(module));

		model.setQuery("esik");

		assertEquals(List.of("hunger", "health"), model.rows().stream().map(MenuRow::id).toList());
	}

	/** Code-point order puts Ç after Z; a reader looks for it next to C. */
	@Test
	void turkishLettersSortWhereAReaderExpectsThem() {
		Module cedilla = module("c", "Çelik", Category.MISC);
		Module d = module("d", "Demir", Category.MISC);
		Module z = module("z", "Zırh", Category.MISC);
		MenuModel model = new MenuModel(List.of(z, d, cedilla), Collator.getInstance(Locale.of("tr", "TR")));

		assertEquals(List.of(cedilla, d, z), model.modules());
	}

	@Test
	void resortingKeepsTheSelection() {
		Module first = module("a", "Alet", Category.MISC);
		Module second = module("z", "Zırh", Category.MISC);
		MenuModel model = new MenuModel(List.of(first, second));
		model.select(second);

		model.resort(Collator.getInstance(Locale.of("tr", "TR")));

		assertSame(second, model.selected().orElseThrow());
	}

	// --- search results ---

	/** A search is answered with settings, not just with the modules holding them. */
	@Test
	void aSettingThatMatchesIsAResultAlongsideTheModuleItLivesIn() {
		TestModule module = anyModule();
		MenuModel model = new MenuModel(List.of(module));
		model.setQuery("anahtar");

		List<SearchHit> results = model.results();

		assertEquals(1, results.size());
		assertSame(module, results.getFirst().module());
		assertEquals("master", results.getFirst().setting().id());
	}

	@Test
	void thereAreNoResultsUntilSomethingIsSearchedFor() {
		MenuModel model = new MenuModel(List.of(anyModule()));

		assertTrue(model.results().isEmpty());
	}

	/** The module list already answers its own name; the results are for what is inside. */
	@Test
	void aModuleMatchedByItsOwnNameContributesNoSettingResults() {
		MenuModel model = new MenuModel(List.of(module("wdl", "Dünya İndirici", Category.MISC)));
		model.setQuery("dunya");

		assertTrue(model.results().isEmpty());
	}

	/** Jumping to something the panel would not draw is a dead end. */
	@Test
	void aSettingHiddenByItsOwnRuleIsNotAResult() {
		MenuModel model = new MenuModel(List.of(anyModule()));
		model.setQuery("ayrıntı");

		assertTrue(model.results().isEmpty());
	}

	@Test
	void resultsComeFromEveryModuleNotOnlyTheSelectedOne() {
		Module first = module("autoeat", "Otomatik Yeme", Category.PLAYER);
		Module second = module("wdl", "Dünya İndirici", Category.MISC);
		MenuModel model = new MenuModel(List.of(first, second));
		model.setQuery("anahtar");

		assertEquals(List.of(first, second), model.results().stream().map(SearchHit::module).toList());
	}

	/** A heading is nothing to jump to, but what it holds is. */
	@Test
	void aGroupHeadingIsNotAResultThoughWhatIsUnderItIs() {
		TestModule module = anyModule();
		SettingGroup nested = group(module, "seed", "Tohum");
		bool(nested, "keep", "Koru");
		MenuModel model = new MenuModel(List.of(module));
		model.setQuery("tohum");

		assertEquals(List.of("keep"), model.results().stream().map(hit -> hit.setting().id()).toList());
	}
}
