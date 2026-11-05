package com.merci.survivalkit.autoeat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.util.FoodSlot;
import com.merci.survivalkit.util.ItemPriorityResolver.Fallback;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The eating cycle: wait → pick a slot → hold the use key → hand the slot back.
 * Hunger is measured in half-drumsticks, the same 0-20 scale the game uses.
 */
class AutoEatTest {
	private static final int HUNGRY = 14;
	private static final FoodSlot BREAD = new FoodSlot(3, "minecraft:bread", 5, 6.0f, false);
	private static final FoodSlot STEAK = new FoodSlot(5, "minecraft:cooked_beef", 8, 12.8f, false);
	private static final FoodSlot ROTTEN_FLESH = new FoodSlot(7, "minecraft:rotten_flesh", 4, 0.8f, true);

	private FakeEatSettings settings;
	private AutoEat autoEat;
	private FakeEatContext client;
	private Hands hands;

	@BeforeEach
	void setUp() {
		settings = new FakeEatSettings();
		hands = new Hands();
		autoEat = new AutoEat(settings, hands);
		client = new FakeEatContext();
		client.selectedSlot = 1;
	}

	private void hungryWith(FoodSlot... available) {
		client.foodLevel = HUNGRY;
		client.food = List.of(available);
	}

	/** The game only lets you eat below full hunger, so a full bar must stay untouched. */
	@Test
	void aFullHungerBarIsLeftAlone() {
		client.foodLevel = 20;
		client.food = List.of(BREAD);

		autoEat.tick(client);

		assertFalse(client.useKeyHeld);
		assertEquals(List.of(), client.slotSelections);
	}

	@Test
	void hungerAtTheThresholdIsNotYetLowEnough() {
		client.foodLevel = 17;
		client.food = List.of(BREAD);

		autoEat.tick(client);

		assertFalse(client.useKeyHeld);
	}

	@Test
	void anEmptyHotbarIsLeftAlone() {
		hungryWith();

		autoEat.tick(client);

		assertFalse(client.useKeyHeld);
	}

	@Test
	void nothingHappensWhileAScreenIsOpen() {
		hungryWith(BREAD);
		client.ready = false;

		autoEat.tick(client);

		assertFalse(client.useKeyHeld);
		assertEquals(List.of(), client.slotSelections);
	}

	@Test
	void hungerSelectsTheFoodSlot() {
		hungryWith(BREAD);

		autoEat.tick(client);

		assertEquals(List.of(3), client.slotSelections);
	}

	@Test
	void selectingTheSlotAlsoHoldsTheUseKey() {
		hungryWith(BREAD);

		autoEat.tick(client);

		assertTrue(client.useKeyHeld);
	}

	@Test
	void theMostFillingFoodWins() {
		hungryWith(BREAD, STEAK);

		autoEat.tick(client);

		assertEquals(List.of(5), client.slotSelections);
	}

	@Test
	void riskyFoodIsOnlyEatenWhenNothingElseIsLeft() {
		hungryWith(ROTTEN_FLESH, BREAD);

		autoEat.tick(client);

		assertEquals(List.of(3), client.slotSelections);
	}

	@Test
	void riskyFoodIsBetterThanStarving() {
		hungryWith(ROTTEN_FLESH);

		autoEat.tick(client);

		assertEquals(List.of(7), client.slotSelections);
	}

	@Test
	void theKeyStaysHeldWhileTheBiteIsInProgress() {
		hungryWith(BREAD);
		autoEat.tick(client);
		client.usingItem = true;

		autoEat.tick(client);

		assertTrue(client.useKeyHeld);
	}

	@Test
	void theKeyIsReleasedWhenTheBiteFinishes() {
		hungryWith(BREAD);
		autoEat.tick(client);
		client.usingItem = true;
		autoEat.tick(client);
		client.usingItem = false;
		client.foodLevel = 20;

		autoEat.tick(client);

		assertFalse(client.useKeyHeld);
	}

	@Test
	void theOriginalSlotComesBackAfterTheBite() {
		hungryWith(BREAD);
		autoEat.tick(client);
		client.usingItem = true;
		autoEat.tick(client);
		client.usingItem = false;
		client.foodLevel = 20;

		autoEat.tick(client);

		assertEquals(1, client.selectedSlot);
	}

	@Test
	void stillBeingHungryStartsAnotherBite() {
		hungryWith(BREAD);
		autoEat.tick(client);
		client.usingItem = true;
		autoEat.tick(client);
		client.usingItem = false;

		autoEat.tick(client);
		autoEat.tick(client);

		assertTrue(client.useKeyHeld);
	}

