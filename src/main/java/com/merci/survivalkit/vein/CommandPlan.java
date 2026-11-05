package com.merci.survivalkit.vein;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * Turns the found blocks into commands. The blocks are taken a column at a time: each unbroken
 * run of one block in one column is one piece of work, so a /fill can do in one command what
 * would take a /setblock per block. How the drops come depends on what the server allows —
 * see {@link Route}.
 */
public final class CommandPlan {
	/** How a block is turned into drops and air. */
	public enum Route {
		/**
		 * /loot mine with the player's main hand — Fortune and Silk Touch count — then the block is
		 * cleared with a plain /fill. Needs /loot.
		 */
		LOOT,
		/**
		 * Silk Touch in hand but no /loot: Silk Touch drops the block itself, so the block itself is
		 * handed over with /give, then cleared. Exact for logs and ores.
		 */
		GIVE_SILK,
		/**
		 * Fortune in hand on an ore {@link OreDrops} knows, but no /loot: the drops are rolled here by
		 * vanilla's rules and handed over with /give, then cleared; the ores' XP follows with one
		 * /xp at the end when the server allows it.
		 */
		GIVE_FORTUNE,
		/**
		 * None of those: /fill or /setblock with "destroy", which drops what the block drops to a
		 * bare hand, where it stood, with the ores' XP. The server's own dice; no Fortune.
		 */
		DESTROY;

		/**
		 * @param fortuneOre Fortune in hand and an ore whose drop {@link OreDrops} knows
		 */
		public static Route of(boolean lootAllowed, boolean silkTouch, boolean fortuneOre) {
			if (lootAllowed) {
				return LOOT;
			}

			if (silkTouch) {
				return GIVE_SILK;
			}

			return fortuneOre ? GIVE_FORTUNE : DESTROY;
		}
	}

	/**
	 * The dice for {@link Route#GIVE_FORTUNE}.
	 *
	 * @param fortune the Fortune level in hand
	 * @param xp      whether /xp may be sent
	 */
	public record Dice(int fortune, RandomGenerator random, boolean xp) {
		public static final Dice NONE = new Dice(0, new Random(0), false);
	}

	/**
	 * One command. A guard is the blocks that must still be {@code guardId} when the command goes;
	 * if the player has changed one of them in the meantime, {@code otherwise} goes instead — the
	 * same work block by block, each with its own guard — and the command itself is dropped.
	 */
	public record Step(String command, List<Pos> guard, String guardId, List<Step> otherwise) {
		public static Step unguarded(String command) {
			return new Step(command, List.of(), null, List.of());
		}

		static Step guarded(String command, Pos at, String id) {
			return new Step(command, List.of(at), id, List.of());
		}

		public boolean guarded() {
			return !guard.isEmpty();
		}
	}

	private CommandPlan() {
	}

	/**
	 * @param idOf    the block at each position, as found
	 * @param useFill whether /fill may be used; if not, every block gets a /setblock
	 */
	public static List<Step> of(List<Pos> blocks, Function<Pos, String> idOf, Drops drops, Route route, boolean useFill) {
		return of(blocks, idOf, drops, route, useFill, Dice.NONE);
	}

