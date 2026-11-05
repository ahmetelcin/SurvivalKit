package com.merci.survivalkit.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.TestNames;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * There is one rule left here: a key is named by the game, in the game's language and on the
 * player's own keyboard layout. Only "no key at all" is answered locally.
 */
class KeyNamesTest {
	/** Stands in for the game's namer, and records what it was asked. */
	private final List<String> asked = new ArrayList<>();
	private final UnaryOperator<String> layout = keyName -> {
		asked.add(keyName);
		return "<oyun:" + keyName + ">";
	};

	@BeforeEach
	void language() {
		TestNames.install();
		TestNames.put(Keys.MENU_UNBOUND, "Yok");
	}

	private String readable(String keyName) {
		return KeyNames.readable(keyName, layout);
	}

	@Test
	void lettersAreNamedByTheGame() {
		assertEquals("<oyun:key.keyboard.k>", readable("key.keyboard.k"));
		assertEquals(List.of("key.keyboard.k"), asked);
	}

	/** These used to be named from a Turkish table of our own. The game knows them better. */
	@Test
	void specialKeysAreNamedByTheGameToo() {
		readable("key.keyboard.right.shift");
		readable("key.keyboard.space");
		readable("key.keyboard.f3");
		readable("key.keyboard.keypad.5");

		assertEquals(
			List.of("key.keyboard.right.shift", "key.keyboard.space", "key.keyboard.f3", "key.keyboard.keypad.5"),
			asked);
	}

	@Test
	void mouseButtonsAreNamedByTheGame() {
		assertEquals("<oyun:key.mouse.4>", readable("key.mouse.4"));
		assertEquals("<oyun:key.mouse.left>", readable("key.mouse.left"));
	}

	/**
	 * The one thing the game has no word for, because it is not a key: the setting is empty.
	 * It comes from the language files like the rest of the menu.
	 */
	@Test
	void anUnboundSettingSaysSoInTheMenusOwnWords() {
		assertEquals("Yok", readable(KeybindSetting.UNBOUND));
		assertEquals("Yok", readable(""));
		assertEquals("Yok", readable(null));
		assertTrue(asked.isEmpty(), "atanmamış tuş için oyuna sorulmamalı");
	}
}
