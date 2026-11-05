package com.merci.survivalkit.autotool;

import java.util.List;

/** What the user has asked of Otomatik Alet, read fresh every tick. */
public interface ToolSettings {
	/** Whether whatever was in hand before goes back once breaking stops. */
	boolean restorePrevious();

	/** Blocks to break with Silk Touch when a tool that has it is in reach. */
	List<String> silkTouchBlocks();

	/** Blocks to break with Fortune when a tool that has it is in reach. */
	List<String> fortuneBlocks();

	/** Tools with fewer uses left than this are never taken out. */
	int durabilityThreshold();

	/** Whether a sword in hand is left there, so a fight is never disarmed by a stray block. */
	boolean keepSword();

	/**
	 * Whether a Silk Touch tool in hand is left there when it can drop the block. Someone
	 * holding one may well be after stone as stone, and a faster plain pickaxe would undo that.
	 */
	boolean keepSilkTouch();

	/**
	 * Whether a run only starts with a tool already in hand. With bare hands, bread or a block
	 * in hand the player is not mining on purpose — punching a block out of the way, placing
	 * and knocking one back — and the hand is left as it is.
	 */
	boolean onlyWithToolInHand();
}
