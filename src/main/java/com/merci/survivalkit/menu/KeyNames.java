package com.merci.survivalkit.menu;

import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.function.UnaryOperator;

/**
 * Turns the name a key is stored under into something worth reading on screen.
 *
 * <p>Settings hold vanilla's own spelling — "key.keyboard.right.shift" — because that is what
 * survives a save, a reload and a Minecraft version. Nobody wants to see it.
 *
 * <p>There used to be a table of Turkish names here. There isn't any more: the game already
 * names every key in the language it is set to, and on top of that it asks the keyboard
 * layout, so the key between P and Enter reads Ğ on a Turkish keyboard and [ on an English
 * one. Both of those are better answers than a table of ours could give, and they are the
 * same names the game's own Controls screen shows, which is where the player learned them.
 */
public final class KeyNames {
	private KeyNames() {
	}

	/**
	 * @param layoutName how the game names a key, given its stored name. Everything but "no
	 *                   key at all" is left to it.
	 */
	public static String readable(String keyName, UnaryOperator<String> layoutName) {
		if (keyName == null || keyName.isBlank() || KeybindSetting.UNBOUND.equals(keyName)) {
			return unbound();
		}

		return layoutName.apply(keyName);
	}

	/** What a keybind with no key shows. */
	public static String unbound() {
		return Lang.of(Keys.MENU_UNBOUND);
	}
}
