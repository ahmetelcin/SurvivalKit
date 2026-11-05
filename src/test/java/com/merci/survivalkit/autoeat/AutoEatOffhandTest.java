package com.merci.survivalkit.autoeat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.util.FoodSlot;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Eating out of the off hand, which exists so the main hand is never disturbed.
 *
 * <p>Three things have to hold, and each of them is a way this can go wrong in a fight. The
 * main hand keeps what it was holding, down to the slot. The bite is started for the off hand
 * itself rather than by pressing use, which would run the main hand first and place a block,
 * strip a log or raise a shield. And whatever the off hand held before comes back, however
 * the meal ended.
 */
class AutoEatOffhandTest {
	private static final int HUNGRY = 14;
	private static final FoodSlot BREAD = new FoodSlot(3, "minecraft:bread", 5, 6.0f, false);
	private static final FoodSlot BACKPACK_STEAK = new FoodSlot(22, "minecraft:cooked_beef", 8, 12.8f, false);
	private static final String TOTEM = "minecraft:totem_of_undying";
	private static final String SHIELD = "minecraft:shield";

	/** The slot the player is holding a sword in, and must still be holding afterwards. */
	private static final int SWORD_SLOT = 4;

	private FakeEatSettings settings;
	private AutoEat autoEat;
	private FakeEatContext client;
	private Hands hands;

	@BeforeEach
	void setUp() {
		settings = new FakeEatSettings();
		hands = new Hands();
		settings.eatingHand = EatingHand.OFF_HAND;
		autoEat = new AutoEat(settings, hands);
		client = new FakeEatContext();
		client.selectedSlot = SWORD_SLOT;
	}

	private void hungryWith(FoodSlot... available) {
		client.foodLevel = HUNGRY;
		client.food = List.of(available);
	}

	/**
	 * Runs the machine until the bite is under way, playing the game's own part: a held use
	 * key starts using what is in the main hand. That is exactly the behaviour the off-hand
	 * mode has to avoid leaning on, so a mode that secretly relied on the key would still be
	 * caught — by the tests that ask who started the bite, and whether the key was down
	 * before it did.
	 */
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

	// --- the main hand is not touched ---

	@Test
	void theFoodIsPutInTheOffHandAndTheSelectionIsLeftAlone() {
		hungryWith(BREAD);

		tickUntilEating();

		assertEquals(List.of(3), client.offhandSwaps);
		assertEquals(List.of(), client.slotSelections, "ana el seçimi hiç değişmemeliydi");
		assertEquals(SWORD_SLOT, client.selectedSlot);
	}

	/** The off hand can be reached from the backpack directly: no hotbar slot is borrowed. */
	@Test
	void foodInTheBackpackGoesStraightToTheOffHand() {
		settings.hungerThreshold = 17;
		hungryWith(BACKPACK_STEAK);

		tickUntilEating();

		assertEquals(List.of(22), client.offhandSwaps);
		assertEquals(List.of(), client.swaps, "hotbar takası yapılmamalıydı");
	}

	// --- the bite is started for the off hand, not by pressing use ---

	@Test
	void theBiteIsStartedForTheOffHandItself() {
		hungryWith(BREAD);

		tickUntilEating();

		assertEquals(List.of(EatingHand.OFF_HAND), client.startedUsing);
	}

	/**
	 * The whole point: while there is no bite running, the use key is up. A key held over a
	 * main hand full of blocks is how a hole gets filled in with dirt.
	 */
	@Test
	void theUseKeyIsNeverHeldWhileNothingIsBeingEaten() {
		hungryWith(BREAD);

		// The swap tick: food is on its way to the off hand, nothing is being eaten yet.
		autoEat.tick(client);

		assertFalse(client.usingItem, "ilk tick'te henüz yeme başlamamalıydı");
		assertFalse(client.useKeyHeld, "yeme başlamadan kullanım tuşu basılı olmamalıydı");
	}

	@Test
	void theUseKeyIsHeldOnceTheBiteIsUnderWay() {
		hungryWith(BREAD);

		tickUntilEating();

		assertTrue(client.useKeyHeld);
	}

