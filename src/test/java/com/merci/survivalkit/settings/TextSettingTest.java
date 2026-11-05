package com.merci.survivalkit.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

/** Backs free-text options such as the world downloader's manual seed. */
class TextSettingTest {
	private static TextSetting seed() {
		return new TextSetting("wdl.seed", "test.wdl.seed", "", 64);
	}

	@Test
	void aFreshSettingHoldsItsDefault() {
		TextSetting setting = seed();

		assertEquals("", setting.get());
	}

	@Test
	void setTruncatesBeyondTheMaximumLength() {
		TextSetting setting = new TextSetting("wdl.name", "test.wdl.name", "", 4);

		setting.set("çokuzunbirad");

		assertEquals("çoku", setting.get());
	}

	@Test
	void setTrimsSurroundingWhitespace() {
		TextSetting setting = seed();

		setting.set("  -4172144997902289642  ");

		assertEquals("-4172144997902289642", setting.get());
	}

	@Test
	void isBlankReportsAnUnsetValue() {
		TextSetting setting = seed();

		assertTrue(setting.isBlank());
	}

	@Test
	void readFallsBackToTheDefaultOnANonString() {
		TextSetting setting = seed();
		setting.set("dolu");

		setting.read(new JsonPrimitive(42));

		assertEquals("", setting.get());
	}

	@Test
	void writeEmitsTheCurrentValue() {
		TextSetting setting = seed();
		setting.set("merhaba");

		assertEquals(new JsonPrimitive("merhaba"), setting.write());
	}
}
