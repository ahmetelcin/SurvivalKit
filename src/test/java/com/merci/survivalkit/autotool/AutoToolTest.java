package com.merci.survivalkit.autotool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.autotool.FakeToolContext.Item;
import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.tool.ToolSlot;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Otomatik Alet's state machine: when it switches, when it gives the hand back, and that
 * it does both exactly once for a run of blocks.
 */
class AutoToolTest {
	private static final int BREAD_SLOT = 0;
	private static final int PICKAXE_SLOT = 2;
	private static final int SHOVEL_SLOT = 3;
	private static final int BACKPACK_SLOT = 20;

	private final FakeToolSettings settings = new FakeToolSettings();
	private final Hands hands = new Hands();
	private final AutoTool autoTool = new AutoTool(settings, hands);
	private final FakeToolContext client = new FakeToolContext();

	@BeforeEach
	void aPlayerHoldingBreadWithAPickaxeInTheHotbar() {
		client.inventory.put(BREAD_SLOT, Item.plain("bread"));
		client.inventory.put(PICKAXE_SLOT, Item.tool("pickaxe", 8));
		client.selected = BREAD_SLOT;
		// Most of these start from bread in hand to watch the switching itself; "only with a
		// tool in hand" is on by default and has its own tests below.
		settings.onlyWithToolInHand = false;
	}

	// --- only with a tool in hand ---

	@Test
	void withOnlyWithToolOnBreadInHandIsLeftAlone() {
		settings.onlyWithToolInHand = true;
		client.startBreaking();
		ticks(5);

		assertEquals("bread", client.held());
		assertEquals(List.of(), client.selections);
		assertFalse(hands.holds(autoTool));
	}

	@Test
	void withOnlyWithToolAnEmptyHandIsLeftAlone() {
		settings.onlyWithToolInHand = true;
		client.inventory.remove(BREAD_SLOT);
		client.startBreaking();
		ticks(5);

		assertEquals(List.of(), client.selections);
	}

	@Test
	void withOnlyWithToolAToolInHandStillMovesToTheRightOne() {
		settings.onlyWithToolInHand = true;
		client.target = Optional.of(new MiningTarget("minecraft:oak_log", false));
		client.inventory.put(BREAD_SLOT, new Item("pickaxe", true, 1, 0, 0, ToolSlot.UNBREAKABLE, false, true));
		client.inventory.put(PICKAXE_SLOT, Item.tool("axe", 6));
		client.startBreaking();
		ticks(1);

		assertEquals("axe", client.held());
	}

	@Test
	void withOnlyWithToolTheRunCarriesOnFromTheToolItTookOut() {
		// Out with the pickaxe, which is a tool: the next block's tool still comes out, and
		// the item the run began with is what goes back.
		settings.onlyWithToolInHand = true;
		client.inventory.put(BREAD_SLOT, new Item("shovel", true, 1, 0, 0, ToolSlot.UNBREAKABLE, false, true));
		client.startBreaking();
		ticks(2);

		assertEquals("pickaxe", client.held());
	}

	@Test
	void theSettingIsOnByDefault() {
		assertTrue(new FakeToolSettings().onlyWithToolInHand);
	}

	private void ticks(int count) {
		for (int i = 0; i < count; i++) {
			autoTool.tick(client);
		}
	}

	/** Breaks one block: a few ticks of breaking, then the short pause before the next. */
	private void breakOneBlock() {
		client.breaking = true;
		ticks(4);
		client.breaking = false;
		ticks(5);
	}

	// --- switching ---

	@Test
	void nothingHappensWhileNothingIsBeingBroken() {
		ticks(10);

		assertEquals(List.of(), client.selections);
		assertFalse(hands.holds(autoTool));
	}

	@Test
	void startingToBreakABlockSwitchesToTheBestTool() {
		client.startBreaking();
		ticks(1);

		assertEquals("pickaxe", client.held());
	}

	@Test
	void theHandIsClaimedWhileTheToolIsOut() {
		client.startBreaking();
		ticks(1);

		assertTrue(hands.holds(autoTool));
	}

	@Test
	void alreadyHoldingTheBestToolChangesNothing() {
		client.selected = PICKAXE_SLOT;
		client.startBreaking();
		ticks(5);

		assertEquals(List.of(), client.selections);
		assertFalse(hands.holds(autoTool));
	}

	@Test
	void nothingIsTakenWhenNothingBeatsTheHand() {
		client.inventory.remove(PICKAXE_SLOT);
		client.startBreaking();
		ticks(5);

		assertEquals(List.of(), client.selections);
	}

	@Test
	void theBlockListsAreHonoured() {
		client.inventory.put(SHOVEL_SLOT, new Item("silk shovel", true, 1, 1, 0, ToolSlot.UNBREAKABLE, false));
		client.target = Optional.of(new MiningTarget("minecraft:glass", false));
		client.inventory.put(PICKAXE_SLOT, Item.plain("pickaxe"));
		settings.silkTouchBlocks = List.of("minecraft:glass");
		client.startBreaking();
		ticks(1);

		assertEquals("silk shovel", client.held());
	}

