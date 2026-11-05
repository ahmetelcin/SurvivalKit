package com.merci.survivalkit.hud;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * For "show on the action bar too": what a module's HUD box says, said on the action bar as one
 * line — when it changes, and once a second besides, before the game fades it. Nothing to say
 * and it falls silent until there is.
 */
public final class ActionBarEcho {
	/** How often an unchanged line is said again: the game fades the action bar after three seconds. */
	public static final int REFRESH_TICKS = 20;
	static final String JOIN = " · ";

	private String said;
	private int since;

	/** One tick: the line to put on the action bar now, if any. */
	public Optional<HudLine> next(List<HudLine> lines) {
		if (lines.isEmpty()) {
			said = null;
			return Optional.empty();
		}

		String text = lines.stream().map(HudLine::text).collect(Collectors.joining(JOIN));

		if (text.equals(said) && ++since < REFRESH_TICKS) {
			return Optional.empty();
		}

		said = text;
		since = 0;
		return Optional.of(new HudLine(text, lines.stream().anyMatch(HudLine::warning)));
	}

	public void reset() {
		said = null;
		since = 0;
	}
}
