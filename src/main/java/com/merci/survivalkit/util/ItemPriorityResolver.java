package com.merci.survivalkit.util;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Picks which food to eat out of a snapshot.
 *
 * <p>Pure on purpose: it takes a list of {@link FoodSlot}, the two lists the user maintains
 * and a fallback mode, and returns one of the slots it was handed. It never reaches for the
 * game, so every rule below can be tried in a plain unit test.
 *
 * <p>The order of business is always the same: drop anything blocked, honour the priority
 * list if it matches, and only then fall back.
 */
public final class ItemPriorityResolver {
	/** What to do when nothing on the priority list is in reach. */
	public enum Fallback {
		/** Fill the bar fastest. */
		HIGHEST_NUTRITION,
		/** Keep the bar full longest. */
		HIGHEST_SATURATION,
		/** Burn the junk first and save the good food for later. */
		LOWEST_VALUE_FIRST,
		/** Eat nothing that was not asked for by name. */
		NOTHING
	}

	/**
	 * Food that hurts goes last whatever the mode, and a lower slot breaks every remaining
	 * tie so the same snapshot always resolves the same way.
	 */
	private static final Comparator<FoodSlot> SAFE_FIRST = Comparator.comparing(FoodSlot::risky);
	private static final Comparator<FoodSlot> BY_SLOT = Comparator.comparingInt(FoodSlot::slot);

	private ItemPriorityResolver() {
	}

	public static Optional<FoodSlot> choose(List<FoodSlot> available, List<String> priority, List<String> blocked, Fallback fallback) {
		return choose(available, priority, blocked, List.of(), fallback);
	}

	/**
	 * @param reserved ids the fallback may not reach for, though the priority list still may.
	 *                 This is how something is kept for an occasion — a golden apple saved for
	 *                 low health rather than spent on an empty stomach — without blocking it
	 *                 outright, which would also silence a user who asked for it by name.
	 */
	public static Optional<FoodSlot> choose(List<FoodSlot> available, List<String> priority, List<String> blocked,
			List<String> reserved, Fallback fallback) {
		Objects.requireNonNull(available, "available");
		Objects.requireNonNull(priority, "priority");
		Objects.requireNonNull(blocked, "blocked");
		Objects.requireNonNull(reserved, "reserved");
		Objects.requireNonNull(fallback, "fallback");

		List<FoodSlot> allowed = available.stream().filter(food -> !blocked.contains(food.id())).toList();
		List<FoodSlot> spendable = allowed.stream().filter(food -> !reserved.contains(food.id())).toList();

		return fromPriorityList(allowed, priority).or(() -> fromFallback(spendable, fallback));
	}

	/**
	 * The earliest entry on the list that is actually in reach wins — the user's order is an
	 * instruction, not a hint, so a listed item beats a more filling unlisted one, and a
	 * listed risky item beats a safe unlisted one.
	 */
	private static Optional<FoodSlot> fromPriorityList(List<FoodSlot> allowed, List<String> priority) {
		return allowed.stream()
			.filter(food -> priority.contains(food.id()))
			.min(Comparator.comparingInt((FoodSlot food) -> priority.indexOf(food.id())).thenComparing(BY_SLOT));
	}

	private static Optional<FoodSlot> fromFallback(List<FoodSlot> allowed, Fallback fallback) {
		if (fallback == Fallback.NOTHING) {
			return Optional.empty();
		}

		return allowed.stream().min(SAFE_FIRST.thenComparing(ranking(fallback)).thenComparing(BY_SLOT));
	}

	private static Comparator<FoodSlot> ranking(Fallback fallback) {
		return switch (fallback) {
			case HIGHEST_NUTRITION -> Comparator.comparingInt(FoodSlot::nutrition).reversed()
				.thenComparing(Comparator.comparingDouble(FoodSlot::saturation).reversed());
			case HIGHEST_SATURATION -> Comparator.comparingDouble(FoodSlot::saturation).reversed()
				.thenComparing(Comparator.comparingInt(FoodSlot::nutrition).reversed());
			case LOWEST_VALUE_FIRST -> Comparator.comparingInt(FoodSlot::nutrition)
				.thenComparing(Comparator.comparingDouble(FoodSlot::saturation));
			case NOTHING -> throw new IllegalStateException("NOTHING sıralanmaz");
		};
	}
}
