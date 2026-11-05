package com.merci.survivalkit.autotool;

import com.merci.survivalkit.tool.ToolSlot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * A hand-driven stand-in for the client. The inventory really moves: a swap exchanges two
 * entries, so a swap that is never undone shows up as an item in the wrong slot.
 */
final class FakeToolContext implements ToolContext {
	/** One item, already measured against whatever block the test is breaking. */
	record Item(String name, boolean harvests, float speed, int silkTouch, int fortune, int durabilityLeft, boolean sword,
			boolean tool) {
		/** Not a tool: bread, a block, a sword. */
		Item(String name, boolean harvests, float speed, int silkTouch, int fortune, int durabilityLeft, boolean sword) {
			this(name, harvests, speed, silkTouch, fortune, durabilityLeft, sword, false);
		}

		/** A pickaxe, axe, shovel, hoe or shears. */
		static Item tool(String name, float speed) {
			return new Item(name, true, speed, 0, 0, ToolSlot.UNBREAKABLE, false, true);
		}

		static Item plain(String name) {
			return new Item(name, true, 1, 0, 0, ToolSlot.UNBREAKABLE, false);
		}

		static Item sword(String name) {
			return new Item(name, true, 1.5f, 0, 0, ToolSlot.UNBREAKABLE, true);
		}
	}

	boolean ready = true;
	boolean creative;
	boolean attackHeld;
	boolean breaking;
	Optional<MiningTarget> target = Optional.of(new MiningTarget("minecraft:stone", true));
	int selected;
	/** Slots holding another module's item — the wand. */
	final Set<Integer> reserved = new HashSet<>();
	final Map<Integer, Item> inventory = new TreeMap<>();

	/** Every selection, in order — the count is the point, so nothing is collapsed. */
	final List<Integer> selections = new ArrayList<>();
	/** Every swap as "from->to", in order. */
	final List<String> swaps = new ArrayList<>();

	/** Presses and holds attack on the current target, as the first tick of breaking sees it. */
	void startBreaking() {
		attackHeld = true;
		breaking = true;
	}

	void releaseAttack() {
		attackHeld = false;
		breaking = false;
	}

	String held() {
		Item item = inventory.get(selected);
		return item == null ? "" : item.name();
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
	public boolean attackHeld() {
		return attackHeld;
	}

	@Override
	public boolean breaking() {
		return breaking;
	}

	@Override
	public Optional<MiningTarget> target() {
		return target;
	}

	@Override
	public List<ToolSlot> tools() {
		List<ToolSlot> tools = new ArrayList<>();
		inventory.forEach((slot, item) -> tools.add(
			new ToolSlot(slot, item.harvests(), item.speed(), item.silkTouch(), item.fortune(), item.durabilityLeft())));
		return tools;
	}

	@Override
	public boolean holdingTool() {
		Item item = inventory.get(selected);
		return item != null && item.tool();
	}

	@Override
	public boolean holdingSword() {
		Item item = inventory.get(selected);
		return item != null && item.sword();
	}

	@Override
	public boolean reserved(int slot) {
		return reserved.contains(slot);
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

		Item from = inventory.remove(inventorySlot);
		Item to = inventory.remove(hotbarSlot);

		if (from != null) {
			inventory.put(hotbarSlot, from);
		}

		if (to != null) {
			inventory.put(inventorySlot, to);
		}
	}
}
