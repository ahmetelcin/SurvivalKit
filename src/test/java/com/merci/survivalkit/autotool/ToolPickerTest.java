package com.merci.survivalkit.autotool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.tool.ToolSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Alet Seçici: a middle click on a block puts the best tool for it in hand, and whether the
 * click is kept from the game's own pick block.
 *
 * <p>"Left for the game" is what {@link FakePickContext#clicksLeft} counts: a click the picker
 * did not take is one vanilla will act on a moment later.
 */
class ToolPickerTest {
	private static final int BREAD_SLOT = 0;
	private static final int PICKAXE_SLOT = 2;
	private static final int BACKPACK_SLOT = 20;

	private final FakePickSettings settings = new FakePickSettings();
	private final Hands hands = new Hands();
	private final ToolPicker picker = new ToolPicker(settings, hands);
	private final FakePickContext client = new FakePickContext();

	@BeforeEach
	void aPlayerHoldingBreadLookingAtStone() {
		client.tools.put(BREAD_SLOT, new ToolSlot(BREAD_SLOT, false, 1, 0, 0, ToolSlot.UNBREAKABLE));
		client.tools.put(PICKAXE_SLOT, new ToolSlot(PICKAXE_SLOT, true, 6, 0, 0, ToolSlot.UNBREAKABLE));
		client.selected = BREAD_SLOT;
	}

	@Test
	void aMiddleClickPutsTheBestToolInHand() {
		client.clicks = 1;
		picker.tick(client);

		assertEquals(PICKAXE_SLOT, client.selected);
	}

	@Test
	void theClickIsTakenSoTheGameDoesNotPickTheBlock() {
		client.clicks = 1;
		client.blockInInventory = true;
		picker.tick(client);

		assertEquals(0, client.clicksLeft());
	}

	@Test
	void withoutAClickNothingHappens() {
		picker.tick(client);

		assertEquals(List.of(), client.selections);
	}

	@Test
	void aToolInTheBackpackIsSwappedIntoTheHand() {
		client.tools.remove(PICKAXE_SLOT);
		client.tools.put(BACKPACK_SLOT, new ToolSlot(BACKPACK_SLOT, true, 6, 0, 0, ToolSlot.UNBREAKABLE));
		client.clicks = 1;
		picker.tick(client);

		assertEquals(List.of(BACKPACK_SLOT + "->" + BREAD_SLOT), client.swaps);
		assertEquals(List.of(), client.selections);
	}

	@Test
	void theHandIsOnlyHeldForTheMomentOfTheSwitch() {
		client.clicks = 1;
		picker.tick(client);

		assertFalse(hands.holds(picker));
	}

	@Test
	void theBlockListsAndThresholdAreHonoured() {
		client.tools.put(3, new ToolSlot(3, true, 4, 0, 3, 200));
		client.tools.put(4, new ToolSlot(4, true, 9, 0, 3, 2));
		settings.fortuneBlocks = List.of("minecraft:stone");
		client.clicks = 1;
		picker.tick(client);

		assertEquals(3, client.selected);
	}

	// --- when the click is left to the game ---

	@Test
	void withNoToolBetterThanTheHandTheGameGetsTheClick() {
		client.tools.remove(PICKAXE_SLOT);
		client.clicks = 1;
		picker.tick(client);

		assertEquals(1, client.clicksLeft());
		assertEquals(List.of(), client.selections);
	}

	@Test
	void withTheBestToolAlreadyInHandTheClickIsStillTaken() {
		client.selected = PICKAXE_SLOT;
		client.blockInInventory = true;
		client.clicks = 1;
		picker.tick(client);

		assertEquals(0, client.clicksLeft());
		assertEquals(List.of(), client.selections);
	}

	@Test
	void notLookingAtABlockLeavesTheClickToTheGame() {
		client.target = Optional.empty();
		client.clicks = 1;
		picker.tick(client);

		assertEquals(1, client.clicksLeft());
	}

	@Test
	void inCreativeTheGameGetsTheClick() {
		client.creative = true;
		client.clicks = 1;
		picker.tick(client);

		assertEquals(1, client.clicksLeft());
		assertEquals(List.of(), client.selections);
	}

	@Test
	void whileNotReadyTheClickIsLeftAlone() {
		client.ready = false;
		client.clicks = 1;
		picker.tick(client);

		assertEquals(1, client.clicksLeft());
	}

	// --- the three modes ---

	@Test
	void alwaysTakesTheToolEvenWhenTheBlockIsInTheInventory() {
		settings.mode = PickMode.ALWAYS;
		client.blockInInventory = true;
		client.clicks = 1;
		picker.tick(client);

		assertEquals(PICKAXE_SLOT, client.selected);
		assertEquals(0, client.clicksLeft());
	}

	@Test
	void whenNotInInventoryTheBlockInTheInventoryIsLeftToTheGame() {
		settings.mode = PickMode.WHEN_BLOCK_NOT_IN_INVENTORY;
		client.blockInInventory = true;
		client.clicks = 1;
		picker.tick(client);

		assertEquals(1, client.clicksLeft());
		assertEquals(List.of(), client.selections);
	}

	@Test
	void whenNotInInventoryAMissingBlockGetsTheTool() {
		settings.mode = PickMode.WHEN_BLOCK_NOT_IN_INVENTORY;
		client.blockInInventory = false;
		client.clicks = 1;
		picker.tick(client);

		assertEquals(PICKAXE_SLOT, client.selected);
	}

	@Test
	void offLeavesEveryClickToTheGame() {
		settings.mode = PickMode.OFF;
		client.clicks = 1;
		picker.tick(client);

		assertEquals(1, client.clicksLeft());
		assertEquals(List.of(), client.selections);
	}

	// --- the hand lock ---

	@Test
	void aMiddleClickOutranksOtomatikAlet() {
		Hands.Owner autoTool = () -> { };
		hands.take(autoTool, Hands.Priority.AUTO_TOOL, Hands.Claim.MAIN_HAND);
		client.clicks = 1;
		picker.tick(client);

		assertEquals(PICKAXE_SLOT, client.selected);
		assertFalse(hands.holds(autoTool));
	}

	@Test
	void nothingMovesWhileAutoTotemHoldsTheHand() {
		hands.take(() -> { }, Hands.Priority.AUTO_TOTEM, Hands.Claim.MAIN_HAND);
		client.clicks = 1;
		picker.tick(client);

		assertEquals(List.of(), client.selections);
		assertTrue(client.clicksLeft() == 0, "the click is still kept from the game rather than picking the block");
	}

	/** A hand-driven stand-in: a slot-to-measurement map and a click counter. */
	static final class FakePickContext implements PickContext {
		boolean ready = true;
		boolean creative;
		boolean blockInInventory;
		Optional<MiningTarget> target = Optional.of(new MiningTarget("minecraft:stone", true));
		int clicks;
		int selected;
		final Map<Integer, ToolSlot> tools = new TreeMap<>();
		final List<Integer> selections = new ArrayList<>();
		final List<String> swaps = new ArrayList<>();

		int clicksLeft() {
			return clicks;
		}

		@Override
		public boolean ready() {
			return ready;
		}

		@Override
		public boolean creative() {
			return creative;
		}

		@Override
		public int takePickClicks() {
			int taken = clicks;
			clicks = 0;
			return taken;
		}

		@Override
		public Optional<MiningTarget> target() {
			return target;
		}

		@Override
		public boolean blockInInventory() {
			return blockInInventory;
		}

		@Override
		public List<ToolSlot> tools() {
			return List.copyOf(tools.values());
		}

		@Override
		public int selectedSlot() {
			return selected;
		}

		@Override
		public void selectSlot(int slot) {
			selections.add(slot);
			selected = slot;
		}

		@Override
		public void swap(int inventorySlot, int hotbarSlot) {
			swaps.add(inventorySlot + "->" + hotbarSlot);
		}
	}

	static final class FakePickSettings implements PickSettings {
		PickMode mode = PickMode.ALWAYS;
		List<String> silkTouchBlocks = List.of();
		List<String> fortuneBlocks = List.of();
		int durabilityThreshold = 10;

		@Override
		public PickMode mode() {
			return mode;
		}

		@Override
		public List<String> silkTouchBlocks() {
			return silkTouchBlocks;
		}

		@Override
		public List<String> fortuneBlocks() {
			return fortuneBlocks;
		}

		@Override
		public int durabilityThreshold() {
			return durabilityThreshold;
		}
	}
}
