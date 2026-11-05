package com.merci.survivalkit.util;

import java.util.Locale;

/**
 * Folds text so a search matches what a Turkish speaker actually types.
 *
 * <p>Two things go wrong without this. Someone hunting for "Dünya İndirici" types "dunya" or
 * "indirici" on a keyboard they cannot be bothered to switch, and a plain comparison finds
 * nothing. And {@code toLowerCase()} with the default locale is a trap in both directions: on
 * a Turkish system "I" lowercases to "ı" rather than "i", so "INDIRICI" stops matching
 * "indirici"; and "İ" lowercases to "i" plus a combining dot, which is two characters where
 * the text has one.
 *
 * <p>So every side of every comparison comes through here first: the Turkish letters are
 * folded to their plain counterparts by hand, and only then is the rest lowercased with a
 * fixed locale. Folding is deliberately one-way and lossy — it is for matching, never for
 * display.
 */
public final class SearchText {
	private SearchText() {
	}

	/** Lowercase, Turkish letters flattened, locale-independent. */
	public static String fold(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}

		StringBuilder folded = new StringBuilder(text.length());

		for (int index = 0; index < text.length(); index++) {
			folded.append(foldChar(text.charAt(index)));
		}

		// Locale.ROOT, never the default: a Turkish default would turn "I" into "ı" here and
		// undo the folding just done above.
		return folded.toString().toLowerCase(Locale.ROOT);
	}

	private static char foldChar(char letter) {
		return switch (letter) {
			case 'ç', 'Ç' -> 'c';
			case 'ğ', 'Ğ' -> 'g';
			case 'ı', 'İ', 'I' -> 'i';
			case 'ö', 'Ö' -> 'o';
			case 'ş', 'Ş' -> 's';
			case 'ü', 'Ü' -> 'u';
			default -> letter;
		};
	}

	/**
	 * True when the needle appears in the haystack once both are folded.
	 *
	 * <p>With one extra allowance for Turkish. A final k softens to ğ when a vowel-initial
	 * suffix follows, so the setting called "Açlık eşiği" holds the word "eşik" in a form
	 * that no longer contains it. Someone searching types the root, not the inflected form,
	 * so a needle ending in k is tried a second time with that softening applied: "esik"
	 * also looks for "esig", which "esigi" does contain.
	 *
	 * <p>Only the needle's last letter is treated this way, and only after a plain match has
	 * already failed, so an ordinary word still matches itself first.
	 */
	public static boolean contains(String haystack, String needle) {
		String folded = fold(haystack);
		String wanted = fold(needle);

		if (folded.contains(wanted)) {
			return true;
		}

		return wanted.endsWith("k") && folded.contains(wanted.substring(0, wanted.length() - 1) + "g");
	}
}
