package com.merci.survivalkit.weapon;

/**
 * One weapon in reach, already measured.
 *
 * @param slot           inventory slot it sits in
 * @param kind           sword or axe
 * @param damage         attack damage with the player's base included, before enchantments
 * @param sharpness      Sharpness level, 0 when absent
 * @param smite          Smite level, 0 when absent
 * @param bane           Bane of Arthropods level, 0 when absent
 * @param durabilityLeft uses left before it breaks, or {@link #UNBREAKABLE}
 * @param item           the item's id, e.g. {@code minecraft:iron_sword}: the game resets the
 *                       attack cooldown only when the item in hand becomes a different one
 */
public record WeaponSlot(int slot, WeaponKind kind, double damage, int sharpness, int smite, int bane, int durabilityLeft,
		String item) {
	/** For weapons that do not wear out. */
	public static final int UNBREAKABLE = -1;
}
