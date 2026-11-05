package com.merci.survivalkit.scaffold;

import com.merci.survivalkit.area.Area;
import com.merci.survivalkit.vein.Pos;
import java.util.List;
import java.util.Optional;

/** What {@link Scaffold} may ask of the running client, and what it may tell it to do. */
public interface ScaffoldContext extends PlacementRule.Supports {
	/** False while there is no player or level. */
	boolean ready();

	boolean screenOpen();

	/** The player's feet: the bottom middle of the body. */
	Vec feet();

	/** How far the player moves this tick, in blocks. */
	Vec motion();

	/** Standing on something, as the game has it. */
	boolean onGround();

	/** The real jump key is held down. */
	boolean jumpHeld();

	/** The real sneak key is held down, with no screen open — never the module's own SafeWalk sneak. */
	boolean sneakKeyHeld();

	/** The Y lock key is held down, with no screen open. */
	boolean lockKeyHeld();

	/** How far the player has fallen since last on the ground. */
	double fallDistance();

	boolean inLava();

	/** Which way the movement keys are taking the player, level and of length 1; zero with none held. */
	Vec heading();

	/** Holds the sneak key down, or lets it go back to what the real key says. */
	void sneak(boolean down);

	/** Yapı Modu's area here, while it is on and both corners are marked. */
	Optional<Area> area();

	Vec eyes();

	/** How far from the eyes the player can use a block. */
	double reach();

	Gap gap(Pos pos);

	/** Every stack within the configured search scope, reserved items left out. */
	List<BlockStack> blocks();

	int selectedSlot();

	void selectSlot(int slot);

	/** Exchanges two inventory slots; calling it again with the same pair puts them back. */
	void swap(int inventorySlot, int hotbarSlot);

	/** Puts a line on the action bar, above the hotbar; a warning stands out. */
	void actionBar(String text, boolean warning);

	/** Draws a box round this block until the next tick. */
	void outline(Pos pos);

	/**
	 * Uses the main hand on the support's face at the hit point, through vanilla's own placement.
	 *
	 * @return whether the game took it: a block went down
	 */
	boolean place(Placement placement);
}
