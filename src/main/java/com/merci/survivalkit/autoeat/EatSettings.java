package com.merci.survivalkit.autoeat;

import com.merci.survivalkit.util.ItemPriorityResolver.Fallback;
import java.util.List;

/**
 * What the user has asked for, read fresh every tick so a setting changed mid-game takes
 * effect on the next one. The module implements this over its settings; tests implement it
 * with plain fields.
 */
public interface EatSettings {
	/** Whether the line is {@link #hungerThreshold} or worked out from the food itself. */
	ThresholdMode thresholdMode();

	/**
	 * Start eating once hunger drops below this, on the 0-20 half-drumstick scale. Only read
	 * in {@link ThresholdMode#FIXED}.
	 */
	int hungerThreshold();

	/**
	 * Whether low health means eating without waiting for hunger, so natural healing, which
	 * stops once the bar drops, keeps going. Not the golden apple rule: ordinary food, and
	 * only while the bar is not already full.
	 */
	boolean eatEarlyWhenHurt();

	/** Health, in half-hearts, below which {@link #eatEarlyWhenHurt} applies. */
	int earlyEatingHealth();

	/** Namespaced ids to eat first, best first. Empty means no preference. */
	List<String> priority();

	/** Namespaced ids never to eat, whatever else happens. */
	List<String> blocked();

	/** What to reach for when nothing on the priority list is around. */
	Fallback fallback();

	/** Whether to break out a golden apple when health runs low. */
	boolean goldenApple();

	/** Whether the health rule may also spend an enchanted golden apple. */
	boolean enchantedGoldenApple();

	/** Health, in half-hearts, below which the golden apple comes out. */
	int healthThreshold();

	/** Whether the hotbar slot that was selected before a bite is selected again after. */
	boolean returnToSlot();

	/** Which hand the food is eaten from. */
	EatingHand eatingHand();

	/**
	 * Whether a totem already in the off hand is left alone, at the cost of eating in the
	 * main hand that time. Only means anything while eating off hand.
	 */
	boolean keepOffhandTotem();
}
