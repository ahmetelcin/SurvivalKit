package com.merci.survivalkit.util;

/**
 * One edible stack, reduced to what a chooser needs. A snapshot: nothing here points back
 * at the game, so the moment it was taken is the moment it describes.
 *
 * @param slot       inventory index — 0-8 is the hotbar, higher is the backpack
 * @param id         namespaced item id, as the priority and block lists spell it
 * @param nutrition  hunger restored, in half-drumsticks
 * @param saturation saturation restored
 * @param risky      true for food that hurts: rotten flesh, raw chicken, pufferfish
 */
public record FoodSlot(int slot, String id, int nutrition, float saturation, boolean risky) {
	public boolean inHotbar() {
		return slot < HOTBAR_SIZE;
	}

	/** Vanilla's hotbar width. Kept here so the pure side does not have to ask the game. */
	public static final int HOTBAR_SIZE = 9;
}
