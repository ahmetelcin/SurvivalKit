package com.merci.survivalkit.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonPrimitive;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnumSettingTest {
	enum Mode {
		OFF,
		CHUNKS_ONLY,
		EVERYTHING
	}

	private static EnumSetting<Mode> mode() {
		return new EnumSetting<>("wdl.mode", "test.wdl.mode", Mode.EVERYTHING);
	}

	@Test
	void writeUsesTheLowercaseConstantName() {
		EnumSetting<Mode> setting = mode();

		setting.set(Mode.CHUNKS_ONLY);

		assertEquals(new JsonPrimitive("chunks_only"), setting.write());
	}

	@Test
	void readAcceptsAStoredConstantName() {
		EnumSetting<Mode> setting = mode();

		setting.read(new JsonPrimitive("off"));

		assertEquals(Mode.OFF, setting.get());
	}

	@Test
	void readIsCaseInsensitive() {
		EnumSetting<Mode> setting = mode();

		setting.read(new JsonPrimitive("ChUnKs_OnLy"));

		assertEquals(Mode.CHUNKS_ONLY, setting.get());
	}

	@Test
	void readFallsBackToTheDefaultOnAnUnknownName() {
		EnumSetting<Mode> setting = mode();
		setting.set(Mode.OFF);

		setting.read(new JsonPrimitive("kaldirildi"));

		assertEquals(Mode.EVERYTHING, setting.get());
	}

	@Test
	void everyConstantIsOfferedToTheUi() {
		EnumSetting<Mode> setting = mode();

		assertEquals(List.of(Mode.OFF, Mode.CHUNKS_ONLY, Mode.EVERYTHING), setting.options());
	}

	@Test
	void cycleAdvancesToTheNextConstant() {
		EnumSetting<Mode> setting = mode();

		setting.set(Mode.OFF);
		setting.cycle();

		assertEquals(Mode.CHUNKS_ONLY, setting.get());
	}

	@Test
	void cycleWrapsAroundAtTheEnd() {
		EnumSetting<Mode> setting = mode();

		setting.set(Mode.EVERYTHING);
		setting.cycle();

		assertEquals(Mode.OFF, setting.get());
	}
}
