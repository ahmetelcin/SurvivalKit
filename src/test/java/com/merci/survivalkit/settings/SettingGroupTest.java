package com.merci.survivalkit.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.TestNames;
import org.junit.jupiter.api.Test;

class SettingGroupTest {
	@Test
	void childrenAreListedInAdditionOrder() {
		SettingGroup group = new SettingGroup("wdl", "test.wdl");
		BoolSetting first = group.add(new BoolSetting("entities", "test.entities", true));
		IntSetting second = group.add(new IntSetting("flush", "test.flush", 5, 1, 60));

		assertEquals(List.of(first, second), group.children());
	}

	@Test
	void addReturnsTheSettingSoItCanBeKept() {
		SettingGroup group = new SettingGroup("wdl", "test.wdl");
		BoolSetting added = new BoolSetting("entities", "test.entities", true);

		assertEquals(added, group.add(added));
	}

	@Test
	void addRejectsADuplicateId() {
		SettingGroup group = new SettingGroup("wdl", "test.wdl");
		group.add(new BoolSetting("entities", "test.entities", true));

		assertThrows(IllegalArgumentException.class, () -> group.add(new BoolSetting("entities", "test.entities", false)));
	}

	@Test
	void groupsNestInsideGroups() {
		SettingGroup root = new SettingGroup("wdl", "test.wdl");
		SettingGroup nested = root.add(new SettingGroup("seed", "test.seed"));

		assertEquals(List.of(nested), root.children());
	}

	@Test
	void resetCascadesToEveryDescendant() {
		SettingGroup root = new SettingGroup("wdl", "test.wdl");
		BoolSetting direct = root.add(new BoolSetting("entities", "test.entities", true));
		SettingGroup nested = root.add(new SettingGroup("seed", "test.seed"));
		TextSetting deep = nested.add(new TextSetting("value", "test.value", "", 64));
		direct.set(false);
		deep.set("123");

		root.reset();

		assertTrue(direct.get());
		assertEquals("", deep.get());
	}

	@Test
	void writeNestsChildrenUnderTheirIds() {
		SettingGroup root = new SettingGroup("wdl", "test.wdl");
		root.add(new BoolSetting("entities", "test.entities", true)).set(false);
		SettingGroup nested = root.add(new SettingGroup("seed", "test.seed"));
		nested.add(new TextSetting("value", "test.value", "", 64)).set("123");

		JsonObject expectedNested = new JsonObject();
		expectedNested.add("value", new JsonPrimitive("123"));
		JsonObject expected = new JsonObject();
		expected.add("entities", new JsonPrimitive(false));
		expected.add("seed", expectedNested);

		assertEquals(expected, root.write());
	}

	@Test
	void readAppliesStoredChildValues() {
		SettingGroup root = new SettingGroup("wdl", "test.wdl");
		BoolSetting entities = root.add(new BoolSetting("entities", "test.entities", true));
		JsonObject stored = new JsonObject();
		stored.add("entities", new JsonPrimitive(false));

		root.read(stored);

		assertFalse(entities.get());
	}

	@Test
	void readLeavesAMissingChildAtItsCurrentValue() {
		SettingGroup root = new SettingGroup("wdl", "test.wdl");
		BoolSetting entities = root.add(new BoolSetting("entities", "test.entities", true));
		entities.set(false);

		root.read(new JsonObject());

		assertFalse(entities.get());
	}

	@Test
	void readIgnoresAnUnknownKey() {
		SettingGroup root = new SettingGroup("wdl", "test.wdl");
		BoolSetting entities = root.add(new BoolSetting("entities", "test.entities", true));
		JsonObject stored = new JsonObject();
		stored.add("kaldirilmis", new JsonPrimitive(1));
		stored.add("entities", new JsonPrimitive(false));

		root.read(stored);

		assertFalse(entities.get());
	}

	@Test
	void readFallsBackToDefaultsOnANonObject() {
		SettingGroup root = new SettingGroup("wdl", "test.wdl");
		BoolSetting entities = root.add(new BoolSetting("entities", "test.entities", true));
		entities.set(false);

		root.read(new JsonPrimitive("bozuk"));

		assertTrue(entities.get());
	}

	@Test
	void visibleChildrenSkipTheHiddenOnes() {
		SettingGroup root = new SettingGroup("wdl", "test.wdl");
		BoolSetting shown = root.add(new BoolSetting("entities", "test.entities", true));
		BoolSetting hidden = root.add(new BoolSetting("flush", "test.flush", true));
		hidden.setVisibleWhen(() -> false);

		assertEquals(List.of(shown), root.visibleChildren());
	}

	@Test
	void byIdFindsADirectChild() {
		SettingGroup root = new SettingGroup("wdl", "test.wdl");
		BoolSetting entities = root.add(new BoolSetting("entities", "test.entities", true));

		assertEquals(entities, root.byId("entities").orElseThrow());
	}

	@Test
	void byIdIsEmptyForAnUnknownId() {
		SettingGroup root = new SettingGroup("wdl", "test.wdl");

		assertTrue(root.byId("yok").isEmpty());
	}