	/** Opening a screen mid-bite must not leave the key stuck down on a borrowed slot. */
	@Test
	void aScreenOpeningMidBiteReleasesTheKeyAndRestoresTheSlot() {
		hungryWith(BREAD);
		autoEat.tick(client);
		client.usingItem = true;
		autoEat.tick(client);
		client.ready = false;

		autoEat.tick(client);

		assertFalse(client.useKeyHeld);
		assertEquals(1, client.selectedSlot);
	}

	@Test
	void foodDisappearingBeforeTheBiteStartsRestoresTheSlot() {
		client.biteStarts = false;
		hungryWith(BREAD);
		autoEat.tick(client);
		client.food = List.of();

		autoEat.tick(client);

		assertFalse(client.useKeyHeld);
		assertEquals(1, client.selectedSlot);
	}

	/** If the bite never starts — lag, a blocked hand — give up instead of holding forever. */
	@Test
	void aBiteThatNeverStartsIsAbandoned() {
		client.biteStarts = false;
		hungryWith(BREAD);

		for (int tick = 0; tick <= AutoEat.START_TIMEOUT_TICKS + 1; tick++) {
			autoEat.tick(client);
		}

		assertFalse(client.useKeyHeld);
		assertEquals(1, client.selectedSlot);
	}

	/**
	 * The use key is what makes the crosshair's target act: a chest opens, a door swings, a
	 * bed in the Nether goes off. So the bite is started directly, and the key is not down
	 * for a single tick before it has.
	 */
	@Test
	void theKeyIsNeverDownBeforeTheBiteHasStarted() {
		client.biteStarts = false;
		hungryWith(BREAD);

		for (int tick = 0; tick < AutoEat.START_TIMEOUT_TICKS; tick++) {
			autoEat.tick(client);
			assertFalse(client.useKeyHeld, "ısırık başlamadan tuş basılı: tick " + tick);
		}

		assertTrue(client.startedUsing.size() > 1, "ısırık her tick doğrudan yeniden denenmeli");
	}

	/** The server's word that the bite is over comes before the keys are read. */
	@Test
	void theKeyGoesUpBeforeTheKeysAreReadOnceTheBiteHasEnded() {
		hungryWith(BREAD);
		autoEat.tick(client);
		client.usingItem = false;

		autoEat.beforeKeysAreRead(client);

		assertFalse(client.useKeyHeld);
	}

	@Test
	void aBiteStillGoingKeepsTheKeyBeforeTheKeysAreRead() {
		hungryWith(BREAD);
		autoEat.tick(client);

		autoEat.beforeKeysAreRead(client);

		assertTrue(client.useKeyHeld);
	}

	@Test
	void beforeTheKeysAreReadAnIdleModuleTouchesNothing() {
		client.useKeyHeld = true;

		autoEat.beforeKeysAreRead(client);

		assertTrue(client.useKeyHeld, "boşta modül oyuncunun kendi bastığı tuşa dokunmamalı");
	}

	@Test
	void theMainHandBiteIsStartedDirectlyAndOnlyThenHeld() {
		hungryWith(BREAD);

		autoEat.tick(client);

		assertEquals(List.of(EatingHand.MAIN_HAND), client.startedUsing);
		assertTrue(client.useKeyHeld, "başlamış ısırık tuşla sürdürülür");
	}

	@Test
	void stoppingMidBiteReleasesTheKeyAndRestoresTheSlot() {
		hungryWith(BREAD);
		autoEat.tick(client);
		client.usingItem = true;
		autoEat.tick(client);

		autoEat.stop(client);

		assertFalse(client.useKeyHeld);
		assertEquals(1, client.selectedSlot);
	}

	@Test
	void stoppingWhenIdleTouchesNothing() {
		client.foodLevel = 20;

		autoEat.stop(client);

		assertEquals(List.of(), client.slotSelections);
	}

	/** The threshold is a live setting, so a change must take effect without a restart. */
	@Test
	void aRaisedThresholdTakesEffectOnTheNextTick() {
		client.foodLevel = 18;
		client.food = List.of(BREAD);
		autoEat.tick(client);
		assertFalse(client.useKeyHeld);

		settings.hungerThreshold = 19;
		autoEat.tick(client);

		assertTrue(client.useKeyHeld);
	}

	// --- settings reaching the state machine ---

