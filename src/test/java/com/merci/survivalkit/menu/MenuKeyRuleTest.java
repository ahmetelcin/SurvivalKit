package com.merci.survivalkit.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MenuKeyRuleTest {
	private static final int LETTER_A = 65;

	private final MenuKeyRule rule = new MenuKeyRule();

	@Test
	void tappingTheMenuKeyClosesTheMenu() {
		rule.pressed(MenuKeyRule.MENU_KEY);

		assertTrue(rule.released(MenuKeyRule.MENU_KEY));
	}

	/**
	 * The press that opened the menu happened before this screen existed, so only its
	 * release arrives here. Closing on it would make the menu impossible to open.
	 */
	@Test
	void aReleaseWithoutItsPressDoesNotClose() {
		assertFalse(rule.released(MenuKeyRule.MENU_KEY));
	}

	/** Right Shift is also the capital-letter key: shift, letter, release must type, not close. */
	@Test
	void aKeyPressedWhileTheMenuKeyIsHeldCancelsTheClose() {
		rule.pressed(MenuKeyRule.MENU_KEY);
		rule.pressed(LETTER_A);

		assertFalse(rule.released(MenuKeyRule.MENU_KEY));
	}

	@Test
	void aCancelledHoldDoesNotLingerIntoTheNextTap() {
		rule.pressed(MenuKeyRule.MENU_KEY);
		rule.pressed(LETTER_A);
		rule.released(MenuKeyRule.MENU_KEY);

		rule.pressed(MenuKeyRule.MENU_KEY);

		assertTrue(rule.released(MenuKeyRule.MENU_KEY));
	}

	@Test
	void releasingAnyOtherKeyDoesNotClose() {
		rule.pressed(MenuKeyRule.MENU_KEY);

		assertFalse(rule.released(LETTER_A));
	}

	/** Letting go of the letter first still leaves a hold that typed something. */
	@Test
	void aLetterReleasedBeforeTheMenuKeyStillCancels() {
		rule.pressed(MenuKeyRule.MENU_KEY);
		rule.pressed(LETTER_A);
		rule.released(LETTER_A);

		assertFalse(rule.released(MenuKeyRule.MENU_KEY));
	}

	@Test
	void pressingSomethingElseAloneNeverCloses() {
		rule.pressed(LETTER_A);

		assertFalse(rule.released(LETTER_A));
	}
}
