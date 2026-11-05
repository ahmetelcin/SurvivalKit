package com.merci.survivalkit.hud;

/** Which corner or edge of the screen a HUD panel is measured from. */
public enum Anchor {
	TOP_LEFT(-1, true),
	TOP_CENTER(0, true),
	TOP_RIGHT(1, true),
	BOTTOM_LEFT(-1, false),
	BOTTOM_CENTER(0, false),
	BOTTOM_RIGHT(1, false);

	private final int horizontal;
	private final boolean top;

	Anchor(int horizontal, boolean top) {
		this.horizontal = horizontal;
		this.top = top;
	}

	/** -1 measured from the left edge, 0 from the middle, 1 from the right edge. */
	public int horizontal() {
		return horizontal;
	}

	public boolean top() {
		return top;
	}

	public static Anchor of(int horizontal, boolean top) {
		for (Anchor anchor : values()) {
			if (anchor.horizontal == Integer.signum(horizontal) && anchor.top == top) {
				return anchor;
			}
		}

		throw new IllegalArgumentException("köşe yok: " + horizontal + ", " + top);
	}
}
