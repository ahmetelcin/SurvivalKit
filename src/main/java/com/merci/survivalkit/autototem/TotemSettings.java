package com.merci.survivalkit.autototem;

import java.util.List;

/**
 * What the user has asked of AutoTotem, read fresh every tick so a setting changed mid-fight
 * takes effect on the next one.
 */
public interface TotemSettings {
	/**
	 * Ids to put in the off hand, in the user's own order, when no totem can be found. Empty
	 * by default: an empty hand is what a player expects once the totems run out, and anything
	 * else would be the mod deciding for them what belongs in that hand.
	 */
	List<String> fallback();

	/** Whether running out of totems is worth one line in chat. */
	boolean warnWhenOut();

	/** Whether the totem is wanted at all times, or only once health runs low. */
	TotemMode mode();

	/** Health, in half-hearts, below which the totem is wanted. Only read in that mode. */
	int healthThreshold();

	/**
	 * Whether whatever the off hand held before is put back once health recovers. Only
	 * means anything in the health-threshold mode, where the hand is borrowed rather than
	 * taken for good.
	 */
	boolean restoreOffhand();
}
