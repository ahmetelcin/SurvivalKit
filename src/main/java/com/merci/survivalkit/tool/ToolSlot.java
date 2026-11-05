package com.merci.survivalkit.tool;

/**
 * One item in reach, already measured against the block.
 *
 * @param slot           inventory slot the item sits in
 * @param harvests       breaking the block with this item drops it
 * @param speed          destroy speed against the block, Efficiency included; the bare hand is 1
 * @param silkTouch      Silk Touch level, 0 when absent
 * @param fortune        Fortune level, 0 when absent
 * @param durabilityLeft uses left before it breaks, or {@link #UNBREAKABLE}
 */
public record ToolSlot(int slot, boolean harvests, float speed, int silkTouch, int fortune, int durabilityLeft) {
	/** For items that do not wear out. */
	public static final int UNBREAKABLE = -1;
}
