package com.merci.survivalkit.wdl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Whose inventory an opened screen shows. The screen itself does not say — the entity sits in
 * a field the game keeps to itself — so it is worked out from what the player just did. A guess
 * that could land on the wrong animal or cart is worse than not capturing at all.
 */
class InventoryTargetTest {
	private static final String RIDDEN = "binilen";
	private static final String CLICKED = "tıklanan";

	@Test
	void theAnimalBeingRiddenIsTheOne() {
		assertEquals(Optional.of(RIDDEN), InventoryTarget.choose(Optional.of(RIDDEN), Optional.of(CLICKED), 1, 1.0));
	}

	@Test
	void notRidingTheOneJustClickedIsTheOne() {
		assertEquals(Optional.of(CLICKED), InventoryTarget.choose(Optional.empty(), Optional.of(CLICKED), 3, 4.0));
	}

	@Test
	void aClickFromAWhileAgoIsNotTrusted() {
		assertEquals(Optional.empty(),
				InventoryTarget.choose(Optional.empty(), Optional.of(CLICKED), InventoryTarget.RECENT_TICKS + 1, 4.0));
	}

	@Test
	void aClickedEntityThatIsNowFarAwayIsNotTrusted() {
		double tooFar = InventoryTarget.NEAR_BLOCKS * InventoryTarget.NEAR_BLOCKS + 1;

		assertEquals(Optional.empty(), InventoryTarget.choose(Optional.empty(), Optional.of(CLICKED), 1, tooFar));
	}

	@Test
	void nothingRiddenAndNothingClickedIsNobody() {
		assertEquals(Optional.empty(), InventoryTarget.choose(Optional.empty(), Optional.empty(), 0, 0.0));
	}
}
