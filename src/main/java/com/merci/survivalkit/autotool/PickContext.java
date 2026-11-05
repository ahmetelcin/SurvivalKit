package com.merci.survivalkit.autotool;

import com.merci.survivalkit.tool.ToolSlot;
import java.util.List;
import java.util.Optional;

/** What {@link ToolPicker} may ask of the running client, and what it may tell it to do. */
public interface PickContext {
	/** False while there is no player or a screen is open. */
	boolean ready();

	boolean creative();

	/**
	 * Takes every pick-block click waiting since the last tick and says how many there were.
	 * A click taken here is gone: the game's own pick block, which runs later in the same
	 * tick, does not see it. A click not taken is left for the game.
	 */
	int takePickClicks();

	/** The block under the crosshair, if there is one. */
	Optional<MiningTarget> target();

	/** Whether the item of the block under the crosshair is anywhere in the inventory. */
	boolean blockInInventory();

	/** Every item within the configured search scope, measured against {@link #target}. */
	List<ToolSlot> tools();

	int selectedSlot();

	void selectSlot(int slot);

	/** Exchanges the contents of two inventory slots. */
	void swap(int inventorySlot, int hotbarSlot);
}
