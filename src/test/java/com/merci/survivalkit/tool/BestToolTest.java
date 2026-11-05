package com.merci.survivalkit.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The shared "best tool" rule, on its own. Speeds are the vanilla ones: wood 2, stone 4,
 * iron 6, diamond 8, netherite 9, gold 12, and Efficiency adds level² + 1.
 */
class BestToolTest {
	private static final int THRESHOLD = 10;

	private static final BlockTarget ORE = new BlockTarget(true, false, false);
	private static final BlockTarget ORE_SILK = new BlockTarget(true, true, false);
	private static final BlockTarget ORE_FORTUNE = new BlockTarget(true, false, true);
	private static final BlockTarget DIRT = new BlockTarget(false, false, false);
	private static final BlockTarget GLASS_SILK = new BlockTarget(false, true, false);

	private static ToolSlot tool(int slot, boolean harvests, float speed) {
		return new ToolSlot(slot, harvests, speed, 0, 0, ToolSlot.UNBREAKABLE);
	}

	private static Optional<ToolSlot> choose(BlockTarget block, ToolSlot... tools) {
		return BestTool.choose(List.of(tools), block, THRESHOLD);
	}

	@Test
	void nothingInReachYieldsNothing() {
		assertTrue(choose(ORE).isEmpty());
	}

	// --- drops first ---

	@Test
	void aToolThatCannotDropTheOreIsNeverChosenOverOneThatCan() {
		ToolSlot stonePickaxe = tool(0, false, 4);
		ToolSlot ironPickaxe = tool(1, true, 6);
		ToolSlot efficientStonePickaxe = tool(2, false, 4 + 26);

		assertEquals(ironPickaxe, choose(ORE, stonePickaxe, efficientStonePickaxe, ironPickaxe).orElseThrow());
	}

	@Test
	void whenNothingCanDropTheOreNothingIsChosen() {
		ToolSlot woodenPickaxe = tool(0, false, 2);
		ToolSlot stonePickaxe = tool(1, false, 4);

		assertTrue(choose(ORE, woodenPickaxe, stonePickaxe).isEmpty());
	}

	// --- then speed ---

	@Test
	void amongToolsThatDropTheFastestWins() {
		ToolSlot ironPickaxe = tool(0, true, 6);
		ToolSlot diamondPickaxe = tool(1, true, 8);
		ToolSlot netheritePickaxe = tool(2, true, 9);

		assertEquals(netheritePickaxe, choose(ORE, ironPickaxe, netheritePickaxe, diamondPickaxe).orElseThrow());
	}

	@Test
	void efficiencyCountsTowardsSpeed() {
		ToolSlot diamondPickaxe = tool(0, true, 8);
		ToolSlot efficientIronPickaxe = tool(1, true, 6 + 10);

		assertEquals(efficientIronPickaxe, choose(ORE, diamondPickaxe, efficientIronPickaxe).orElseThrow());
	}

	@Test
	void onABlockAnyToolDropsTheFastestWins() {
		ToolSlot pickaxe = tool(0, true, 1);
		ToolSlot shovel = tool(1, true, 6);

		assertEquals(shovel, choose(DIRT, pickaxe, shovel).orElseThrow());
	}

	@Test
	void aToolNoBetterThanTheBareHandIsNotChosen() {
		ToolSlot sword = tool(0, true, 1);
		ToolSlot stick = tool(1, true, 1);

		assertTrue(choose(DIRT, sword, stick).isEmpty());
	}

	@Test
	void equalToolsResolveToTheLowerSlot() {
		ToolSlot first = tool(3, true, 8);
		ToolSlot second = tool(7, true, 8);

		assertEquals(first, choose(ORE, second, first).orElseThrow());
	}

	// --- enchantment preferences ---

	@Test
	void onABlockThatWantsSilkTouchASlowerSilkToolBeatsAFasterPlainOne() {
		ToolSlot diamondPickaxe = tool(0, true, 8);
		ToolSlot silkIronPickaxe = new ToolSlot(1, true, 6, 1, 0, ToolSlot.UNBREAKABLE);

		assertEquals(silkIronPickaxe, choose(ORE_SILK, diamondPickaxe, silkIronPickaxe).orElseThrow());
	}

