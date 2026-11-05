package com.merci.survivalkit.profile;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/** What a profile may be called: it is a file name too, so it has to be one on every system. */
public final class ProfileNames {
	public static final int MAX_LENGTH = 32;
	/** Characters no file name may hold on Windows, where the stricter rule is. */
	private static final String FORBIDDEN = "\\/:*?\"<>|";

	/** Why a name will not do. */
	public enum Problem {
		BLANK,
		TOO_LONG,
		BAD_CHARACTER,
		TAKEN
	}

	private ProfileNames() {
	}

	/**
	 * @param taken every profile there is; a name differing only in case is taken too, since on
	 *              Windows it is the same file
	 * @param own   the profile being renamed, which may keep its own name; null otherwise
	 */
	public static Optional<Problem> check(String name, Collection<String> taken, String own) {
		String trimmed = name.strip();

		if (trimmed.isEmpty() || trimmed.equals(".") || trimmed.equals("..")) {
			return Optional.of(Problem.BLANK);
		}

		if (trimmed.length() > MAX_LENGTH) {
			return Optional.of(Problem.TOO_LONG);
		}

		if (trimmed.chars().anyMatch(c -> c < 0x20 || FORBIDDEN.indexOf(c) >= 0) || trimmed.endsWith(".")) {
			return Optional.of(Problem.BAD_CHARACTER);
		}

		String folded = trimmed.toLowerCase(Locale.ROOT);
		boolean clash = taken.stream()
			.filter(existing -> own == null || !existing.equalsIgnoreCase(own))
			.anyMatch(existing -> existing.toLowerCase(Locale.ROOT).equals(folded));
		return clash ? Optional.of(Problem.TAKEN) : Optional.empty();
	}
}
