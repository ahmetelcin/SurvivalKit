package com.merci.survivalkit.scaffold;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Which stack to build with, out of a snapshot. Like
 * {@link com.merci.survivalkit.util.ItemPriorityResolver}: drop what may not be used, honour the
 * list if anything on it is in reach, and only then fall back.
 */
public final class BlockChoice {
	/** What to build with when nothing on the list is in reach. */
	public enum Fallback {
		/** The biggest stack, so the hand changes least often. */
		LARGEST_STACK,
		/** The one already in hand, else the nearest: hotbar before backpack, lower slot first. */
		NEAREST,
		/** Nothing that was not asked for by name. */
		NOTHING
	}

	static final int HOTBAR_SIZE = 9;

	private static final List<String> COLOURS = List.of(
		"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
		"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

	/**
	 * Never built with unless the user takes them off the list. Blocks that fall — a floor of sand
	 * drops out from under you the moment it goes down — TNT, and containers: a chest spent as
	 * a floor is a chest gone from the inventory, and a shulker box put down is everything in it.
	 */
	public static final List<String> BLOCKED_BY_DEFAULT = Stream.of(
			Stream.of("minecraft:sand", "minecraft:red_sand", "minecraft:gravel",
				"minecraft:suspicious_sand", "minecraft:suspicious_gravel",
				"minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil",
				"minecraft:dragon_egg", "minecraft:pointed_dripstone", "minecraft:scaffolding"),
			COLOURS.stream().map(colour -> "minecraft:" + colour + "_concrete_powder"),
			Stream.of("minecraft:tnt"),
			Stream.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:ender_chest",
				"minecraft:shulker_box"),
			COLOURS.stream().map(colour -> "minecraft:" + colour + "_shulker_box"),
			Stream.of("minecraft:hopper", "minecraft:dispenser", "minecraft:dropper", "minecraft:crafter",
				"minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker", "minecraft:brewing_stand"))
		.flatMap(ids -> ids)
		.toList();

	private BlockChoice() {
	}

	/**
	 * @param selected        the hotbar slot in hand; between two equal stacks that one stays
	 * @param preferFullStack a full stack goes before a part one of the same rank
	 */
	public static Optional<BlockStack> choose(List<BlockStack> available, List<String> preferred, List<String> blocked,
			boolean preferFullStack, Fallback fallback, int selected) {
		return choose(available, Optional.empty(), preferred, blocked, preferFullStack, fallback, selected);
	}

	/**
	 * @param first a block to use before the list whenever a usable stack of it is in reach — the
	 *              one underfoot, for "same block"; the list and the fallback decide only without it
	 */
	public static Optional<BlockStack> choose(List<BlockStack> available, Optional<String> first, List<String> preferred,
			List<String> blocked, boolean preferFullStack, Fallback fallback, int selected) {
		Objects.requireNonNull(available, "available");
		Objects.requireNonNull(first, "first");
		Objects.requireNonNull(preferred, "preferred");
		Objects.requireNonNull(blocked, "blocked");
		Objects.requireNonNull(fallback, "fallback");

		List<BlockStack> usable = usable(available, blocked);
		Comparator<BlockStack> within = within(preferFullStack, selected);

		Optional<BlockStack> same = first.flatMap(id -> usable.stream().filter(stack -> stack.id().equals(id)).min(within));

		if (same.isPresent()) {
			return same;
		}

		Optional<BlockStack> listed = usable.stream()
			.filter(stack -> preferred.contains(stack.id()))
			.min(Comparator.comparingInt((BlockStack stack) -> preferred.indexOf(stack.id())).thenComparing(within));

		if (listed.isPresent() || fallback == Fallback.NOTHING) {
			return listed;
		}

		Comparator<BlockStack> ranking = fallback == Fallback.LARGEST_STACK
			? Comparator.comparingInt(BlockStack::count).reversed().thenComparing(within)
			: within;
		return usable.stream().min(ranking);
	}

	/** The torch to hang on the bridge: the one in hand, else the hotbar's, the lower slot first. */
	public static Optional<BlockStack> torch(List<BlockStack> available, int selected) {
		return available.stream()
			.filter(stack -> TORCHES.contains(stack.id()) && stack.count() > 0)
			.min(within(false, selected));
	}

	/** What hangs on a wall as a torch does. */
	public static final List<String> TORCHES = List.of("minecraft:torch", "minecraft:soul_torch", "minecraft:copper_torch");

	/** Every stack that may be built with at all: a whole block, not on the blocked list. */
	public static List<BlockStack> usable(List<BlockStack> available, List<String> blocked) {
		return available.stream()
			.filter(stack -> stack.placeable() && stack.count() > 0 && !blocked.contains(stack.id()))
			.toList();
	}

	/** Between stacks of the same rank: full first if asked, then the one in hand, the hotbar, the lower slot. */
	private static Comparator<BlockStack> within(boolean preferFullStack, int selected) {
		Comparator<BlockStack> full = Comparator.comparing((BlockStack stack) -> preferFullStack && !stack.full());
		return full
			.thenComparing(stack -> stack.slot() != selected)
			.thenComparing(stack -> !stack.inHotbar())
			.thenComparingInt(BlockStack::slot);
	}
}
