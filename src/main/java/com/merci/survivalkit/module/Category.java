package com.merci.survivalkit.module;

import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;

/** Top level grouping a module appears under in the menu. */
public enum Category {
	COMBAT,
	MOVEMENT,
	PLAYER,
	RENDER,
	MISC;

	public String displayName() {
		return Lang.of(Keys.category(this));
	}
}
