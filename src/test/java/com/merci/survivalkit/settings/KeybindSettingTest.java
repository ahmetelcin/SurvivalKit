package com.merci.survivalkit.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonPrimitive;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Stores a key by Minecraft's own name so the core stays free of Minecraft types. */
class KeybindSettingTest {
	private static final String KEY_J = "key.keyboard.j";
	private static final String MOUSE_4 = "key.mouse.4";

	/** Stands in for the client's real code-to-name translator. */
	private static final Map<Integer, String> LEGACY = Map.of(74, KEY_J, -1, KeybindSetting.UNBOUND);

	@AfterEach
	void forgetTheTranslator() {
		KeybindSetting.useLegacyKeyNames(code -> KeybindSetting.UNBOUND);
	}

	@Test
	void anUnboundDefaultReportsNoKey() {
		KeybindSetting setting = new KeybindSetting("key", "test.key", KeybindSetting.UNBOUND);

		assertFalse(setting.isBound());
	}

	@Test
	void aBoundSettingReportsItsKey() {
		KeybindSetting setting = new KeybindSetting("key", "test.key", KEY_J);

		assertTrue(setting.isBound());
		assertEquals(KEY_J, setting.get());
	}

	@Test
	void aMouseButtonIsAKeyLikeAnyOther() {
		KeybindSetting setting = new KeybindSetting("key", "test.key", KeybindSetting.UNBOUND);

		setting.set(MOUSE_4);

		assertTrue(setting.isBound());
		assertEquals(MOUSE_4, setting.get());
	}

	@Test
	void clearUnbindsTheKey() {
		KeybindSetting setting = new KeybindSetting("key", "test.key", KEY_J);

		setting.clear();

		assertFalse(setting.isBound());
	}

	@Test
	void aNameThatIsNotAKeyIsTreatedAsUnbound() {
		KeybindSetting setting = new KeybindSetting("key", "test.key", KEY_J);

		setting.set("J");

		assertFalse(setting.isBound());
	}

	@Test
	void readFallsBackToTheDefaultOnSomethingThatIsNeitherNameNorCode() {
		KeybindSetting setting = new KeybindSetting("key", "test.key", KEY_J);
		setting.clear();

		setting.read(new JsonPrimitive(true));

		assertEquals(KEY_J, setting.get());
	}

	@Test
	void readTakesAKeyName() {
		KeybindSetting setting = new KeybindSetting("key", "test.key", KeybindSetting.UNBOUND);

		setting.read(new JsonPrimitive(KEY_J));

		assertEquals(KEY_J, setting.get());
	}

	@Test
	void anOldFileStillHoldingARawKeyCodeIsConverted() {
		KeybindSetting.useLegacyKeyNames(code -> LEGACY.getOrDefault(code, KeybindSetting.UNBOUND));
		KeybindSetting setting = new KeybindSetting("key", "test.key", KeybindSetting.UNBOUND);

		setting.read(new JsonPrimitive(74));

		assertEquals(KEY_J, setting.get());
	}

	@Test
	void anOldFilesUnboundCodeStaysUnbound() {
		KeybindSetting.useLegacyKeyNames(code -> LEGACY.getOrDefault(code, KeybindSetting.UNBOUND));
		KeybindSetting setting = new KeybindSetting("key", "test.key", KEY_J);

		setting.read(new JsonPrimitive(-1));

		assertFalse(setting.isBound());
	}

	@Test
	void aConvertedKeyIsWrittenBackAsAName() {
		KeybindSetting.useLegacyKeyNames(code -> LEGACY.getOrDefault(code, KeybindSetting.UNBOUND));
		KeybindSetting setting = new KeybindSetting("key", "test.key", KeybindSetting.UNBOUND);

		setting.read(new JsonPrimitive(74));

		assertEquals(new JsonPrimitive(KEY_J), setting.write());
	}

	@Test
	void writeEmitsTheCurrentKeyName() {
		KeybindSetting setting = new KeybindSetting("key", "test.key", KEY_J);

		assertEquals(new JsonPrimitive(KEY_J), setting.write());
	}
}