	@Test
	void theFortuneListIsHonoured() {
		client.inventory.put(SHOVEL_SLOT, new Item("fortune pickaxe", true, 6, 0, 3, ToolSlot.UNBREAKABLE, false));
		client.target = Optional.of(new MiningTarget("minecraft:diamond_ore", true));
		settings.fortuneBlocks = List.of("minecraft:diamond_ore");
		client.startBreaking();
		ticks(1);

		assertEquals("fortune pickaxe", client.held());
	}

	@Test
	void theDurabilityThresholdIsHonoured() {
		client.inventory.put(PICKAXE_SLOT, new Item("worn pickaxe", true, 8, 0, 0, 3, false));
		client.inventory.put(SHOVEL_SLOT, new Item("fresh pickaxe", true, 6, 0, 0, 200, false));
		settings.durabilityThreshold = 10;
		client.startBreaking();
		ticks(1);

		assertEquals("fresh pickaxe", client.held());
	}

	// --- one switch for a run of blocks ---

	@Test
	void breakingThreeBlocksInARowSwitchesOnceAndGivesBackOnce() {
		client.attackHeld = true;
		breakOneBlock();
		breakOneBlock();
		breakOneBlock();
		client.releaseAttack();
		ticks(1);

		assertEquals(List.of(PICKAXE_SLOT, BREAD_SLOT), client.selections);
	}

	@Test
	void theToolStaysOutWhileAttackIsHeldBetweenBlocks() {
		client.attackHeld = true;
		breakOneBlock();

		assertEquals("pickaxe", client.held());
	}

	@Test
	void lettingGoOfAttackPutsThePreviousItemBack() {
		client.startBreaking();
		ticks(3);
		client.releaseAttack();
		ticks(1);

		assertEquals("bread", client.held());
		assertFalse(hands.holds(autoTool));
	}

	@Test
	void holdingAttackWithNothingToBreakGivesItBackAfterAShortWhile() {
		client.startBreaking();
		ticks(3);
		client.breaking = false;

		ticks(AutoTool.IDLE_TICKS - 1);
		assertEquals("pickaxe", client.held());

		ticks(1);
		assertEquals("bread", client.held());
	}

	@Test
	void aDifferentBlockMovesStraightToItsOwnToolAndBackToTheFirstItemAtTheEnd() {
		// Against stone only the pickaxe is quick.
		client.inventory.put(SHOVEL_SLOT, new Item("shovel", false, 1, 0, 0, ToolSlot.UNBREAKABLE, false));
		client.startBreaking();
		ticks(2);

		// Against dirt only the shovel is.
		client.target = Optional.of(new MiningTarget("minecraft:dirt", false));
		client.inventory.put(SHOVEL_SLOT, Item.tool("shovel", 8));
		client.inventory.put(PICKAXE_SLOT, Item.plain("pickaxe"));
		ticks(2);
		client.releaseAttack();
		ticks(1);

		assertEquals(List.of(PICKAXE_SLOT, SHOVEL_SLOT, BREAD_SLOT), client.selections);
	}

	@Test
	void withRestoreOffTheToolStaysAndTheHandIsLetGo() {
		settings.restorePrevious = false;
		client.startBreaking();
		ticks(3);
		client.releaseAttack();
		ticks(5);

		assertEquals("pickaxe", client.held());
		assertEquals(List.of(PICKAXE_SLOT), client.selections);
		assertFalse(hands.holds(autoTool));
	}

	// --- the backpack ---

	@Test
	void aToolInTheBackpackIsSwappedIntoTheHandAndSwappedBackAfter() {
		client.inventory.remove(PICKAXE_SLOT);
		client.inventory.put(BACKPACK_SLOT, Item.tool("pickaxe", 8));
		client.startBreaking();
		ticks(3);

		assertEquals("pickaxe", client.held());
		assertEquals(List.of(), client.selections);

		client.releaseAttack();
		ticks(1);

		assertEquals("bread", client.held());
		assertEquals("pickaxe", client.inventory.get(BACKPACK_SLOT).name());
		assertEquals(List.of(BACKPACK_SLOT + "->" + BREAD_SLOT, BACKPACK_SLOT + "->" + BREAD_SLOT), client.swaps);
	}

	@Test
	void aBackpackSwapAlsoClaimsTheInventory() {
		client.inventory.remove(PICKAXE_SLOT);
		client.inventory.put(BACKPACK_SLOT, Item.tool("pickaxe", 8));
		client.startBreaking();
		ticks(1);

		assertTrue(hands.taken().contains(Hands.Claim.INVENTORY));
	}

	// --- when it keeps out of the way ---

	@Test
	void aSwordInHandIsLeftAloneByDefault() {
		client.inventory.put(BREAD_SLOT, Item.sword("sword"));
		client.startBreaking();
		ticks(5);

		assertEquals("sword", client.held());
		assertEquals(List.of(), client.selections);
	}