	private static final FoodSlot GOLDEN_APPLE = new FoodSlot(2, "minecraft:golden_apple", 4, 9.6f, false);
	private static final FoodSlot BACKPACK_BREAD = new FoodSlot(20, "minecraft:bread", 5, 6.0f, false);

	/** Runs a whole bite: start it, let it land, then let it end. */
	private void eatOnce() {
		autoEat.tick(client);
		client.usingItem = true;
		autoEat.tick(client);
		client.usingItem = false;
		autoEat.tick(client);
	}

	@Test
	void theFirstItemOnThePriorityListIsEatenEvenIfSomethingFillsMore() {
		settings.priority = List.of("minecraft:bread");
		hungryWith(BREAD, STEAK);

		autoEat.tick(client);

		assertEquals(List.of(BREAD.slot()), client.slotSelections);
	}

	@Test
	void thePriorityListFallsThroughToItsSecondEntry() {
		settings.priority = List.of("minecraft:cooked_porkchop", "minecraft:cooked_beef");
		hungryWith(BREAD, STEAK);

		autoEat.tick(client);

		assertEquals(List.of(STEAK.slot()), client.slotSelections);
	}

	@Test
	void aBlockedItemIsNotEatenEvenWhenItIsAllThereIs() {
		settings.blocked = List.of("minecraft:bread");
		hungryWith(BREAD);

		autoEat.tick(client);

		assertFalse(client.useKeyHeld);
		assertEquals(List.of(), client.slotSelections);
	}

	@Test
	void theFallbackModeDecidesWhenNothingIsListed() {
		settings.fallback = Fallback.LOWEST_VALUE_FIRST;
		hungryWith(BREAD, STEAK);

		autoEat.tick(client);

		assertEquals(List.of(BREAD.slot()), client.slotSelections);
	}

	@Test
	void theDoNothingFallbackLeavesUnlistedFoodAlone() {
		settings.fallback = Fallback.NOTHING;
		hungryWith(BREAD, STEAK);

		autoEat.tick(client);

		assertFalse(client.useKeyHeld);
	}

	// --- golden apple ---

	@Test
	void lowHealthBringsOutTheGoldenAppleEvenOnAFullHungerBar() {
		settings.goldenApple = true;
		client.foodLevel = 20;
		client.health = 6.0f;
		client.food = List.of(BREAD, GOLDEN_APPLE);

		autoEat.tick(client);

		assertEquals(List.of(GOLDEN_APPLE.slot()), client.slotSelections);
	}

	@Test
	void healthAboveTheThresholdKeepsTheAppleInThePack() {
		settings.goldenApple = true;
		client.foodLevel = 20;
		client.health = 12.0f;
		client.food = List.of(BREAD, GOLDEN_APPLE);

		autoEat.tick(client);

		assertFalse(client.useKeyHeld);
	}

	@Test
	void theHealthThresholdMeansNothingWhileTheAppleRuleIsOff() {
		settings.goldenApple = false;
		client.foodLevel = 20;
		client.health = 2.0f;
		client.food = List.of(GOLDEN_APPLE);

		autoEat.tick(client);

		assertFalse(client.useKeyHeld);
	}

	@Test
	void aBlockedGoldenAppleStaysBlockedInAnEmergency() {
		settings.goldenApple = true;
		settings.blocked = List.of("minecraft:golden_apple");
		client.foodLevel = 20;
		client.health = 2.0f;
		client.food = List.of(GOLDEN_APPLE);

		autoEat.tick(client);

		assertFalse(client.useKeyHeld);
	}

	@Test
	void theAppleWinsOverOrdinaryFoodWhileHealthIsLow() {
		settings.goldenApple = true;
		settings.priority = List.of("minecraft:bread");
		client.health = 4.0f;
		hungryWith(BREAD, GOLDEN_APPLE);

		autoEat.tick(client);

		assertEquals(List.of(GOLDEN_APPLE.slot()), client.slotSelections);
	}

	// --- reaching into the backpack ---

	@Test
	void foodOutsideTheHotbarIsSwappedIntoTheSlotInHand() {
		hungryWith(BACKPACK_BREAD);

		autoEat.tick(client);

		assertEquals(List.of("20>1"), client.swaps);
		assertEquals(List.of(), client.slotSelections, "takas varken seçim değişmemeli");
	}

	@Test
	void theSwapIsUndoneWhenTheBiteEnds() {
		hungryWith(BACKPACK_BREAD);

		eatOnce();

		assertEquals(List.of("20>1", "20>1"), client.swaps);
	}

