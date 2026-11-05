package com.merci.survivalkit.wdl;

/** Reads a hand-typed seed the way the world creation box does. */
public final class Seed {
	/** What gets written when the player never supplied one. */
	public static final long NONE = 0L;

	private Seed() {
	}

	/**
	 * The seed to write when continuing a world. One learnt this time is the newer word; not
	 * knowing it this time is no reason to wipe the one an earlier download found.
	 */
	public static long keep(long fresh, java.util.OptionalLong earlier) {
		return fresh != NONE ? fresh : earlier.orElse(NONE);
	}

	public static long parse(String raw) {
		String trimmed = raw.trim();

		if (trimmed.isEmpty()) {
			return NONE;
		}

		try {
			return Long.parseLong(trimmed);
		} catch (NumberFormatException notANumber) {
			return trimmed.hashCode();
		}
	}
}
