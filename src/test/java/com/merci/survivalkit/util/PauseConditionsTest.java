package com.merci.survivalkit.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.util.PauseConditions.State;
import org.junit.jupiter.api.Test;

class PauseConditionsTest {
	@Test
	void aPlayerStandingInAWorldIsNotPaused() {
		assertFalse(PauseConditions.paused(new State(true, false)));
	}

	@Test
	void anOpenScreenPauses() {
		assertTrue(PauseConditions.paused(new State(true, true)));
	}

	@Test
	void beingOutOfAWorldPauses() {
		assertTrue(PauseConditions.paused(new State(false, false)));
	}

	@Test
	void anOpenScreenOutsideAWorldStillPauses() {
		assertTrue(PauseConditions.paused(new State(false, true)));
	}

	@Test
	void activeIsTheOppositeOfPaused() {
		for (boolean inWorld : new boolean[] {false, true}) {
			for (boolean screenOpen : new boolean[] {false, true}) {
				State state = new State(inWorld, screenOpen);

				assertTrue(PauseConditions.active(state) != PauseConditions.paused(state));
			}
		}
	}
}
