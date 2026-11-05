package com.merci.survivalkit.client;

import com.merci.survivalkit.settings.KeybindSetting;

/**
 * Names the GLFW key codes that earlier versions of this mod wrote into a config. Minecraft 26.3
 * numbers its keys by scancode, so a GLFW code cannot be looked up in the game's table any more:
 * it is spelled out here instead. Letters, digits, function keys and the usual modifiers are
 * covered; any other code is unbound, the same as a code nobody can press.
 */
public final class LegacyGlfwKeys {
	private static final int GLFW_SPACE = 32;
	private static final int GLFW_0 = 48;
	private static final int GLFW_9 = 57;
	private static final int GLFW_A = 65;
	private static final int GLFW_Z = 90;
	private static final int GLFW_GRAVE_ACCENT = 96;
	private static final int GLFW_ESCAPE = 256;
	private static final int GLFW_ENTER = 257;
	private static final int GLFW_TAB = 258;
	private static final int GLFW_BACKSPACE = 259;
	private static final int GLFW_F1 = 290;
	private static final int GLFW_F12 = 301;
	private static final int GLFW_LEFT_SHIFT = 340;
	private static final int GLFW_LEFT_CONTROL = 341;
	private static final int GLFW_LEFT_ALT = 342;
	private static final int GLFW_RIGHT_SHIFT = 344;
	private static final int GLFW_RIGHT_CONTROL = 345;
	private static final int GLFW_RIGHT_ALT = 346;

	private LegacyGlfwKeys() {
	}

	/** The name Minecraft gives a key that GLFW numbered {@code code}, or unbound if there is none. */
	public static String name(int code) {
		if (code >= GLFW_A && code <= GLFW_Z) {
			return "key.keyboard." + (char) (code + ('a' - 'A'));
		}

		if (code >= GLFW_0 && code <= GLFW_9) {
			return "key.keyboard." + (char) code;
		}

		if (code >= GLFW_F1 && code <= GLFW_F12) {
			return "key.keyboard.f" + (code - GLFW_F1 + 1);
		}

		return switch (code) {
			case GLFW_SPACE -> "key.keyboard.space";
			case GLFW_GRAVE_ACCENT -> "key.keyboard.grave.accent";
			case GLFW_ESCAPE -> "key.keyboard.escape";
			case GLFW_ENTER -> "key.keyboard.enter";
			case GLFW_TAB -> "key.keyboard.tab";
			case GLFW_BACKSPACE -> "key.keyboard.backspace";
			case GLFW_LEFT_SHIFT -> "key.keyboard.left.shift";
			case GLFW_LEFT_CONTROL -> "key.keyboard.left.control";
			case GLFW_LEFT_ALT -> "key.keyboard.left.alt";
			case GLFW_RIGHT_SHIFT -> "key.keyboard.right.shift";
			case GLFW_RIGHT_CONTROL -> "key.keyboard.right.control";
			case GLFW_RIGHT_ALT -> "key.keyboard.right.alt";
			default -> KeybindSetting.UNBOUND;
		};
	}
}
