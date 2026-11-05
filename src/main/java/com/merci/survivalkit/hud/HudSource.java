package com.merci.survivalkit.hud;

import java.util.List;

/** A module with something to say on the HUD: its own box, placed by the HUD's settings. */
public interface HudSource {
	/** The module's id, which the box's settings are named after. */
	String id();

	/** What it says right now; nothing — typically while the module is off — and the box is not drawn. */
	List<HudLine> hudLines();
}
