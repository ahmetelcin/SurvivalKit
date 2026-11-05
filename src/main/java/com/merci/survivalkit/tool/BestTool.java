package com.merci.survivalkit.tool;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The "best tool" rule both tool modules share: which item in reach to break a block with.
 *
 * <p>Pure on purpose, like {@link com.merci.survivalkit.util.ItemPriorityResolver}: it is
 * handed items already measured against the block and returns one of them, so every rule
 * below can be tried without a game.
 *
 * <p>The order of business:
 * <ol>
 * <li>Worn tools are out — below the durability threshold an item is not chosen for
 *     anything, speed and enchantments included.
 * <li>If the block drops nothing without the right tool, only items that drop it are in.
 *     When none do, nothing is chosen: breaking an ore faster to lose it anyway only spends
 *     durability.
 * <li>If the block is on the Silk Touch or the Fortune list, that enchantment decides before
 *     speed, higher level first. Silk Touch wins when a block is on both lists, since the
 *     two cannot sit on one tool and a user who listed a block for Silk Touch wants the
 *     block itself.
 * <li>Then the fastest, and then the lower slot so the same inventory always resolves the
 *     same way.
 * </ol>
 * An item that changes nothing compared to the bare hand — no faster, no better drop, no
 * wanted enchantment — is never chosen, so a sword is not pulled out to dig dirt.
 */
public final class BestTool {
	/** Destroy speed of the bare hand and of anything that is not a tool for the block. */
	public static final float HAND_SPEED = 1.0f;

	private static final Comparator<ToolSlot> FASTEST = Comparator.comparingDouble((ToolSlot tool) -> tool.speed()).reversed();
	private static final Comparator<ToolSlot> BY_SLOT = Comparator.comparingInt(ToolSlot::slot);

	private BestTool() {
	}

	/**
	 * @param durabilityThreshold items with fewer uses left than this are never chosen;
	 *                            unbreakable items are never held back by it
	 */
	public static Optional<ToolSlot> choose(List<ToolSlot> available, BlockTarget block, int durabilityThreshold) {
		Objects.requireNonNull(available, "available");
		Objects.requireNonNull(block, "block");

		return available.stream()
			.filter(tool -> !worn(tool, durabilityThreshold))
			.filter(tool -> tool.harvests() || !block.needsCorrectTool())
			.filter(tool -> betterThanTheHand(tool, block))
			.min(Comparator.comparingInt((ToolSlot tool) -> wantedEnchantment(tool, block)).reversed()
				.thenComparing(FASTEST)
				.thenComparing(BY_SLOT));
	}

	private static boolean worn(ToolSlot tool, int threshold) {
		return tool.durabilityLeft() != ToolSlot.UNBREAKABLE && tool.durabilityLeft() < threshold;
	}

	private static boolean betterThanTheHand(ToolSlot tool, BlockTarget block) {
		return tool.speed() > HAND_SPEED
			|| block.needsCorrectTool()
			|| wantedEnchantment(tool, block) > 0;
	}

	/** The level of the enchantment this block is listed for, or 0. */
	private static int wantedEnchantment(ToolSlot tool, BlockTarget block) {
		if (block.wantsSilkTouch()) {
			return tool.silkTouch();
		}

		if (block.wantsFortune()) {
			return tool.fortune();
		}

		return 0;
	}
}
