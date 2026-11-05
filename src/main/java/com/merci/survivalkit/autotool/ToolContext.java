package com.merci.survivalkit.autotool;

import com.merci.survivalkit.tool.ToolSlot;
import java.util.List;
import java.util.Optional;

/** What {@link AutoTool} may ask of the running client, and what it may tell it to do. */
public interface ToolContext {
	/** False while there is no player or a screen is open. */
	boolean ready();

	boolean creative();

	/** The attack key is held down. */
	boolean attackHeld();

	/** A block is being broken right now. False in the short pause between two blocks. */
	boolean breaking();

	/** The block under the crosshair, if there is one. */
	Optional<MiningTarget> target();

	/** Every item within the configured search scope, measured against {@link #target}. */
	List<ToolSlot> tools();

	/** The main hand holds a pickaxe, axe, shovel, hoe or shears. */
	boolean holdingTool();

	/** The main hand holds a sword. */
	boolean holdingSword();

	int selectedSlot();

	/**
	 * Another module's item sits in this inventory slot — Yapı Modu's wand while it is on — and
	 * stays there: it is not a tool to take out, and with it in hand the hand is left alone.
	 */
	boolean reserved(int slot);

	void selectSlot(int slot);

	/**
	 * Exchanges the contents of two inventory slots. Symmetric: calling it again with the
	 * same pair puts the inventory back the way it was.
	 */
	void swap(int inventorySlot, int hotbarSlot);
}
