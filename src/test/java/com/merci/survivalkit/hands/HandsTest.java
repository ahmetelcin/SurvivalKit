package com.merci.survivalkit.hands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.hands.Hands.Claim;
import com.merci.survivalkit.hands.Hands.Priority;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The rule that keeps two modules out of the same hand.
 *
 * <p>Everything here is about the moment they collide: who wins, who is told, and what the
 * loser is expected to have put back by the time it is over.
 */
class HandsTest {
	/** A module that remembers being interrupted, which is all the lock asks of one. */
	private static final class Module implements Hands.Owner {
		private final String name;
		private int interrupted;

		private Module(String name) {
			this.name = name;
		}

		@Override
		public void handsTaken() {
			interrupted++;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	private Hands hands;
	private Module eating;
	private Module totem;

	@BeforeEach
	void setUp() {
		hands = new Hands();
		eating = new Module("yeme");
		totem = new Module("totem");
	}

	@Test
	void freeHandsAreGivenToWhoeverAsks() {
		assertTrue(hands.take(eating, Priority.AUTO_EAT, Claim.OFF_HAND));
		assertTrue(hands.holds(eating));
	}

	@Test
	void twoModulesCanHoldDifferentHandsAtOnce() {
		hands.take(eating, Priority.AUTO_EAT, Claim.MAIN_HAND);

		assertTrue(hands.take(totem, Priority.AUTO_TOTEM, Claim.OFF_HAND));
		assertTrue(hands.holds(eating));
		assertEquals(0, eating.interrupted);
	}

	// --- who wins ---

	@Test
	void theMoreUrgentModuleTakesTheHandItWants() {
		hands.take(eating, Priority.AUTO_EAT, Claim.OFF_HAND, Claim.INVENTORY);

		assertTrue(hands.take(totem, Priority.AUTO_TOTEM, Claim.OFF_HAND));
		assertTrue(hands.holds(totem));
	}

	@Test
	void theInterruptedModuleIsToldExactlyOnce() {
		hands.take(eating, Priority.AUTO_EAT, Claim.OFF_HAND, Claim.INVENTORY);

		hands.take(totem, Priority.AUTO_TOTEM, Claim.OFF_HAND);

		assertEquals(1, eating.interrupted);
	}

	/** It is interrupted, not trimmed: it has put everything back, so it holds nothing. */
	@Test
	void theInterruptedModuleLosesEveryClaimNotJustTheContestedOne() {
		hands.take(eating, Priority.AUTO_EAT, Claim.OFF_HAND, Claim.INVENTORY);

		hands.take(totem, Priority.AUTO_TOTEM, Claim.OFF_HAND);

		assertFalse(hands.holds(eating));
		assertEquals(Set.of(Claim.OFF_HAND), hands.taken());
	}

	@Test
	void theLessUrgentModuleIsRefusedAndTheHolderIsLeftAlone() {
		hands.take(totem, Priority.AUTO_TOTEM, Claim.OFF_HAND);

		assertFalse(hands.take(eating, Priority.AUTO_EAT, Claim.OFF_HAND));
		assertEquals(0, totem.interrupted);
		assertTrue(hands.holds(totem));
		assertFalse(hands.holds(eating));
	}

	/** Nobody is interrupted by an equal: first there keeps it, or they would trade forever. */
	@Test
	void anEqualClaimDoesNotInterruptTheOneAlreadyHolding() {
		Module other = new Module("başka");
		hands.take(eating, Priority.AUTO_EAT, Claim.OFF_HAND);

		assertFalse(hands.take(other, Priority.AUTO_EAT, Claim.OFF_HAND));
		assertEquals(0, eating.interrupted);
	}

	@Test
	void aHoldOnSomethingElseSurvivesTheInterruption() {
		Module walking = new Module("yürüme");
		hands.take(walking, Priority.AUTO_EAT, Claim.MAIN_HAND);
		hands.take(eating, Priority.AUTO_EAT, Claim.OFF_HAND);

		hands.take(totem, Priority.AUTO_TOTEM, Claim.OFF_HAND);

		assertTrue(hands.holds(walking));
		assertEquals(0, walking.interrupted);
	}

	/** The inventory counts: a backpack swap collides with a backpack swap. */
	@Test
	void twoModulesSwappingTheBackpackCollideEvenInDifferentHands() {
		hands.take(eating, Priority.AUTO_EAT, Claim.MAIN_HAND, Claim.INVENTORY);

		assertTrue(hands.take(totem, Priority.AUTO_TOTEM, Claim.OFF_HAND, Claim.INVENTORY));
		assertEquals(1, eating.interrupted);
	}

	// --- letting go ---

	@Test
	void releasingGivesTheHandsBack() {
		hands.take(eating, Priority.AUTO_EAT, Claim.OFF_HAND);

		hands.release(eating);

		assertFalse(hands.holds(eating));
		assertEquals(Set.of(), hands.taken());
	}

	@Test
	void releasingWhatWasNeverHeldIsHarmless() {
		hands.release(eating);

		assertFalse(hands.holds(eating));
	}

	/** A module that lets go inside its own interruption must not confuse the lock. */
	@Test
	void aModuleMayReleaseItselfWhileBeingInterrupted() {
		Hands.Owner tidy = new Hands.Owner() {
			@Override
			public void handsTaken() {
				hands.release(this);
			}
		};
		hands.take(tidy, Priority.AUTO_EAT, Claim.OFF_HAND);

		assertTrue(hands.take(totem, Priority.AUTO_TOTEM, Claim.OFF_HAND));
		assertTrue(hands.holds(totem));
		assertFalse(hands.holds(tidy));
	}

	@Test
	void theSameModuleCanWidenItsOwnClaimWithoutInterruptingItself() {
		hands.take(eating, Priority.AUTO_EAT, Claim.OFF_HAND);

		assertTrue(hands.take(eating, Priority.AUTO_EAT, Claim.OFF_HAND, Claim.INVENTORY));
		assertEquals(0, eating.interrupted);
		assertEquals(Set.of(Claim.OFF_HAND, Claim.INVENTORY), hands.taken());
	}

	@Test
	void whatIsTakenListsEveryHold() {
		hands.take(eating, Priority.AUTO_EAT, Claim.MAIN_HAND);
		hands.take(totem, Priority.AUTO_TOTEM, Claim.OFF_HAND);

		assertEquals(Set.of(Claim.MAIN_HAND, Claim.OFF_HAND), hands.taken());
	}
}
