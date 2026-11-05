package com.merci.survivalkit.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A 40×20 panel on a 400×300 screen, from each corner and middle. */
class HudLayoutTest {
	private static HudBox place(Anchor anchor, int x, int y) {
		return HudLayout.place(anchor, x, y, 40, 20, 400, 300);
	}

	@Test
	void fromTheTopLeftTheOffsetsAreTheCorner() {
		assertEquals(new HudBox(5, 7, 40, 20), place(Anchor.TOP_LEFT, 5, 7));
	}

	@Test
	void fromTheTopRightTheOffsetIsFromTheRightEdge() {
		assertEquals(new HudBox(355, 7, 40, 20), place(Anchor.TOP_RIGHT, 5, 7));
	}

	@Test
	void fromTheBottomRightBothAreFromTheFarEdges() {
		assertEquals(new HudBox(355, 273, 40, 20), place(Anchor.BOTTOM_RIGHT, 5, 7));
	}

	@Test
	void fromTheBottomLeft() {
		assertEquals(new HudBox(5, 273, 40, 20), place(Anchor.BOTTOM_LEFT, 5, 7));
	}

	@Test
	void fromAMiddleTheOffsetIsFromCentre() {
		assertEquals(new HudBox(180, 7, 40, 20), place(Anchor.TOP_CENTER, 0, 7));
		assertEquals(new HudBox(170, 273, 40, 20), place(Anchor.BOTTOM_CENTER, -10, 7));
	}

	/** Pushed past an edge, the panel stays whole on the screen. */
	@Test
	void aPanelIsKeptOnTheScreen() {
		assertEquals(new HudBox(360, 280, 40, 20), place(Anchor.TOP_LEFT, 1000, 1000));
		assertEquals(new HudBox(0, 0, 40, 20), place(Anchor.TOP_LEFT, -50, -50));
		assertEquals(new HudBox(0, 0, 40, 20), place(Anchor.BOTTOM_RIGHT, 1000, 1000));
	}

	@Test
	void anAnchorIsFoundBySide() {
		assertEquals(Anchor.BOTTOM_CENTER, Anchor.of(0, false));
		assertEquals(Anchor.TOP_RIGHT, Anchor.of(5, true));
	}
}
