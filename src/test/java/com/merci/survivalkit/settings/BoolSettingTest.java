package com.merci.survivalkit.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonPrimitive;
import com.merci.survivalkit.text.TestNames;
import org.junit.jupiter.api.Test;

/** The shared {@link Setting} behaviour is exercised through its simplest subclass. */
class BoolSettingTest {
	@Test
	void aFreshSettingHoldsItsDefault() {
		BoolSetting setting = new BoolSetting("wdl.entities", "test.wdl.entities", true);

		assertTrue(setting.get());
	}

	@Test
	void setReplacesTheValue() {
		BoolSetting setting = new BoolSetting("wdl.entities", "test.wdl.entities", true);

		setting.set(false);

		assertFalse(setting.get());
	}

	@Test
	void resetRestoresTheDefault() {
		BoolSetting setting = new BoolSetting("wdl.entities", "test.wdl.entities", true);
		setting.set(false);

		setting.reset();

		assertTrue(setting.get());
	}

	/** Nothing translated behind the key means nothing to read: the key shows through. */
	@Test
	void aSettingWithNoTranslationShowsItsKey() {
		TestNames.install();
		BoolSetting setting = new BoolSetting("wdl.entities", "test.wdl.entities", true);

		assertEquals("survivalkit.setting.test.wdl.entities.tooltip", setting.tooltip());
	}

	@Test
	void constructionRejectsABlankId() {
		assertThrows(IllegalArgumentException.class, () -> new BoolSetting("  ", "test.  ", true));
	}

	@Test
	void aSettingIsVisibleByDefault() {
		BoolSetting setting = new BoolSetting("wdl.entities", "test.wdl.entities", true);

		assertTrue(setting.isVisible());
	}

	@Test
	void visibleWhenDecidesVisibility() {
		BoolSetting parent = new BoolSetting("wdl.enabled", "test.wdl.enabled", false);
		BoolSetting child = new BoolSetting("wdl.entities", "test.wdl.entities", true);
		child.setVisibleWhen(parent::get);

		assertFalse(child.isVisible());
		parent.set(true);
		assertTrue(child.isVisible());
	}

	@Test
	void anInvisibleSettingStillKeepsItsValue() {
		BoolSetting setting = new BoolSetting("wdl.entities", "test.wdl.entities", true);
		setting.setVisibleWhen(() -> false);

		assertTrue(setting.get());
	}

	@Test
	void writeEmitsTheCurrentValue() {
		BoolSetting setting = new BoolSetting("wdl.entities", "test.wdl.entities", true);
		setting.set(false);

		assertEquals(new JsonPrimitive(false), setting.write());
	}

	@Test
	void readAcceptsAStoredValue() {
		BoolSetting setting = new BoolSetting("wdl.entities", "test.wdl.entities", true);

		setting.read(new JsonPrimitive(false));

		assertFalse(setting.get());
	}

	@Test
	void readFallsBackToTheDefaultOnAWrongType() {
		BoolSetting setting = new BoolSetting("wdl.entities", "test.wdl.entities", true);
		setting.set(false);

		setting.read(new JsonPrimitive("evet"));

		assertTrue(setting.get());
	}

	@Test
	void aSettingReadsItsNameAndTooltipFromTheLanguageInUse() {
		TestNames.install();
		BoolSetting setting = TestNames.named(
			new BoolSetting("wdl.entities", "test.wdl.entities", true), "Varlıklar", "Mobları da indir");

		assertEquals("Varlıklar", setting.displayName());
		assertEquals("Mobları da indir", setting.tooltip());
	}

	@Test
	void aHiddenSettingBecomesVisibleAgainWhenItsRuleFlips() {
		BoolSetting parent = new BoolSetting("wdl.enabled", "test.wdl.enabled", true);
		BoolSetting child = new BoolSetting("wdl.entities", "test.wdl.entities", true);
		child.setVisibleWhen(parent::get);

		parent.set(false);
		assertFalse(child.isVisible());

		parent.set(true);
		assertTrue(child.isVisible());
	}

	@Test
	void resetLeavesTheVisibilityRuleAlone() {
		BoolSetting setting = new BoolSetting("wdl.entities", "test.wdl.entities", true);
		setting.setVisibleWhen(() -> false);
		setting.set(false);

		setting.reset();

		assertTrue(setting.get());
		assertFalse(setting.isVisible());
	}
}
