package com.merci.survivalkit.menu;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Whether the menu key should close the menu.
 *
 * <p>Right Shift both opens this menu and types capital letters, so the menu closes on the
 * key coming back up rather than going down, and not at all if anything was pressed in
 * between. A capital letter is exactly that: shift held, letter pressed, shift released.
 */
public final class MenuKeyRule {
	public static final int MENU_KEY = InputConstants.KEY_RSHIFT;

	/** True while the menu key is down and nothing else has been pressed since. */
	private boolean armed;

	public void pressed(int key) {
		armed = key == MENU_KEY;
	}

	/**
	 * True when this release should close the menu. A release whose press was never seen
	 * does nothing, which is what lets the key that opened the menu come back up in peace.
	 */
	public boolean released(int key) {
		if (key != MENU_KEY) {
			return false;
		}

		boolean closes = armed;
		armed = false;
		return closes;
	}
}
