package com.merci.survivalkit.hud;

import java.util.List;
import java.util.function.Consumer;

/**
 * What a module has to say on the HUD, kept between ticks, and its echo on the action bar for
 * "show on the action bar too".
 */
public final class ModuleStatus {
	private final ActionBarEcho echo = new ActionBarEcho();
	private List<HudLine> lines = List.of();

	/**
	 * One tick's lines.
	 *
	 * @param alsoOnActionBar the module's "show on the action bar too"
	 * @param actionBar       where a line for the action bar goes
	 */
	public void update(List<HudLine> lines, boolean alsoOnActionBar, Consumer<HudLine> actionBar) {
		this.lines = List.copyOf(lines);

		if (alsoOnActionBar) {
			echo.next(this.lines).ifPresent(actionBar);
		} else {
			echo.reset();
		}
	}

	public List<HudLine> lines() {
		return lines;
	}

	/** The module went off: nothing to say until it is on again. */
	public void clear() {
		lines = List.of();
		echo.reset();
	}
}
