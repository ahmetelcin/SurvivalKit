package com.merci.survivalkit.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.util.ItemPriorityResolver.Fallback;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The chooser, on its own. Real vanilla numbers are used so the trade-offs the modes are
 * meant to express stay recognisable: beef fills the most, golden carrot lasts the longest.
 */
class ItemPriorityResolverTest {
	private static final FoodSlot BREAD = new FoodSlot(0, "minecraft:bread", 5, 6.0f, false);
	private static final FoodSlot BEEF = new FoodSlot(1, "minecraft:cooked_beef", 8, 12.8f, false);
	private static final FoodSlot GOLDEN_CARROT = new FoodSlot(2, "minecraft:golden_carrot", 6, 14.4f, false);
	private static final FoodSlot COOKIE = new FoodSlot(3, "minecraft:cookie", 2, 0.4f, false);
	private static final FoodSlot ROTTEN_FLESH = new FoodSlot(4, "minecraft:rotten_flesh", 4, 0.8f, true);

	private static final List<FoodSlot> PANTRY = List.of(BREAD, BEEF, GOLDEN_CARROT, COOKIE, ROTTEN_FLESH);

	private static Optional<FoodSlot> choose(List<FoodSlot> available, List<String> priority, List<String> blocked, Fallback fallback) {
		return ItemPriorityResolver.choose(available, priority, blocked, fallback);
	}

	private static Optional<FoodSlot> withFallback(Fallback fallback) {
		return choose(PANTRY, List.of(), List.of(), fallback);
	}

	@Test
	void anEmptyInventoryYieldsNothing() {
		assertTrue(choose(List.of(), List.of("minecraft:bread"), List.of(), Fallback.HIGHEST_NUTRITION).isEmpty());
	}

	// --- priority list ---

	@Test
	void theFirstEntryOnTheListWinsOverAMoreFillingItem() {
		assertEquals(BREAD, choose(PANTRY, List.of("minecraft:bread", "minecraft:cooked_beef"), List.of(), Fallback.HIGHEST_NUTRITION).orElseThrow());
	}

	@Test
	void theSecondEntryIsUsedWhenTheFirstIsNotInReach() {
		List<FoodSlot> available = List.of(BEEF, COOKIE);

		assertEquals(BEEF, choose(available, List.of("minecraft:bread", "minecraft:cooked_beef"), List.of(), Fallback.HIGHEST_NUTRITION).orElseThrow());
	}

	@Test
	void anEntryThatIsNotInReachIsSimplySkipped() {
		assertEquals(COOKIE, choose(PANTRY, List.of("minecraft:enchanted_golden_apple", "minecraft:cookie"), List.of(), Fallback.HIGHEST_NUTRITION).orElseThrow());
	}

	@Test
	void aListedRiskyItemBeatsAnUnlistedSafeOne() {
		assertEquals(ROTTEN_FLESH, choose(PANTRY, List.of("minecraft:rotten_flesh"), List.of(), Fallback.HIGHEST_NUTRITION).orElseThrow());
	}

	@Test
	void theSameItemInTwoSlotsResolvesToTheLowerSlot() {
		FoodSlot higher = new FoodSlot(7, "minecraft:bread", 5, 6.0f, false);
		List<FoodSlot> available = List.of(higher, BREAD);

		assertEquals(BREAD, choose(available, List.of("minecraft:bread"), List.of(), Fallback.HIGHEST_NUTRITION).orElseThrow());
	}

	@Test
	void theFallbackIsNotConsultedWhileTheListMatches() {
		assertEquals(COOKIE, choose(PANTRY, List.of("minecraft:cookie"), List.of(), Fallback.NOTHING).orElseThrow());
	}

	// --- block list ---

	@Test
	void aBlockedItemIsNeverChosenByTheFallback() {
		assertEquals(GOLDEN_CARROT, choose(PANTRY, List.of(), List.of("minecraft:cooked_beef"), Fallback.HIGHEST_NUTRITION).orElseThrow());
	}

	@Test
	void blockingBeatsThePriorityList() {
		assertEquals(BEEF, choose(PANTRY, List.of("minecraft:bread"), List.of("minecraft:bread"), Fallback.HIGHEST_NUTRITION).orElseThrow());
	}

	@Test
	void blockingEverythingEdibleYieldsNothing() {
		List<String> everything = PANTRY.stream().map(FoodSlot::id).toList();

		assertTrue(choose(PANTRY, List.of(), everything, Fallback.HIGHEST_NUTRITION).isEmpty());
	}