	@Test
	void withKeepSwordOffTheSwordIsPutAwayToo() {
		settings.keepSword = false;
		client.inventory.put(BREAD_SLOT, Item.sword("sword"));
		client.startBreaking();
		ticks(1);

		assertEquals("pickaxe", client.held());
	}

	@Test
	void aSilkTouchToolInHandThatDropsTheBlockIsLeftThere() {
		client.inventory.put(BREAD_SLOT, new Item("silk pickaxe", true, 6, 1, 0, ToolSlot.UNBREAKABLE, false));
		client.startBreaking();
		ticks(5);

		assertEquals("silk pickaxe", client.held());
		assertEquals(List.of(), client.selections);
	}

	@Test
	void aSilkTouchPickaxeIsPutAwayOnABlockItIsNotTheToolFor() {
		// Dirt drops to anything, the pickaxe included, but the pickaxe is not its tool: Silk
		// Touch on it is not why anyone is holding it here. This used to keep the pickaxe,
		// which left Otomatik Alet stuck on it for wood, dirt, wool and hay alike.
		client.target = Optional.of(new MiningTarget("minecraft:dirt", false));
		client.inventory.put(BREAD_SLOT, new Item("silk pickaxe", false, 1, 1, 0, ToolSlot.UNBREAKABLE, false));
		client.inventory.put(PICKAXE_SLOT, Item.tool("shovel", 8));
		client.startBreaking();
		ticks(1);

		assertEquals("shovel", client.held());
	}

	@Test
	void aSilkTouchShovelIsLeftOnABlockItIsTheToolFor() {
		// Grass taken as grass: the shovel is grass's own tool, and Silk Touch is the point.
		client.target = Optional.of(new MiningTarget("minecraft:grass_block", false));
		client.inventory.put(BREAD_SLOT, new Item("silk shovel", true, 6, 1, 0, ToolSlot.UNBREAKABLE, false));
		client.inventory.put(PICKAXE_SLOT, Item.tool("faster shovel", 8));
		client.startBreaking();
		ticks(5);

		assertEquals("silk shovel", client.held());
	}

	@Test
	void aSilkTouchToolThatCannotDropTheBlockIsSwappedOut() {
		client.inventory.put(BREAD_SLOT, new Item("silk shovel", false, 1, 1, 0, ToolSlot.UNBREAKABLE, false));
		client.startBreaking();
		ticks(1);

		assertEquals("pickaxe", client.held());
	}

	@Test
	void withKeepSilkTouchOffTheFasterToolIsTaken() {
		settings.keepSilkTouch = false;
		client.inventory.put(BREAD_SLOT, new Item("silk pickaxe", true, 6, 1, 0, ToolSlot.UNBREAKABLE, false));
		client.startBreaking();
		ticks(1);

		assertEquals("pickaxe", client.held());
	}

	@Test
	void nothingHappensInCreative() {
		client.creative = true;
		client.startBreaking();
		ticks(5);

		assertEquals(List.of(), client.selections);
		assertFalse(hands.holds(autoTool));
	}

	@Test
	void switchingToCreativeMidwayGivesTheItemBack() {
		client.startBreaking();
		ticks(2);
		client.creative = true;
		ticks(1);

		assertEquals("bread", client.held());
		assertFalse(hands.holds(autoTool));
	}

	@Test
	void nothingHappensWhileNotReady() {
		client.ready = false;
		client.startBreaking();
		ticks(5);

		assertEquals(List.of(), client.selections);
	}

	@Test
	void stoppingTheModuleGivesTheItemBack() {
		client.startBreaking();
		ticks(2);
		autoTool.stop(client);

		assertEquals("bread", client.held());
		assertFalse(hands.holds(autoTool));
	}

	// --- the hand lock ---

	@Test
	void nothingMovesWhileSomethingMoreUrgentHoldsTheHand() {
		hands.take(() -> { }, Hands.Priority.AUTO_EAT, Hands.Claim.MAIN_HAND);
		client.startBreaking();
		ticks(5);

		assertEquals(List.of(), client.selections);
	}

	@Test
	void anInterruptionPutsEverythingBack() {
		client.inventory.remove(PICKAXE_SLOT);
		client.inventory.put(BACKPACK_SLOT, Item.tool("pickaxe", 8));
		client.startBreaking();
		ticks(2);

		hands.take(() -> { }, Hands.Priority.AUTO_EAT, Hands.Claim.MAIN_HAND);

		assertEquals("bread", client.held());
		assertEquals("pickaxe", client.inventory.get(BACKPACK_SLOT).name());
		assertFalse(hands.holds(autoTool));
	}

	@Test
	void afterAnInterruptionItDoesNotPutTheItemBackASecondTime() {
		client.startBreaking();
		ticks(2);
		hands.take(() -> { }, Hands.Priority.AUTO_EAT, Hands.Claim.MAIN_HAND);
		int afterInterruption = client.selections.size();

		client.releaseAttack();
		ticks(3);

		assertEquals(afterInterruption, client.selections.size());
	}
}
