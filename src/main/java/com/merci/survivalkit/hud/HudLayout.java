package com.merci.survivalkit.hud;

/**
 * Where a panel goes, from its anchor and its offsets: the offsets are measured inward from the
 * anchored edges — from the left or right edge, or sideways from the middle — and a panel is
 * always kept whole on the screen.
 */
public final class HudLayout {
	private HudLayout() {
	}

	/**
	 * @param width  the panel's width on screen, scale included
	 * @param height the same for its height
	 */
	public static HudBox place(Anchor anchor, int offsetX, int offsetY, int width, int height, int screenWidth, int screenHeight) {
		int x = switch (anchor.horizontal()) {
			case -1 -> offsetX;
			case 0 -> (screenWidth - width) / 2 + offsetX;
			default -> screenWidth - width - offsetX;
		};
		int y = anchor.top() ? offsetY : screenHeight - height - offsetY;
		return new HudBox(clamp(x, screenWidth - width), clamp(y, screenHeight - height), width, height);
	}

	private static int clamp(int value, int max) {
		return Math.max(0, Math.min(value, Math.max(0, max)));
	}
}
