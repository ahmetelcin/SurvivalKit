package com.merci.survivalkit.text;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Where every word the menu shows comes from.
 *
 * <p>The mod holds translation keys and nothing else; the text behind them is in the language
 * files and follows whatever language the game is set to. The lookup is installed once at
 * startup rather than reached for directly, so the settings tree stays free of Minecraft and
 * can be tried in a plain unit test with the real language file loaded into a map.
 *
 * <p>Nothing is installed by default and a key with no text behind it comes back as itself.
 * That is deliberate: a missing translation shows up as {@code survivalkit.something} on
 * screen, which is ugly enough to notice and exactly what the tests look for.
 */
public final class Lang {
	private static UnaryOperator<String> lookup = key -> key;

	private Lang() {
	}

	/** Installs the lookup. The client passes vanilla's; tests pass a map. */
	public static void use(UnaryOperator<String> lookup) {
		Lang.lookup = Objects.requireNonNull(lookup, "lookup");
	}

	public static String of(String key) {
		if (key == null) {
			return "";
		}

		String text = lookup.apply(key);
		return text == null ? key : text;
	}

	/** Fills {@code %s} in the text behind the key, in order. Plain text in, plain text out. */
	public static String of(String key, Object... arguments) {
		StringBuilder filled = new StringBuilder(of(key));

		for (Object argument : arguments) {
			int at = filled.indexOf("%s");

			if (at < 0) {
				break;
			}

			filled.replace(at, at + 2, String.valueOf(argument));
		}

		return filled.toString();
	}

	/** True when nothing but the key itself came back — an untranslated key. */
	public static boolean isRaw(String text) {
		return text != null && text.startsWith("survivalkit.");
	}
}
