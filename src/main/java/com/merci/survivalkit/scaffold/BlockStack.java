package com.merci.survivalkit.scaffold;

/**
 * One stack in reach that might be put down.
 *
 * @param slot      inventory slot; 0–8 is the hotbar
 * @param id        the item's namespaced id
 * @param count     how many are in the stack
 * @param maxCount  how many the stack holds when full
 * @param placeable it is a block that goes down as a whole cube a player can stand on — not a
 *                  slab, a torch or a sapling, and not something that is no block at all
 */
public record BlockStack(int slot, String id, int count, int maxCount, boolean placeable) {
	public boolean full() {
		return count >= maxCount;
	}

	public boolean inHotbar() {
		return slot < BlockChoice.HOTBAR_SIZE;
	}
}