	@Test
	void aGroupBuiltWithoutATooltipHasAnEmptyOne() {
		SettingGroup group = new SettingGroup("wdl", "test.wdl");

		assertEquals("", group.tooltip());
	}

	/** A heading says what it is by its name; the audit asks nothing more of it. */
	@Test
	void aGroupNeverCarriesATooltip() {
		TestNames.install();
		SettingGroup group = new SettingGroup("wdl", "test.wdl");
		TestNames.put(Keys.setting("test.wdl"), "İndirici");

		assertEquals("İndirici", group.displayName());
		assertEquals("", group.tooltip());
	}

	@Test
	void aGroupIsVisibleByDefault() {
		SettingGroup group = new SettingGroup("wdl", "test.wdl");

		assertTrue(group.isVisible());
	}

	@Test
	void visibleWhenDecidesAGroupsVisibility() {
		BoolSetting master = new BoolSetting("wdl.enabled", "test.wdl.enabled", false);
		SettingGroup group = new SettingGroup("seed", "test.seed");
		group.setVisibleWhen(master::get);

		assertFalse(group.isVisible());
		master.set(true);
		assertTrue(group.isVisible());
	}

	@Test
	void aHiddenGroupIsSkippedByItsParent() {
		SettingGroup root = new SettingGroup("wdl", "test.wdl");
		BoolSetting shown = root.add(new BoolSetting("entities", "test.entities", true));
		SettingGroup hidden = root.add(new SettingGroup("seed", "test.seed"));
		hidden.setVisibleWhen(() -> false);

		assertEquals(List.of(shown), root.visibleChildren());
	}

	@Test
	void aHiddenGroupStillWritesAndReadsItsChildren() {
		SettingGroup root = new SettingGroup("wdl", "test.wdl");
		SettingGroup hidden = root.add(new SettingGroup("seed", "test.seed"));
		TextSetting value = hidden.add(new TextSetting("value", "test.value", "", 64));
		hidden.setVisibleWhen(() -> false);
		value.set("123");

		SettingGroup reloadedRoot = new SettingGroup("wdl", "test.wdl");
		SettingGroup reloadedHidden = reloadedRoot.add(new SettingGroup("seed", "test.seed"));
		TextSetting reloadedValue = reloadedHidden.add(new TextSetting("value", "test.value", "", 64));
		reloadedHidden.setVisibleWhen(() -> false);
		reloadedRoot.read(root.write());

		assertEquals("123", reloadedValue.get());
	}

	// --- sections: headings that are not a level in the file ---

	@Test
	void aSectionWritesItsSettingsAsIfItWereNotThere() {
		SettingGroup root = new SettingGroup("autoeat");
		root.add(new BoolSetting("enabled", "test.enabled", false));
		SettingGroup trigger = root.add(SettingGroup.section("trigger", "test.trigger"));
		trigger.add(new IntSetting("threshold", "test.threshold", 17, 1, 19)).set(12);

		JsonObject written = root.write().getAsJsonObject();

		assertEquals(12, written.get("threshold").getAsInt());
		assertFalse(written.has("trigger"), "bölüm dosyada bir kat olmamalı");
	}

	@Test
	void aSectionReadsItsSettingsFromTheLevelItSitsIn() {
		SettingGroup root = new SettingGroup("autoeat");
		SettingGroup trigger = root.add(SettingGroup.section("trigger", "test.trigger"));
		IntSetting threshold = trigger.add(new IntSetting("threshold", "test.threshold", 17, 1, 19));
		JsonObject file = new JsonObject();
		file.add("threshold", new JsonPrimitive(12));

		root.read(file);

		assertEquals(12, threshold.get());
	}

	@Test
	void aSettingInsideASectionIsFoundFromTheLevelAbove() {
		SettingGroup root = new SettingGroup("autoeat");
		SettingGroup trigger = root.add(SettingGroup.section("trigger", "test.trigger"));
		IntSetting threshold = trigger.add(new IntSetting("threshold", "test.threshold", 17, 1, 19));

		assertEquals(threshold, root.<IntSetting>byId("threshold").orElseThrow());
	}

	/** Two sections share one level of the file, so an id may only be used once across them. */
	@Test
	void anIdAlreadyUsedBesideASectionIsRejectedInsideIt() {
		SettingGroup root = new SettingGroup("autoeat");
		root.add(new BoolSetting("scope", "test.scope", false));
		SettingGroup hands = root.add(SettingGroup.section("hands", "test.hands"));

		assertThrows(IllegalArgumentException.class, () -> hands.add(new BoolSetting("scope", "test.scope", true)));
	}

	@Test
	void anIdUsedInOneSectionIsRejectedInAnother() {
		SettingGroup root = new SettingGroup("autoeat");
		root.add(SettingGroup.section("trigger", "test.trigger")).add(new BoolSetting("mode", "test.mode", false));
		SettingGroup hands = root.add(SettingGroup.section("hands", "test.hands"));

		assertThrows(IllegalArgumentException.class, () -> hands.add(new BoolSetting("mode", "test.mode", true)));
	}

	@Test
	void aSectionMustHaveAHeading() {
		assertThrows(NullPointerException.class, () -> SettingGroup.section("trigger", null));
	}
}