	@Test
	void theSwapIsUndoneEvenWhenReturningToTheOldSlotIsOff() {
		settings.returnToSlot = false;
		hungryWith(BACKPACK_BREAD);

		eatOnce();

		assertEquals(List.of("20>1", "20>1"), client.swaps);
	}

	@Test
	void theSwapIsUndoneWhenTheModuleIsSwitchedOffMidBite() {
		hungryWith(BACKPACK_BREAD);
		autoEat.tick(client);

		autoEat.stop(client);

		assertEquals(List.of("20>1", "20>1"), client.swaps);
		assertFalse(client.useKeyHeld);
	}

	@Test
	void hotbarFoodIsSelectedRatherThanSwapped() {
		hungryWith(BREAD);

		autoEat.tick(client);

		assertEquals(List.of(), client.swaps);
		assertEquals(List.of(BREAD.slot()), client.slotSelections);
	}

	// --- returning to the old slot ---

	@Test
	void turningOffTheReturnLeavesTheFoodSlotSelected() {
		settings.returnToSlot = false;
		hungryWith(BREAD);

		eatOnce();

		assertEquals(BREAD.slot(), client.selectedSlot);
	}

	@Test
	void leavingTheReturnOnPutsTheOldSlotBack() {
		settings.returnToSlot = true;
		hungryWith(BREAD);

		eatOnce();

		assertEquals(1, client.selectedSlot);
	}

	// --- golden apples are saved, not spent on hunger ---

	private static final FoodSlot ENCHANTED_APPLE =
		new FoodSlot(4, "minecraft:enchanted_golden_apple", 4, 9.6f, false);

	@Test
	void aGoldenAppleIsNeverEatenForHungerAlone() {
		hungryWith(GOLDEN_APPLE, BREAD);

		autoEat.tick(client);

		assertEquals(List.of(BREAD.slot()), client.slotSelections);
	}

	@Test
	void hungerGoesUnfedRatherThanSpendTheLastGoldenApple() {
		hungryWith(GOLDEN_APPLE);

		autoEat.tick(client);

		assertFalse(client.useKeyHeld);
	}

	@Test
	void anEnchantedAppleIsNotSpentOnHungerEither() {
		hungryWith(ENCHANTED_APPLE);

		autoEat.tick(client);

		assertFalse(client.useKeyHeld);
	}

	/** Reserved is not blocked: asking for it by name is still honoured. */
	@Test
	void aGoldenAppleOnThePriorityListIsEatenForHunger() {
		settings.priority = List.of("minecraft:golden_apple");
		hungryWith(GOLDEN_APPLE, BREAD);

		autoEat.tick(client);

		assertEquals(List.of(GOLDEN_APPLE.slot()), client.slotSelections);
	}

	// --- the enchanted apple joins the health rule only when asked ---

	@Test
	void theHealthRuleLeavesTheEnchantedAppleAloneByDefault() {
		settings.goldenApple = true;
		client.foodLevel = 20;
		client.health = 4.0f;
		client.food = List.of(ENCHANTED_APPLE);

		autoEat.tick(client);

		assertFalse(client.useKeyHeld);
	}

	@Test
	void turningOnTheEnchantedAppleLetsTheHealthRuleUseIt() {
		settings.goldenApple = true;
		settings.enchantedGoldenApple = true;
		client.foodLevel = 20;
		client.health = 4.0f;
		client.food = List.of(ENCHANTED_APPLE);

		autoEat.tick(client);

		assertEquals(List.of(ENCHANTED_APPLE.slot()), client.slotSelections);
	}

	@Test
	void thePlainAppleIsSpentBeforeTheEnchantedOne() {
		settings.goldenApple = true;
		settings.enchantedGoldenApple = true;
		client.foodLevel = 20;
		client.health = 4.0f;
		client.food = List.of(ENCHANTED_APPLE, GOLDEN_APPLE);

		autoEat.tick(client);

		assertEquals(List.of(GOLDEN_APPLE.slot()), client.slotSelections);
	}

	@Test
	void thePlainAppleStillWorksWhileTheEnchantedOneIsOff() {
		settings.goldenApple = true;
		client.foodLevel = 20;
		client.health = 4.0f;
		client.food = List.of(ENCHANTED_APPLE, GOLDEN_APPLE);

		autoEat.tick(client);

		assertEquals(List.of(GOLDEN_APPLE.slot()), client.slotSelections);
	}
}
