package com.merci.survivalkit.vein;

/**
 * The tool in the main hand, as far as wear goes. Only a tool that wears at all is one: a tool
 * component with damage per block, and an item that can take damage (not unbreakable).
 *
 * @param slot           the hotbar slot it is in, 0 to 8
 * @param damage         the damage it has taken
 * @param maxDamage      the damage that breaks it
 * @param damagePerBlock what one block costs it, from its tool component (1 for tools, 2 for swords)
 * @param unbreaking     its Unbreaking level, 0 without
 */
public record HeldTool(int slot, int damage, int maxDamage, int damagePerBlock, int unbreaking) {
	/** What it has left before it breaks. */
	public int left() {
		return maxDamage - damage;
	}
}