	@Test
	void blockingAnItemThatIsNotThereChangesNothing() {
		assertEquals(BEEF, choose(PANTRY, List.of(), List.of("minecraft:pufferfish"), Fallback.HIGHEST_NUTRITION).orElseThrow());
	}

	// --- fallback modes ---

	@Test
	void highestNutritionFillsTheBarFastest() {
		assertEquals(BEEF, withFallback(Fallback.HIGHEST_NUTRITION).orElseThrow());
	}

	@Test
	void highestSaturationPrefersTheLongerLastingItem() {
		assertEquals(GOLDEN_CARROT, withFallback(Fallback.HIGHEST_SATURATION).orElseThrow());
	}

	@Test
	void lowestValueFirstBurnsTheJunk() {
		assertEquals(COOKIE, withFallback(Fallback.LOWEST_VALUE_FIRST).orElseThrow());
	}

	@Test
	void nothingEatsNothingItWasNotToldAbout() {
		assertTrue(withFallback(Fallback.NOTHING).isEmpty());
	}

	@Test
	void everyModeKeepsRiskyFoodForLast() {
		for (Fallback fallback : List.of(Fallback.HIGHEST_NUTRITION, Fallback.HIGHEST_SATURATION, Fallback.LOWEST_VALUE_FIRST)) {
			FoodSlot chosen = choose(List.of(ROTTEN_FLESH, COOKIE), List.of(), List.of(), fallback).orElseThrow();

			assertEquals(COOKIE, chosen, "risky yiyecek " + fallback + " modunda öne geçti");
		}
	}

	@Test
	void riskyFoodIsStillBetterThanStarving() {
		assertEquals(ROTTEN_FLESH, choose(List.of(ROTTEN_FLESH), List.of(), List.of(), Fallback.HIGHEST_NUTRITION).orElseThrow());
	}

	@Test
	void saturationBreaksATieOnNutrition() {
		FoodSlot dry = new FoodSlot(5, "minecraft:melon_slice", 5, 1.2f, false);

		assertEquals(BREAD, choose(List.of(dry, BREAD), List.of(), List.of(), Fallback.HIGHEST_NUTRITION).orElseThrow());
	}

	@Test
	void theLowerSlotBreaksATieOnEverythingElse() {
		FoodSlot copy = new FoodSlot(6, "minecraft:baked_potato", 5, 6.0f, false);

		assertEquals(BREAD, choose(List.of(copy, BREAD), List.of(), List.of(), Fallback.HIGHEST_NUTRITION).orElseThrow());
	}

	// --- reserved: out of the fallback's reach, still on the list's ---

	private static final FoodSlot GOLDEN_APPLE = new FoodSlot(5, "minecraft:golden_apple", 4, 9.6f, false);
	private static final List<String> APPLES = List.of("minecraft:golden_apple");

	@Test
	void theFallbackWillNotSpendAReservedItem() {
		List<FoodSlot> available = List.of(GOLDEN_APPLE, COOKIE);

		assertEquals(COOKIE, ItemPriorityResolver.choose(available, List.of(), List.of(), APPLES, Fallback.HIGHEST_NUTRITION).orElseThrow());
	}

	@Test
	void aReservedItemIsNotEatenEvenWhenItIsAllThereIs() {
		List<FoodSlot> available = List.of(GOLDEN_APPLE);

		assertTrue(ItemPriorityResolver.choose(available, List.of(), List.of(), APPLES, Fallback.HIGHEST_NUTRITION).isEmpty());
	}

	@Test
	void thePriorityListStillReachesAReservedItem() {
		List<FoodSlot> available = List.of(GOLDEN_APPLE, COOKIE);

		assertEquals(GOLDEN_APPLE, ItemPriorityResolver.choose(available, APPLES, List.of(), APPLES, Fallback.HIGHEST_NUTRITION).orElseThrow());
	}

	@Test
	void blockingStillBeatsTheListForAReservedItem() {
		List<FoodSlot> available = List.of(GOLDEN_APPLE, COOKIE);

		assertEquals(COOKIE, ItemPriorityResolver.choose(available, APPLES, APPLES, APPLES, Fallback.HIGHEST_NUTRITION).orElseThrow());
	}

	@Test
	void reservingNothingIsTheSameAsTheShorterCall() {
		assertEquals(
			ItemPriorityResolver.choose(PANTRY, List.of(), List.of(), Fallback.HIGHEST_NUTRITION),
			ItemPriorityResolver.choose(PANTRY, List.of(), List.of(), List.of(), Fallback.HIGHEST_NUTRITION));
	}
}
