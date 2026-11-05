package com.merci.survivalkit.vein;

/**
 * Damar Kırıcı's price: one hunger point for every so many blocks it breaks, paid through the
 * server's own Hunger effect, so the server takes it the way it takes a sprint or a jump.
 *
 * <p>Blocks left over from a job are carried to the next: at ten blocks a point, a vein of three
 * costs nothing yet, and the fourth such vein pays for the first ten. The carry lives as long as
 * the module stays on and the connection lasts.
 *
 * <p>A point is what the game drains in one go once the exhaustion passes 4: saturation first,
 * then the food bar. The effect gives 4 per point and a hair over, because the game only drains
 * past 4, never at it; the hair stays under one point even with exhaustion the player already had
 * of up to 3.
 */
public final class HungerCost {
	/**
	 * The food bar never goes below this for a job: at 6 and under the player cannot sprint, which
	 * is where being hungry starts to matter.
	 */
	public static final int LEAST_FOOD_LEFT = 7;

	/** Hunger gives 0.005 exhaustion a tick per level (amplifier + 1): 0.1 a second. Units of 0.1 per point. */
	private static final int UNITS_PER_POINT = 40;

	/** 0.5 exhaustion over, so the last point is past 4 and not at it, whatever the float sums do. */
	private static final int UNITS_OVER = 5;

	private static final int MAX_LEVEL = 256;

	private int carried;

	/**
	 * The command and how long its effect runs.
	 *
	 * @param seconds the effect's length; the drain is done when it ends
	 */
	public record Charge(String command, int seconds) {
	}

	/** How many points this many blocks cost now, carry included. Changes nothing. */
	public int points(int blocks, int blocksPerPoint) {
		if (blocksPerPoint <= 0) {
			return 0;
		}

		return (carried + blocks) / blocksPerPoint;
	}

	/** The job went ahead: what was paid for is gone, the rest is carried. */
	public void pay(int blocks, int blocksPerPoint) {
		if (blocksPerPoint > 0) {
			carried = (carried + blocks) % blocksPerPoint;
		}
	}

	public void reset() {
		carried = 0;
	}

	/**
	 * Whether the bar can pay without falling under {@link #LEAST_FOOD_LEFT}. Saturation goes
	 * first, a whole point for any part of one, as the game drains it.
	 */
	public static boolean affordable(int food, float saturation, int points) {
		int fromFood = Math.max(0, points - (int) Math.ceil(saturation));
		return food - fromFood >= LEAST_FOOD_LEFT;
	}

	/**
	 * /effect for exactly this many points: the shortest effect that does it, a level no higher
	 * than the game allows. Particles hidden: it is a price, not a status.
	 */
	public static Charge charge(int points) {
		if (points <= 0) {
			throw new IllegalArgumentException("en az bir puan: " + points);
		}

		int units = points * UNITS_PER_POINT + UNITS_OVER;
		int seconds = Math.ceilDiv(units, MAX_LEVEL);
		int level = Math.ceilDiv(units, seconds);
		return new Charge("effect give @s minecraft:hunger " + seconds + " " + (level - 1) + " true", seconds);
	}
}