	/** And it is let go the moment the bite ends, before the next right-click can land. */
	@Test
	void theUseKeyIsReleasedTheTickTheBiteEnds() {
		hungryWith(BREAD);
		tickUntilEating();

		client.usingItem = false;
		autoEat.tick(client);

		assertFalse(client.useKeyHeld);
	}

	// --- the off hand is always put back ---

	@Test
	void theOffHandIsPutBackWhenTheBiteEnds() {
		hungryWith(BREAD);
		tickUntilEating();

		client.usingItem = false;
		autoEat.tick(client);

		assertEquals(List.of(3, 3), client.offhandSwaps, "takas geri alınmalıydı");
		assertEquals("", client.offhandId);
	}

	@Test
	void theOffHandIsPutBackWhenTheModuleIsSwitchedOffMidBite() {
		hungryWith(BREAD);
		tickUntilEating();

		autoEat.stop(client);

		assertEquals(List.of(3, 3), client.offhandSwaps);
		assertFalse(client.useKeyHeld);
	}

	/** A shield, a map, anything: it goes back where it was, not just totems. */
	@Test
	void whateverWasInTheOffHandComesBack() {
		client.offhandId = SHIELD;
		hungryWith(BREAD);
		tickUntilEating();

		client.usingItem = false;
		autoEat.tick(client);

		assertEquals(List.of(3, 3), client.offhandSwaps);
	}

	// --- the totem rule ---

	@Test
	void aTotemInTheOffHandIsLeftWhereItIs() {
		client.offhandId = TOTEM;
		hungryWith(BREAD);

		tickUntilEating();

		assertEquals(List.of(), client.offhandSwaps, "totem yerinden oynamamalıydı");
		assertEquals(TOTEM, client.offhandId);
	}

	/** With the totem left alone, the bite has to happen in the main hand instead. */
	@Test
	void theBiteFallsBackToTheMainHandWhenATotemIsHeld() {
		client.offhandId = TOTEM;
		hungryWith(BREAD);

		tickUntilEating();

		assertEquals(List.of(3), client.slotSelections);
		assertEquals(List.of(EatingHand.MAIN_HAND), client.startedUsing, "totem varken ısırık ana el için başlatılır");
	}

	/** Negative control: with the rule off, the totem is treated like anything else. */
	@Test
	void theTotemRuleCanBeTurnedOff() {
		settings.keepOffhandTotem = false;
		client.offhandId = TOTEM;
		hungryWith(BREAD);

		tickUntilEating();

		assertEquals(List.of(3), client.offhandSwaps);
	}

	/** Negative control: the rule is about the off hand, not about totems anywhere. */
	@Test
	void aTotemInTheMainHandDoesNotStopTheOffHandBite() {
		client.offhandId = "";
		hungryWith(BREAD);

		tickUntilEating();

		assertEquals(List.of(3), client.offhandSwaps);
	}

	// --- the food is already in hand ---

	/**
	 * Food in the selected slot is already held: swapping it into the off hand would take it
	 * out of the main hand, which is the one thing this mode promises not to do.
	 */
	@Test
	void foodAlreadyInTheMainHandIsSimplyEaten() {
		FoodSlot heldBread = new FoodSlot(SWORD_SLOT, "minecraft:bread", 5, 6.0f, false);
		hungryWith(heldBread);

		tickUntilEating();

		assertEquals(List.of(), client.offhandSwaps);
		assertEquals(List.of(), client.swaps);
		assertEquals(SWORD_SLOT, client.selectedSlot);
	}

	// --- the other mode is untouched ---

	/** Negative control: none of this happens unless the setting asks for it. */
	@Test
	void theMainHandModeStillSelectsTheFoodSlot() {
		settings.eatingHand = EatingHand.MAIN_HAND;
		hungryWith(BREAD);

		tickUntilEating();

		assertEquals(List.of(3), client.slotSelections);
		assertEquals(List.of(), client.offhandSwaps);
		assertEquals(List.of(EatingHand.MAIN_HAND), client.startedUsing);
	}
}
