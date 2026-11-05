package com.merci.survivalkit.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.merci.survivalkit.settings.KeybindSetting;
import org.junit.jupiter.api.Test;

class LegacyGlfwKeysTest {
	@Test
	void lettersAndDigitsKeepTheirCharacter() {
		assertEquals("key.keyboard.k", LegacyGlfwKeys.name(75));
		assertEquals("key.keyboard.a", LegacyGlfwKeys.name(65));
		assertEquals("key.keyboard.z", LegacyGlfwKeys.name(90));
		assertEquals("key.keyboard.0", LegacyGlfwKeys.name(48));
		assertEquals("key.keyboard.9", LegacyGlfwKeys.name(57));
	}

	@Test
	void functionKeysAreNumberedFromOne() {
		assertEquals("key.keyboard.f1", LegacyGlfwKeys.name(290));
		assertEquals("key.keyboard.f12", LegacyGlfwKeys.name(301));
	}

	@Test
	void modifiersAndTheMenuKeyAreNamed() {
		assertEquals("key.keyboard.right.shift", LegacyGlfwKeys.name(344));
		assertEquals("key.keyboard.left.control", LegacyGlfwKeys.name(341));
		assertEquals("key.keyboard.escape", LegacyGlfwKeys.name(256));
	}

	@Test
	void anUnknownCodeIsUnbound() {
		assertEquals(KeybindSetting.UNBOUND, LegacyGlfwKeys.name(1));
		assertEquals(KeybindSetting.UNBOUND, LegacyGlfwKeys.name(999));
	}
}
