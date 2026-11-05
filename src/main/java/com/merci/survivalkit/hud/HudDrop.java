package com.merci.survivalkit.hud;

/**
 * Where the HUD editor puts a box the player drags: snapped to an edge — or the middle — once it
 * is within {@link #SNAP} of it, and kept, when dropped, from the corner or edge nearest it, so
 * it keeps its place whatever size the window becomes.
 */
public final class HudDrop {
	/** How near an edge, in GUI units, a box has to come to stick to it. */
	public static final int SNAP = 6;

	/** A box's settings after a drop. */
	public record Placed(Anchor anchor, int offsetX, int offsetY) {
	}

	private HudDrop() {
	}

	/** Where a box being dragged to (x, y) is shown: on the screen, and stuck to an edge it is near. */
	public static HudBox snap(int x, int y, int width, int height, int screenWidth, int screenHeight) {
		int maxX = Math.max(0, screenWidth - width);
		int maxY = Math.max(0, screenHeight - height);
		int sx = Math.clamp(x, 0, maxX);
		int sy = Math.clamp(y, 0, maxY);
		int centred = maxX / 2;

		if (sx < SNAP) {
			sx = 0;
		} else if (maxX - sx < SNAP) {
			sx = maxX;
		} else if (Math.abs(sx - centred) < SNAP) {
			sx = centred;
		}

		if (sy < SNAP) {
			sy = 0;
		} else if (maxY - sy < SNAP) {
			sy = maxY;
		}

		return new HudBox(sx, sy, width, height);
	}

	/**
	 * The settings for a box dropped at (x, y): snapped as while dragging, and measured from the
	 * side of the screen its middle is in — the left, middle or right third, the top or bottom half.
	 */
	public static Placed drop(int x, int y, int width, int height, int screenWidth, int screenHeight) {
		HudBox box = snap(x, y, width, height, screenWidth, screenHeight);
		double middleX = box.x() + width / 2.0;
		int horizontal = middleX < screenWidth / 3.0 ? -1 : middleX > screenWidth * 2 / 3.0 ? 1 : 0;
		boolean top = box.y() + height / 2.0 < screenHeight / 2.0;
		int maxX = Math.max(0, screenWidth - width);
		int offsetX = switch (horizontal) {
			case -1 -> box.x();
			case 0 -> box.x() - maxX / 2;
			default -> maxX - box.x();
		};
		int offsetY = top ? box.y() : Math.max(0, screenHeight - height) - box.y();
		return new Placed(Anchor.of(horizontal, top), offsetX, offsetY);
	}
}
