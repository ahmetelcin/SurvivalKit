package com.merci.survivalkit.autoeat;

import com.merci.survivalkit.util.FoodSlot;
import java.util.List;

/** What {@link AutoEat} may ask of the running client, and what it may tell it to do. */
public interface EatContext {
	/** False while there is no player or a screen is open. */
	boolean ready();

	/** Hunger in half-drumsticks, 0-20. */
	int foodLevel();

	/** Health in half-hearts, 0-20. */
	float health();

	/** True while a bite is actually being taken. */
	boolean usingItem();

	int selectedSlot();

	/** Everything edible within the configured search scope. */
	List<FoodSlot> food();

	void selectSlot(int slot);

	/**
	 * Exchanges the contents of two inventory slots. Symmetric: calling it again with the
	 * same pair puts the inventory back the way it was.
	 */
	void swap(int inventorySlot, int hotbarSlot);

	void holdUseKey(boolean held);

	/** The namespaced id of whatever is in the off hand, or an empty string when it is empty. */
	String offhandId();

	/**
	 * Exchanges an inventory slot with the off hand. Symmetric, like {@link #swap}: calling
	 * it again with the same slot puts both back. The main hand is not involved.
	 */
	void swapToOffhand(int inventorySlot);

	/**
	 * Starts using what is in that hand, directly, without going through the use key.
	 *
	 * <p>Pressing use is not the same thing: the game offers the main hand first, so a block
	 * would be placed, a log stripped or a shield raised before the food in the off hand was
	 * ever reached. The key is still held afterwards, but only to keep a bite that has
	 * already started from being let go.
	 */
	void startUsing(EatingHand hand);
}
