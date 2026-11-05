package com.merci.survivalkit.autotool;

/** What a middle click on a block does while Alet Seçici is on. */
public enum PickMode {
	/** The best tool for the block, always. The game's own pick block never runs. */
	ALWAYS,
	/** The game's pick block when the block is in the inventory, the best tool otherwise. */
	WHEN_BLOCK_NOT_IN_INVENTORY,
	/** The game's pick block, as if the module were not there. */
	OFF
}
