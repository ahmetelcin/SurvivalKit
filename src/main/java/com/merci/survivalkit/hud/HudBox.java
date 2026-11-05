package com.merci.survivalkit.hud;

/** A rectangle on the screen in GUI units: left, top, width, height. */
public record HudBox(int x, int y, int width, int height) {
	public int right() {
		return x + width;
	}

	public int bottom() {
		return y + height;
	}

	public boolean contains(double px, double py) {
		return px >= x && px < right() && py >= y && py < bottom();
	}
}
