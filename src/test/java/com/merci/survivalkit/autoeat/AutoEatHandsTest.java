package com.merci.survivalkit.autoeat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.hands.Hands.Claim;
import com.merci.survivalkit.hands.Hands.Priority;
import com.merci.survivalkit.util.FoodSlot;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * AutoEat as a tenant of the hand lock: what it claims, what it does when it cannot have it,
 * and what it has put back by the time something more urgent gets it.
 *
 * <p>Eating is the least urgent thing a module can be doing, so this is almost entirely
 * about getting out of the way properly.
 */
class AutoEatHandsTest {
	private static final int HUNGRY = 14;
	private static final FoodSlot BREAD = new FoodSlot(3, "minecraft:bread", 5, 6.0f, false);
	private static final FoodSlot BACKPACK_STEAK = new FoodSlot(22, "minecraft:cooked_beef", 8, 12.8f, false);
	private static final int SWORD_SLOT = 4;

	/** Stands in for AutoTotem, which does not exist yet and does not need to. */
	private static final class Urgent implements Hands.Owner {
		@Override
		public void handsTaken() {
			throw new AssertionError("bundan daha acil bir şey yok");
		}
	}

	private FakeEatSettings settings;
	private FakeEatContext client;
	private Hands hands;
	private AutoEat autoEat;
	private Urgent totem;

	@BeforeEach
	void setUp() {
		settings = new FakeEatSettings();
		hands = new Hands();
		autoEat = new AutoEat(settings, hands);
		client = new FakeEatContext();
		client.selectedSlot = SWORD_SLOT;
		totem = new Urgent();
		client.foodLevel = HUNGRY;
	}

	private int tickUntilEating() {
		for (int tick = 1; tick <= 10; tick++) {
			autoEat.tick(client);

			if (client.usingItem) {
				return tick;
			}

			if (client.useKeyHeld) {
				client.usingItem = true;
			}
		}

		throw new AssertionError("ısırık başlamadı");
	}

	// --- holding them ---

	@Test
	void theHandsAreHeldForTheLengthOfTheMeal() {
		client.food = List.of(BREAD);

		tickUntilEating();

		assertTrue(hands.holds(autoEat));
	}

	@Test
	void theHandsAreGivenBackWhenTheMealEnds() {
		client.food = List.of(BREAD);
		tickUntilEating();

		client.usingItem = false;
		autoEat.tick(client);

		assertFalse(hands.holds(autoEat));
	}

	@Test
	void theHandsAreGivenBackWhenTheModuleIsSwitchedOff() {
		client.food = List.of(BREAD);
		tickUntilEating();

		autoEat.stop(client);

		assertFalse(hands.holds(autoEat));
	}

	/** Only what it will actually disturb: a bite out of the hotbar leaves the rest free. */
	@Test
	void aMealFromTheHotbarClaimsTheHandAndNothingElse() {
		client.food = List.of(BREAD);

		tickUntilEating();

		assertEquals(java.util.Set.of(Claim.MAIN_HAND), hands.taken());
	}

	@Test
	void aMealFromTheBackpackClaimsTheInventoryToo() {
		client.food = List.of(BACKPACK_STEAK);

		tickUntilEating();

		assertEquals(java.util.Set.of(Claim.MAIN_HAND, Claim.INVENTORY), hands.taken());
	}

	@Test
	void aMealInTheOffHandClaimsThatHandAndTheInventory() {
		settings.eatingHand = EatingHand.OFF_HAND;
		client.food = List.of(BREAD);

		tickUntilEating();

		assertEquals(java.util.Set.of(Claim.OFF_HAND, Claim.INVENTORY), hands.taken());
	}

	// --- not getting them ---

	@Test
	void nothingIsMovedWhileSomethingMoreUrgentHoldsTheHand() {
		settings.eatingHand = EatingHand.OFF_HAND;
		client.food = List.of(BREAD);
		hands.take(totem, Priority.AUTO_TOTEM, Claim.OFF_HAND);

		autoEat.tick(client);
		autoEat.tick(client);

		assertEquals(List.of(), client.offhandSwaps, "eller başkasındayken hiçbir şey taşınmamalıydı");
		assertFalse(client.useKeyHeld);
		assertFalse(hands.holds(autoEat));
	}

	/** And it is not sulking: the moment they are free it eats. */
	@Test
	void theMealStartsAsSoonAsTheHandsAreFree() {
		settings.eatingHand = EatingHand.OFF_HAND;
		client.food = List.of(BREAD);
		hands.take(totem, Priority.AUTO_TOTEM, Claim.OFF_HAND);
		autoEat.tick(client);

		hands.release(totem);
		tickUntilEating();

		assertEquals(List.of(3), client.offhandSwaps);
	}

	/** A hand it was never going to touch is not a reason to go hungry. */
	@Test
	void aHoldOnTheOtherHandDoesNotStopTheMeal() {
		client.food = List.of(BREAD);
		hands.take(totem, Priority.AUTO_TOTEM, Claim.OFF_HAND);

		tickUntilEating();

		assertEquals(List.of(3), client.slotSelections);
	}

	// --- being interrupted ---

	@Test
	void anInterruptionPutsTheOffHandBackAndLetsGoOfTheKey() {
		settings.eatingHand = EatingHand.OFF_HAND;
		client.food = List.of(BREAD);
		tickUntilEating();

		hands.take(totem, Priority.AUTO_TOTEM, Claim.OFF_HAND);

		assertEquals(List.of(3, 3), client.offhandSwaps, "yiyecek diğer elden geri alınmalıydı");
		assertFalse(client.useKeyHeld);
		assertFalse(hands.holds(autoEat));
	}

	@Test
	void anInterruptionUndoesABackpackSwapAndGivesTheSlotBack() {
		client.food = List.of(BACKPACK_STEAK);
		tickUntilEating();

		hands.take(totem, Priority.AUTO_TOTEM, Claim.INVENTORY);

		assertEquals(List.of("22>4", "22>4"), client.swaps, "sırt çantası takası geri alınmalıydı");
		assertEquals(SWORD_SLOT, client.selectedSlot);
	}

	/** Interrupted means stopped, not paused: the next tick starts a fresh attempt. */
	@Test
	void afterAnInterruptionTheNextAttemptStartsFromTheBeginning() {
		settings.eatingHand = EatingHand.OFF_HAND;
		client.food = List.of(BREAD);
		tickUntilEating();
		hands.take(totem, Priority.AUTO_TOTEM, Claim.OFF_HAND);
		client.usingItem = false;

		hands.release(totem);
		autoEat.tick(client);

		assertEquals(List.of(3, 3, 3), client.offhandSwaps);
		assertTrue(hands.holds(autoEat));
	}
}
