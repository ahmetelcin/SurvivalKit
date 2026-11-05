package com.merci.survivalkit.scaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.scaffold.BlockChoice.Fallback;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Which stack Scaffold builds with. */
class BlockChoiceTest {
	private static final String STONE = "minecraft:stone";
	private static final String DIRT = "minecraft:dirt";
	private static final String COBBLE = "minecraft:cobblestone";
	private static final String SAND = "minecraft:sand";

	private static BlockStack stack(int slot, String id, int count) {
		return new BlockStack(slot, id, count, 64, true);
	}

	private static Optional<BlockStack> choose(List<String> preferred, boolean full, Fallback fallback, int selected, BlockStack... stacks) {
		return BlockChoice.choose(List.of(stacks), preferred, BlockChoice.BLOCKED_BY_DEFAULT, full, fallback, selected);
	}

	@Test
	void nothingInReachYieldsNothing() {
		assertTrue(choose(List.of(STONE), false, Fallback.LARGEST_STACK, 0).isEmpty());
	}

	// --- the list ---

	@Test
	void theListsOrderBeatsAnyFallback() {
		BlockStack dirt = stack(0, DIRT, 64);
		BlockStack cobble = stack(1, COBBLE, 3);

		assertEquals(cobble, choose(List.of(COBBLE, DIRT), false, Fallback.LARGEST_STACK, 0, dirt, cobble).orElseThrow());
	}

	@Test
	void theEarliestEntryInReachWins() {
		BlockStack dirt = stack(4, DIRT, 10);

		assertEquals(dirt, choose(List.of(COBBLE, DIRT), false, Fallback.NOTHING, 0, dirt).orElseThrow());
	}

	// --- the blocked list ---

	@Test
	void blockedBlocksAreNeverChosenEvenWhenListed() {
		BlockStack sand = stack(0, SAND, 64);

		assertTrue(choose(List.of(SAND), false, Fallback.LARGEST_STACK, 0, sand).isEmpty());
	}

	@Test
	void theDefaultBlockedListHoldsFallingBlocksTntAndContainers() {
		for (String id : List.of("minecraft:sand", "minecraft:red_sand", "minecraft:gravel", "minecraft:white_concrete_powder",
			"minecraft:black_concrete_powder", "minecraft:anvil", "minecraft:tnt", "minecraft:chest", "minecraft:trapped_chest",
			"minecraft:barrel", "minecraft:ender_chest", "minecraft:shulker_box", "minecraft:red_shulker_box", "minecraft:hopper")) {
			assertTrue(BlockChoice.BLOCKED_BY_DEFAULT.contains(id), id);
		}
	}

	@Test
	void aBlockOffTheBlockedListIsUsedAgain() {
		BlockStack sand = stack(0, SAND, 64);

		assertEquals(sand, BlockChoice.choose(List.of(sand), List.of(), List.of(), false, Fallback.LARGEST_STACK, 0).orElseThrow());
	}

	@Test
	void whatIsNoWholeBlockIsNeverChosen() {
		BlockStack slab = new BlockStack(0, "minecraft:stone_slab", 64, 64, false);

		assertTrue(choose(List.of("minecraft:stone_slab"), false, Fallback.LARGEST_STACK, 0, slab).isEmpty());
	}

	// --- full stacks ---

	@Test
	void withFullStackPreferredAFullStackGoesFirst() {
		BlockStack part = stack(0, STONE, 20);
		BlockStack full = stack(5, STONE, 64);

		assertEquals(full, choose(List.of(STONE), true, Fallback.NOTHING, 0, part, full).orElseThrow());
		assertEquals(part, choose(List.of(STONE), false, Fallback.NOTHING, 0, part, full).orElseThrow());
	}

	@Test
	void aFullStackDoesNotJumpTheListsOrder() {
		BlockStack cobble = stack(0, COBBLE, 2);
		BlockStack stone = stack(1, STONE, 64);

		assertEquals(cobble, choose(List.of(COBBLE, STONE), true, Fallback.NOTHING, 0, cobble, stone).orElseThrow());
	}

	// --- fallback ---

	@Test
	void largestStackFallsBackToTheBiggest() {
		BlockStack dirt = stack(0, DIRT, 12);
		BlockStack stone = stack(7, STONE, 40);

		assertEquals(stone, choose(List.of(), false, Fallback.LARGEST_STACK, 0, dirt, stone).orElseThrow());
	}

	@Test
	void nearestFallsBackToWhatIsInHand() {
		BlockStack dirt = stack(0, DIRT, 12);
		BlockStack stone = stack(3, STONE, 40);

		assertEquals(stone, choose(List.of(), false, Fallback.NEAREST, 3, dirt, stone).orElseThrow());
	}

	@Test
	void nearestPrefersTheHotbarToTheBackpack() {
		BlockStack backpack = stack(9, STONE, 64);
		BlockStack hotbar = stack(8, DIRT, 1);

		assertEquals(hotbar, choose(List.of(), false, Fallback.NEAREST, 0, backpack, hotbar).orElseThrow());
	}

	@Test
	void nothingFallsBackToNothing() {
		assertTrue(choose(List.of(COBBLE), false, Fallback.NOTHING, 0, stack(0, DIRT, 64)).isEmpty());
	}

	@Test
	void betweenEqualStacksTheOneInHandStays() {
		BlockStack first = stack(0, STONE, 64);
		BlockStack inHand = stack(4, STONE, 64);

		assertEquals(inHand, choose(List.of(STONE), true, Fallback.NOTHING, 4, first, inHand).orElseThrow());
	}

	@Test
	void anEmptyStackIsNotABlock() {
		assertTrue(choose(List.of(STONE), false, Fallback.LARGEST_STACK, 0, stack(0, STONE, 0)).isEmpty());
	}
}
