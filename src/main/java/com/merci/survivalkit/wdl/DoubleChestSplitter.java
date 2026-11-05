package com.merci.survivalkit.wdl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pure splitting logic for double chests.
 *
 * <p>In vanilla Minecraft, a double chest is made of two adjacent 27-slot chests:
 * the right chest (BlockType.FIRST) holds slots 0..26, and the left chest
 * (BlockType.SECOND) holds slots 27..53. When saved as individual block entities,
 * each chest requires a 27-slot inventory with slot indices 0..26.
 */
public final class DoubleChestSplitter {
	public static final int HALF_SIZE = 27;
	public static final int FULL_SIZE = 54;

	private DoubleChestSplitter() {
	}

	public record SplitPositions<P>(P rightPos, P leftPos) {
		public SplitPositions {
			Objects.requireNonNull(rightPos, "rightPos cannot be null");
			Objects.requireNonNull(leftPos, "leftPos cannot be null");
		}
	}

	public record SplitResult<T>(List<T> rightHalf, List<T> leftHalf) {
		public SplitResult {
			Objects.requireNonNull(rightHalf, "rightHalf cannot be null");
			Objects.requireNonNull(leftHalf, "leftHalf cannot be null");
		}
	}

	/**
	 * Resolves which block position is the right half (slots 0..26) and which is the
	 * left half (slots 27..53).
	 *
	 * @param clickedPos position of the block the player interacted with
	 * @param connectedPos position of the other connected chest block
	 * @param clickedIsRight true if the clicked block is the right chest half
	 */
	public static <P> SplitPositions<P> resolvePositions(P clickedPos, P connectedPos, boolean clickedIsRight) {
		if (clickedIsRight) {
			return new SplitPositions<>(clickedPos, connectedPos);
		} else {
			return new SplitPositions<>(connectedPos, clickedPos);
		}
	}

	/**
	 * Splits 54 container slots into the right half (0..26) and left half (27..53).
	 * Both halves contain 27 elements with indices 0..26.
	 */
	public static <T> SplitResult<T> splitItems(List<T> all54Items) {
		Objects.requireNonNull(all54Items, "items cannot be null");
		if (all54Items.size() != FULL_SIZE) {
			throw new IllegalArgumentException("Çift sandık 54 slot olmalı, bulunan: " + all54Items.size());
		}

		List<T> right = Collections.unmodifiableList(new ArrayList<>(all54Items.subList(0, HALF_SIZE)));
		List<T> left = Collections.unmodifiableList(new ArrayList<>(all54Items.subList(HALF_SIZE, FULL_SIZE)));
		return new SplitResult<>(right, left);
	}
}