	public static List<Step> of(List<Pos> blocks, Function<Pos, String> idOf, Drops drops, Route route, boolean useFill, Dice dice) {
		int experience = 0;
		// Columns in the order their first block was found, so the work starts where the player looked.
		Map<Long, List<Pos>> columns = new LinkedHashMap<>();

		for (Pos pos : blocks) {
			columns.computeIfAbsent(((long) pos.x() << 32) ^ (pos.z() & 0xffffffffL), key -> new ArrayList<>()).add(pos);
		}

		List<Step> steps = new ArrayList<>();

		for (List<Pos> column : columns.values()) {
			column.sort(Comparator.comparingInt(Pos::y));

			for (List<Pos> run : runs(column, idOf)) {
				String id = idOf.apply(run.getFirst());

				switch (route) {
					case LOOT -> {
						for (Pos pos : run) {
							steps.add(Step.guarded(loot(pos, drops), pos, id));
						}

						steps.addAll(clear(run, id, useFill));
					}
					case GIVE_SILK -> {
						List<Step> oneByOne = run.stream().map(pos -> Step.guarded("give @s " + id + " 1", pos, id)).toList();
						steps.add(run.size() == 1 ? oneByOne.getFirst() : new Step("give @s " + id + " " + run.size(), run, id, oneByOne));
						steps.addAll(clear(run, id, useFill));
					}
					case GIVE_FORTUNE -> {
						OreDrops.Drop drop = OreDrops.of(id).orElseThrow(() -> new IllegalArgumentException("Servet tablosunda yok: " + id));
						List<Step> oneByOne = new ArrayList<>();
						int total = 0;

						for (Pos pos : run) {
							int count = drop.roll(dice.random(), dice.fortune());
							total += count;
							experience += drop.rollXp(dice.random());
							oneByOne.add(Step.guarded("give @s " + drop.item() + " " + count, pos, id));
						}

						steps.add(run.size() == 1 ? oneByOne.getFirst() : new Step("give @s " + drop.item() + " " + total, run, id, List.copyOf(oneByOne)));
						steps.addAll(clear(run, id, useFill));
					}
					case DESTROY -> {
						List<Step> oneByOne = run.stream().map(pos -> Step.guarded("setblock " + coords(pos) + " minecraft:air destroy", pos, id)).toList();

						if (useFill && run.size() > 1) {
							// "destroy" takes no filter, so the run is checked whole first.
							steps.add(new Step("fill " + coords(run.getFirst()) + " " + coords(run.getLast()) + " minecraft:air destroy", run, id, oneByOne));
						} else {
							steps.addAll(oneByOne);
						}
					}
				}
			}
		}

		if (dice.xp() && experience > 0) {
			steps.add(Step.unguarded("xp add @s " + experience + " points"));
		}

		return steps;
	}

	/** Air where the run was, once its drops are out. */
	private static List<Step> clear(List<Pos> run, String id, boolean useFill) {
		if (useFill) {
			// "replace <id>": whatever else stands in the range by then is left alone.
			return List.of(Step.unguarded("fill " + coords(run.getFirst()) + " " + coords(run.getLast()) + " minecraft:air replace " + id));
		}

		return run.stream().map(pos -> Step.guarded("setblock " + coords(pos) + " minecraft:air", pos, id)).toList();
	}

	/** A column cut wherever a gap is or the block changes, so every run is one id end to end. */
	private static List<List<Pos>> runs(List<Pos> column, Function<Pos, String> idOf) {
		List<List<Pos>> runs = new ArrayList<>();
		List<Pos> current = new ArrayList<>();

		for (Pos pos : column) {
			if (!current.isEmpty()) {
				Pos last = current.getLast();

				if (pos.y() != last.y() + 1 || !idOf.apply(pos).equals(idOf.apply(last))) {
					runs.add(current);
					current = new ArrayList<>();
				}
			}

			current.add(pos);
		}

		if (!current.isEmpty()) {
			runs.add(current);
		}

		return runs;
	}

	private static String loot(Pos pos, Drops drops) {
		String source = "mine " + coords(pos) + " mainhand";

		return switch (drops) {
			case INVENTORY -> "loot give @s " + source;
			case AT_BLOCK -> "loot spawn " + middle(pos.x()) + " " + middle(pos.y()) + " " + middle(pos.z()) + " " + source;
		};
	}

	/**
	 * The middle of a block along one axis, written by hand: a double would print 12500000.5 as
	 * "1.25000005E7", which the command parser refuses.
	 */
	static String middle(int coordinate) {
		long twice = 2L * coordinate + 1;
		return (twice < 0 ? "-" : "") + Math.abs(twice) / 2 + ".5";
	}

	private static String coords(Pos pos) {
		return pos.x() + " " + pos.y() + " " + pos.z();
	}
}
