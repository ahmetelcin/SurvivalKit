package com.merci.survivalkit.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A 40×20 box dragged on a 300×200 screen: stuck to edges it comes near, kept from the nearest corner. */
class HudDropTest {
	private static HudDrop.Placed drop(int x, int y) {
		return HudDrop.drop(x, y, 40, 20, 300, 200);
	}

	@Test
	void nearTheLeftEdgeItSticksToIt() {
		assertEquals(new HudBox(0, 50, 40, 20), HudDrop.snap(4, 50, 40, 20, 300, 200));
	}

	@Test
	void nearTheRightAndBottomEdgesItSticksToThem() {
		assertEquals(new HudBox(260, 180, 40, 20), HudDrop.snap(256, 175, 40, 20, 300, 200));
	}

	@Test
	void nearTheMiddleItSticksToTheMiddle() {
		assertEquals(new HudBox(130, 50, 40, 20), HudDrop.snap(133, 50, 40, 20, 300, 200));
	}

	@Test
	void awayFromEveryEdgeItStaysWhereItWasLet() {
		assertEquals(new HudBox(60, 50, 40, 20), HudDrop.snap(60, 50, 40, 20, 300, 200));
	}

	@Test
	void draggedOffTheScreenItStaysOnIt() {
		assertEquals(new HudBox(0, 180, 40, 20), HudDrop.snap(-80, 900, 40, 20, 300, 200));
	}

	@Test
	void inTheTopLeftItIsKeptFromTheTopLeft() {
		assertEquals(new HudDrop.Placed(Anchor.TOP_LEFT, 20, 30), drop(20, 30));
	}

	@Test
	void inTheBottomRightItIsKeptFromThere() {
		assertEquals(new HudDrop.Placed(Anchor.BOTTOM_RIGHT, 0, 10), drop(259, 170));
	}

	@Test
	void inTheMiddleThirdItIsKeptFromTheMiddle() {
		assertEquals(new HudDrop.Placed(Anchor.TOP_CENTER, 20, 40), drop(150, 40));
	}

	/** Dropped and placed again by the layout, the box is exactly where it was let go, snapped. */
	@Test
	void whereItIsDroppedIsWhereTheLayoutPutsIt() {
		for (int x = -10; x < 300; x += 7) {
			for (int y = -10; y < 200; y += 9) {
				HudBox snapped = HudDrop.snap(x, y, 40, 20, 300, 200);
				HudDrop.Placed placed = drop(x, y);

				assertEquals(snapped, HudLayout.place(placed.anchor(), placed.offsetX(), placed.offsetY(), 40, 20, 300, 200),
					"bırakılan " + x + "," + y);
			}
		}
	}
}