	@Test
	void onABlockThatDoesNotWantSilkTouchSpeedDecides() {
		ToolSlot diamondPickaxe = tool(0, true, 8);
		ToolSlot silkIronPickaxe = new ToolSlot(1, true, 6, 1, 0, ToolSlot.UNBREAKABLE);

		assertEquals(diamondPickaxe, choose(ORE, diamondPickaxe, silkIronPickaxe).orElseThrow());
	}

	@Test
	void silkTouchStillHasToDropTheBlock() {
		ToolSlot silkStonePickaxe = new ToolSlot(0, false, 4, 1, 0, ToolSlot.UNBREAKABLE);
		ToolSlot ironPickaxe = tool(1, true, 6);

		assertEquals(ironPickaxe, choose(ORE_SILK, silkStonePickaxe, ironPickaxe).orElseThrow());
	}

	@Test
	void aSilkToolIsWorthTakingEvenWhenItIsNoFasterThanTheHand() {
		ToolSlot silkPickaxe = new ToolSlot(0, true, 1, 1, 0, ToolSlot.UNBREAKABLE);

		assertEquals(silkPickaxe, choose(GLASS_SILK, silkPickaxe).orElseThrow());
	}

	@Test
	void onABlockThatWantsFortuneTheHigherLevelWinsOverSpeed() {
		ToolSlot netheritePickaxe = tool(0, true, 9);
		ToolSlot fortuneOneDiamond = new ToolSlot(1, true, 8, 0, 1, ToolSlot.UNBREAKABLE);
		ToolSlot fortuneThreeIron = new ToolSlot(2, true, 6, 0, 3, ToolSlot.UNBREAKABLE);

		assertEquals(fortuneThreeIron, choose(ORE_FORTUNE, netheritePickaxe, fortuneOneDiamond, fortuneThreeIron).orElseThrow());
	}

	@Test
	void equalFortuneFallsBackToSpeed() {
		ToolSlot fortuneIron = new ToolSlot(0, true, 6, 0, 3, ToolSlot.UNBREAKABLE);
		ToolSlot fortuneDiamond = new ToolSlot(1, true, 8, 0, 3, ToolSlot.UNBREAKABLE);

		assertEquals(fortuneDiamond, choose(ORE_FORTUNE, fortuneIron, fortuneDiamond).orElseThrow());
	}

	@Test
	void aBlockOnBothListsPrefersSilkTouch() {
		BlockTarget both = new BlockTarget(true, true, true);
		ToolSlot fortunePickaxe = new ToolSlot(0, true, 8, 0, 3, ToolSlot.UNBREAKABLE);
		ToolSlot silkPickaxe = new ToolSlot(1, true, 8, 1, 0, ToolSlot.UNBREAKABLE);

		assertEquals(silkPickaxe, choose(both, fortunePickaxe, silkPickaxe).orElseThrow());
	}

	// --- durability ---

	@Test
	void aToolBelowTheThresholdIsNeverChosen() {
		ToolSlot worn = new ToolSlot(0, true, 9, 0, 0, THRESHOLD - 1);
		ToolSlot ironPickaxe = new ToolSlot(1, true, 6, 0, 0, 200);

		assertEquals(ironPickaxe, choose(ORE, worn, ironPickaxe).orElseThrow());
	}

	@Test
	void aWornToolIsNotChosenEvenWhenItIsTheOnlyOneThatDrops() {
		ToolSlot worn = new ToolSlot(0, true, 9, 0, 0, 1);

		assertTrue(choose(ORE, worn).isEmpty());
	}

	@Test
	void aToolExactlyAtTheThresholdIsStillUsable() {
		ToolSlot atThreshold = new ToolSlot(0, true, 9, 0, 0, THRESHOLD);

		assertEquals(atThreshold, choose(ORE, atThreshold).orElseThrow());
	}

	@Test
	void aWornToolIsNotChosenForItsEnchantmentEither() {
		ToolSlot wornSilk = new ToolSlot(0, true, 8, 1, 0, 3);
		ToolSlot ironPickaxe = new ToolSlot(1, true, 6, 0, 0, 200);

		assertEquals(ironPickaxe, choose(ORE_SILK, wornSilk, ironPickaxe).orElseThrow());
	}

	@Test
	void unbreakableItemsIgnoreTheThreshold() {
		ToolSlot unbreakable = tool(0, true, 8);

		assertEquals(unbreakable, BestTool.choose(List.of(unbreakable), ORE, Integer.MAX_VALUE - 1).orElseThrow());
	}
}
