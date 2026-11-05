package com.merci.survivalkit.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.TestNames;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The tooltip rule, now that the words live in the language files: a setting with nothing
 * translated behind its tooltip key is a setting that explains nothing on screen.
 */
class SettingsAuditTest {
	@BeforeEach
	void freshLanguage() {
		TestNames.install();
	}

	@Test
	void aTreeWhereEverythingExplainsItselfComesBackClean() {
		SettingGroup root = new SettingGroup("autoeat", "test.autoeat");
		root.add(TestNames.named(new BoolSetting("enabled", "test.enabled", false), "Açık", "Modül açık mı"));

		assertTrue(SettingsAudit.settingsWithoutTooltip(root).isEmpty());
	}

	@Test
	void theCountIsOfSettingsAtEveryDepthNotOfHeadings() {
		SettingGroup root = new SettingGroup("autotool", "test.autotool");
		root.add(new BoolSetting("enabled", "test.enabled", false));
		SettingGroup section = root.add(SettingGroup.section("which_tool", "test.which_tool"));
		section.add(new BoolSetting("keep_sword", "test.keep_sword", true));
		section.add(new IntSetting("durability_threshold", "test.durability_threshold", 10, 0, 100));
		SettingGroup nested = section.add(new SettingGroup("deeper", "test.deeper"));
		nested.add(new BoolSetting("only_with_tool", "test.only_with_tool", true));

		assertEquals(4, SettingsAudit.settingsChecked(root));
	}

	@Test
	void aSettingWithNoTranslatedTooltipIsNamedByItsFullPath() {
		SettingGroup root = new SettingGroup("autoeat", "test.autoeat");
		root.add(new BoolSetting("enabled", "test.enabled", false));

		assertEquals(List.of("autoeat.enabled"), SettingsAudit.settingsWithoutTooltip(root));
	}

	/** A line of spaces in the language file is no more of an explanation than nothing. */
	@Test
	void aTooltipOfNothingButSpacesDoesNotCount() {
		SettingGroup root = new SettingGroup("autoeat", "test.autoeat");
		root.add(new BoolSetting("enabled", "test.enabled", false));
		TestNames.put(Keys.tooltip("test.enabled"), "   ");

		assertEquals(List.of("autoeat.enabled"), SettingsAudit.settingsWithoutTooltip(root));
	}

	@Test
	void nestedGroupsAreWalkedToo() {
		SettingGroup root = new SettingGroup("wdl", "test.wdl");
		SettingGroup nested = root.add(new SettingGroup("seed", "test.seed"));
		nested.add(new IntSetting("value", "test.value", 0, 0, 10));

		assertEquals(List.of("wdl.seed.value"), SettingsAudit.settingsWithoutTooltip(root));
	}

	@Test
	void aGroupOfItsOwnNeedsNoTooltip() {
		SettingGroup root = new SettingGroup("wdl", "test.wdl");
		root.add(new SettingGroup("seed", "test.seed"));

		assertTrue(SettingsAudit.settingsWithoutTooltip(root).isEmpty());
	}

	@Test
	void everyOffenderIsReportedNotJustTheFirst() {
		SettingGroup root = new SettingGroup("wdl", "test.wdl");
		root.add(new BoolSetting("entities", "test.entities", true));
		SettingGroup nested = root.add(new SettingGroup("seed", "test.seed"));
		nested.add(new IntSetting("value", "test.value", 0, 0, 10));

		assertEquals(List.of("wdl.entities", "wdl.seed.value"), SettingsAudit.settingsWithoutTooltip(root));
	}
}
