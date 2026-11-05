package com.merci.survivalkit.vein;

import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * What vanilla's ores drop, for a server that will not run /loot: there the drops are rolled
 * here and handed over with /give. Copied from the game's own data (26.2,
 * {@code data/minecraft/loot_table/blocks/*.json} and the ores' XP in {@code Blocks}), and the
 * dice are thrown the way {@code ApplyBonusCount} throws them. A gametest reads the running
 * game's tables and holds this copy to them, so a new Minecraft version that changes a number
 * fails the build instead of drifting quietly.
 *
 * <p>Only what Fortune changes is here. Logs and ancient debris drop themselves whatever the
 * tool, so the server's own /fill … destroy gives them exactly.
 */
public final class OreDrops {
	/** How Fortune grows the count. */
	public enum Bonus {
		/** {@code minecraft:ore_drops}: count × (1 + max(0, rand(fortune + 2) − 1)). */
		ORE_DROPS,
		/** {@code minecraft:uniform_bonus_count}, multiplier 1: count + rand(fortune + 1). Redstone. */
		UNIFORM_BONUS
	}

	/**
	 * One ore's drop without Silk Touch.
	 *
	 * @param min   {@code set_count}'s lower end, or 1 when the table has none
	 * @param max   its upper end, inclusive
	 * @param xpMin the experience the ore gives, lower end
	 * @param xpMax upper end, inclusive
	 */
	public record Drop(String item, int min, int max, Bonus bonus, int xpMin, int xpMax) {
		/** One block's worth, rolled. */
		public int roll(RandomGenerator random, int fortune) {
			int count = between(random, min, max);

			return switch (bonus) {
				case ORE_DROPS -> fortune > 0 ? count * (Math.max(0, random.nextInt(fortune + 2) - 1) + 1) : count;
				case UNIFORM_BONUS -> count + random.nextInt(fortune + 1);
			};
		}

		public int rollXp(RandomGenerator random) {
			return between(random, xpMin, xpMax);
		}
	}

	private static final Map<String, Drop> BY_FAMILY = Map.of(
		"minecraft:coal_ore", new Drop("minecraft:coal", 1, 1, Bonus.ORE_DROPS, 0, 2),
		"minecraft:iron_ore", new Drop("minecraft:raw_iron", 1, 1, Bonus.ORE_DROPS, 0, 0),
		"minecraft:copper_ore", new Drop("minecraft:raw_copper", 2, 5, Bonus.ORE_DROPS, 0, 0),
		"minecraft:gold_ore", new Drop("minecraft:raw_gold", 1, 1, Bonus.ORE_DROPS, 0, 0),
		"minecraft:redstone_ore", new Drop("minecraft:redstone", 4, 5, Bonus.UNIFORM_BONUS, 1, 5),
		"minecraft:emerald_ore", new Drop("minecraft:emerald", 1, 1, Bonus.ORE_DROPS, 3, 7),
		"minecraft:lapis_ore", new Drop("minecraft:lapis_lazuli", 4, 9, Bonus.ORE_DROPS, 2, 5),
		"minecraft:diamond_ore", new Drop("minecraft:diamond", 1, 1, Bonus.ORE_DROPS, 3, 7),
		"minecraft:nether_gold_ore", new Drop("minecraft:gold_nugget", 2, 6, Bonus.ORE_DROPS, 0, 1),
		"minecraft:nether_quartz_ore", new Drop("minecraft:quartz", 1, 1, Bonus.ORE_DROPS, 2, 5));

	private OreDrops() {
	}

	/** The drop of an ore, its deepslate twin included; empty for anything not in the table. */
	public static Optional<Drop> of(String blockId) {
		return Optional.ofNullable(BY_FAMILY.get(VeinScan.family(blockId)));
	}

	/** The ores the table knows, deepslate twins not listed separately. */
	public static Map<String, Drop> all() {
		return BY_FAMILY;
	}

	/** {@code Mth.nextInt}: inclusive at both ends. */
	private static int between(RandomGenerator random, int min, int max) {
		return min >= max ? min : random.nextInt(max - min + 1) + min;
	}
}
