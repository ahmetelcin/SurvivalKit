package com.merci.survivalkit.hud;

import java.util.List;

/** One box of lines on the HUD. What it says comes from {@link #lines}; where it goes, from {@link #placement}. */
public interface HudPanel {
	/** Stable, for the settings' ids and the tests. */
	String id();

	HudPlacement placement();

	/** What it says right now; empty and it is not drawn at all. */
	List<HudLine> lines();
}
