package com.merci.survivalkit.hud;

/** One line of a HUD panel; a warning stands out. */
public record HudLine(String text, boolean warning) {
	public static HudLine of(String text) {
		return new HudLine(text, false);
	}
}
