package com.merci.survivalkit.autoeat;

import com.merci.survivalkit.util.FoodSlot;
import java.util.ArrayList;
import java.util.List;

/** A hand-driven stand-in for the live client, so the state machine can be tested on its own. */
final class FakeEatContext implements EatContext {
	boolean ready = true;
	int foodLevel = 20;
	float health = 20.0f;
	boolean usingItem;
	int selectedSlot = 0;
	List<FoodSlot> food = new ArrayList<>();
	boolean useKeyHeld;
	String offhandId = "";
	/** Whether a bite asked for directly actually starts. False stands in for lag or a refusal. */
	boolean biteStarts = true;
	final List<Integer> slotSelections = new ArrayList<>();
	/** Every swap asked for, as "from>to", so an undo shows up as the pair repeated. */
	final List<String> swaps = new ArrayList<>();
	/** Every off-hand swap, by inventory slot, in the order it was asked for. */
	final List<Integer> offhandSwaps = new ArrayList<>();
	/** Every bite started directly, and which hand it was started for. */
	final List<EatingHand> startedUsing = new ArrayList<>();

	@Override
	public boolean ready() {
		return ready;
	}

	@Override
	public int foodLevel() {
		return foodLevel;
	}

	@Override
	public float health() {
		return health;
	}

	@Override
	public boolean usingItem() {
		return usingItem;
	}

	@Override
	public int selectedSlot() {
		return selectedSlot;
	}

	@Override
	public List<FoodSlot> food() {
		return food;
	}

	@Override
	public void selectSlot(int slot) {
		selectedSlot = slot;
		slotSelections.add(slot);
	}

	@Override
	public void swap(int inventorySlot, int hotbarSlot) {
		swaps.add(inventorySlot + ">" + hotbarSlot);
	}

	@Override
	public void holdUseKey(boolean held) {
		useKeyHeld = held;
	}

	@Override
	public String offhandId() {
		return offhandId;
	}

	/**
	 * Moves the food out of the inventory and into the off hand, the way the real swap does:
	 * a stack in the off hand is no longer one of the slots {@link #food()} offers, which is
	 * the detail the state machine has to cope with. Symmetric, so a second call puts it back.
	 */
	@Override
	public void swapToOffhand(int inventorySlot) {
		offhandSwaps.add(inventorySlot);

		FoodSlot held = heldFood;

		if (held != null && held.slot() == inventorySlot) {
			food = new ArrayList<>(food);
			food.add(held);
			heldFood = null;
			offhandId = "";
			return;
		}

		for (FoodSlot slot : food) {
			if (slot.slot() == inventorySlot) {
				food = new ArrayList<>(food);
				food.remove(slot);
				heldFood = slot;
				offhandId = slot.id();
				return;
			}
		}

		// Nothing edible there: the off hand still changes hands with whatever was in it.
		offhandId = "";
	}

	@Override
	public void startUsing(EatingHand hand) {
		startedUsing.add(hand);
		usingItem = biteStarts;
	}

	/** What the off hand is holding because this fake put it there. */
	private FoodSlot heldFood;
}
