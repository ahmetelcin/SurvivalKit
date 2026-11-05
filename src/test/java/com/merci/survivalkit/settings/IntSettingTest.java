package com.merci.survivalkit.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

class IntSettingTest {
	private static IntSetting flushInterval() {
		return new IntSetting("wdl.flush", "test.wdl.flush", 5, 1, 60);
	}

	@Test
	void setClampsBelowTheMinimum() {
		IntSetting setting = flushInterval();

		setting.set(0);

		assertEquals(1, setting.get());
	}

	@Test
	void setClampsAboveTheMaximum() {
		IntSetting setting = flushInterval();

		setting.set(999);

		assertEquals(60, setting.get());
	}

	@Test
	void setKeepsAValueInRange() {
		IntSetting setting = flushInterval();

		setting.set(30);

		assertEquals(30, setting.get());
	}

	@Test
	void constructionRejectsADefaultOutsideTheRange() {
		assertThrows(IllegalArgumentException.class, () -> new IntSetting("wdl.flush", "test.wdl.flush", 99, 1, 60));
	}

	@Test
	void constructionRejectsAnInvertedRange() {
		assertThrows(IllegalArgumentException.class, () -> new IntSetting("wdl.flush", "test.wdl.flush", 5, 60, 1));
	}

	@Test
	void readClampsAnOutOfRangeStoredValue() {
		IntSetting setting = flushInterval();

		setting.read(new JsonPrimitive(1000));

		assertEquals(60, setting.get());
	}

	@Test
	void readFallsBackToTheDefaultOnANonNumber() {
		IntSetting setting = flushInterval();
		setting.set(30);

		setting.read(new JsonPrimitive("çok"));

		assertEquals(5, setting.get());
	}

	@Test
	void minAndMaxAreExposedForTheUi() {
		IntSetting setting = flushInterval();

		assertEquals(1, setting.min());
		assertEquals(60, setting.max());
	}
}
