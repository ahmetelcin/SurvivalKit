package com.merci.survivalkit.autototem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * A hand-driven stand-in for the client. The inventory is a slot-to-id map and the off hand
 * is one more string, which is enough for every rule AutoTotem has — and it really moves
 * things, so a swap that is never undone shows up as an item in the wrong place.
 */
final class FakeTotemContext implements TotemContext {
	boolean ready = true;
	float health = 20.0f;
	String offhandId = "";
	final Map<Integer, String> inventory = new LinkedHashMap<>();
	/** Every off-hand swap, by slot, in the order it was asked for. */
	final List<Integer> offhandSwaps = new ArrayList<>();
	/** Every line put in chat, in order — the count is the point, so nothing is collapsed. */
	final List<String> said = new ArrayList<>();

	@Override
	public void say(String message) {
		said.add(message);
	}

	@Override
	public boolean ready() {
		return ready;
	}

	@Override
	public float health() {
		return health;
	}

	@Override
	public String offhandId() {
		return offhandId;
	}

	@Override
	public int count(String id) {
		int inBag = (int) inventory.values().stream().filter(id::equals).count();
		return inBag + (offhandId.equals(id) ? 1 : 0);
	}

	@Override
	public OptionalInt slotOf(String id) {
		return inventory.entrySet().stream()
			.filter(entry -> entry.getValue().equals(id))
			.mapToInt(Map.Entry::getKey)
			.min();
	}

	@Override
	public void swapToOffhand(int inventorySlot) {
		offhandSwaps.add(inventorySlot);

		String there = inventory.getOrDefault(inventorySlot, "");

		if (offhandId.isEmpty()) {
			inventory.remove(inventorySlot);
		} else {
			inventory.put(inventorySlot, offhandId);
		}

		offhandId = there;
	}

	/** The game spending the totem the player was saved by. */
	void totemPops() {
		offhandId = "";
	}
}
