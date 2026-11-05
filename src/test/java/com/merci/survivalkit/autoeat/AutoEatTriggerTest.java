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
 * When a meal starts: the fixed line, the automatic one worked out from the food, and low
 * health that does not wait for either.
 */
class AutoEatTriggerTest {
	private static final FoodSlot BREAD = new FoodSlot(3, "minecraft:bread", 5, 6.0f, false);
	private static final FoodSlot STEAK = new FoodSlot(5, "minecraft:cooked_beef", 8, 12.8f, false);
	private static final FoodSlot GOLDEN_APPLE = new FoodSlot(6, "minecraft:golden_apple", 4, 9.6f, false);

	private FakeEatSettings settings;
	private AutoEat autoEat;
	private FakeEatContext client;

	@BeforeEach
	void setUp() {
		settings = new FakeEatSettings();
		autoEat = new AutoEat(settings, new Hands());
		client = new FakeEatContext();
		client.selectedSlot = 1;
	}

	private boolean eatsAt(int foodLevel, FoodSlot... available) {
		client.foodLevel = foodLevel;
		client.food = List.of(available);
		autoEat.tick(client);
		return client.useKeyHeld;
	}

	// --- automatic: the food draws the line ---

	@Test
	void steakWaitsUntilThereIsRoomForAllEightPoints() {
		settings.thresholdMode = ThresholdMode.AUTO;

		assertFalse(eatsAt(13, STEAK), "13'te 1 puan boşa giderdi");
		assertTrue(eatsAt(12, STEAK));
	}

	@Test
	void breadWaitsUntilThereIsRoomForAllFivePoints() {
		settings.thresholdMode = ThresholdMode.AUTO;

		assertFalse(eatsAt(16, BREAD));
		assertTrue(eatsAt(15, BREAD));
	}

	/** Bread would fit at 15, but steak is what would be eaten, and steak does not. */
	@Test
	void theLineIsTheFoodThatWouldBeEatenNotTheSmallest() {
		settings.thresholdMode = ThresholdMode.AUTO;

		assertFalse(eatsAt(15, BREAD, STEAK));
		assertTrue(eatsAt(12, BREAD, STEAK));
		assertEquals(List.of(STEAK.slot()), client.slotSelections);
	}

	@Test
	void theLineFollowsThePreferenceOrderToo() {
		settings.thresholdMode = ThresholdMode.AUTO;
		settings.priority = List.of("minecraft:bread");

		assertTrue(eatsAt(15, BREAD, STEAK));
		assertEquals(List.of(BREAD.slot()), client.slotSelections);
	}

	@Test
	void automaticIgnoresTheFixedThreshold() {
		settings.thresholdMode = ThresholdMode.AUTO;
		settings.hungerThreshold = 19;

		assertFalse(eatsAt(13, STEAK));
	}

	@Test
	void fixedStillUsesTheThreshold() {
		settings.thresholdMode = ThresholdMode.FIXED;
		settings.hungerThreshold = 17;

		assertTrue(eatsAt(16, STEAK), "sabit modda 16 < 17 yemeye yeter, puan boşa gitse de");
	}

	@Test
	void automaticWithNothingToEatDoesNothing() {
		settings.thresholdMode = ThresholdMode.AUTO;
		settings.fallback = Fallback.NOTHING;

		assertFalse(eatsAt(2, BREAD));
	}

	/** The bite that started keeps going; the line is not asked again halfway through. */
	@Test
	void anAutomaticMealThatStartedIsSeenThrough() {
		settings.thresholdMode = ThresholdMode.AUTO;
		assertTrue(eatsAt(12, STEAK));

		client.usingItem = true;
		autoEat.tick(client);

		assertTrue(client.useKeyHeld);
	}

	// --- hurt: eat now, so healing does not stop ---

	@Test
	void lowHealthEatsWithoutWaitingForHunger() {
		settings.eatEarlyWhenHurt = true;
		settings.earlyEatingHealth = 14;
		client.health = 10;

		assertTrue(eatsAt(19, BREAD));
	}

	@Test
	void healthAtTheLineIsNotLowEnough() {
		settings.eatEarlyWhenHurt = true;
		settings.earlyEatingHealth = 14;
		client.health = 14;

		assertFalse(eatsAt(19, BREAD));
	}

	@Test
	void aFullBarIsLeftAloneHoweverLowHealthIs() {
		settings.eatEarlyWhenHurt = true;
		client.health = 2;

		assertFalse(eatsAt(20, BREAD));
	}

	@Test
	void withTheRuleOffLowHealthWaitsForHunger() {
		settings.eatEarlyWhenHurt = false;
		client.health = 2;

		assertFalse(eatsAt(19, BREAD));
	}

	@Test
	void lowHealthDoesNotWaitForTheAutomaticLineEither() {
		settings.thresholdMode = ThresholdMode.AUTO;
		settings.eatEarlyWhenHurt = true;
		client.health = 10;

		assertTrue(eatsAt(19, STEAK));
	}

	@Test
	void theEarlyRuleNeverSpendsAGoldenApple() {
		settings.eatEarlyWhenHurt = true;
		client.health = 10;

		assertFalse(eatsAt(19, GOLDEN_APPLE));
	}

	@Test
	void theEarlyRuleRespectsTheBlockList() {
		settings.eatEarlyWhenHurt = true;
		settings.blocked = List.of("minecraft:bread");
		client.health = 10;

		assertFalse(eatsAt(19, BREAD));
	}

	/** Both rules hold: the golden apple one is the stronger medicine and goes first. */
	@Test
	void theGoldenAppleRuleGoesFirstWhenItsLineIsCrossed() {
		settings.eatEarlyWhenHurt = true;
		settings.earlyEatingHealth = 14;
		settings.goldenApple = true;
		settings.healthThreshold = 8;
		client.health = 6;

		assertTrue(eatsAt(19, BREAD, GOLDEN_APPLE));
		assertEquals(List.of(GOLDEN_APPLE.slot()), client.slotSelections);
	}

	/** Only the early rule's line is crossed: ordinary food, the apple stays put. */
	@Test
	void aboveTheAppleLineTheEarlyRuleEatsOrdinaryFood() {
		settings.eatEarlyWhenHurt = true;
		settings.earlyEatingHealth = 14;
		settings.goldenApple = true;
		settings.healthThreshold = 8;
		client.health = 10;

		assertTrue(eatsAt(19, BREAD, GOLDEN_APPLE));
		assertEquals(List.of(BREAD.slot()), client.slotSelections);
	}
}
