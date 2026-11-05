package com.merci.survivalkit.vein;

import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * The wear a job puts on the tool in hand, by vanilla's own rule for breaking a block with it
 * ({@code Item.mineBlock}): the tool's damage per block for each block, and with Unbreaking each
 * point is skipped with the chance level / (level + 1), as the enchantment's {@code remove_binomial}
 * does for anything that is not armour. Rolled here, like Fortune without /loot, and added to the
 * tool with /item.
 *
 * <p>The tool never breaks: a job whose wear would leave less than one point does not start.
 */
public final class ToolWear {
	private ToolWear() {
	}

	public static int roll(int blocks, HeldTool tool, RandomGenerator random) {
		int wear = 0;

		for (int point = 0; point < blocks * tool.damagePerBlock(); point++) {
			if (tool.unbreaking() <= 0 || random.nextInt(tool.unbreaking() + 1) == 0) {
				wear++;
			}
		}

		return wear;
	}

	/**
	 * Whether the tool takes this and still has at least one point left.
	 *
	 * @param pending wear the server is about to add on its own — the block the player broke by hand
	 *                to set the job off, which the client has not heard about yet
	 */
	public static boolean affordable(HeldTool tool, int wear, int pending) {
		return tool.left() - pending - wear >= 1;
	}

	/**
	 * Adds the wear to whatever damage the tool has on the server when the command arrives, so a
	 * point the server is still adding for a block broken by hand is not overwritten. /item adds it
	 * as a share of the durability and rounds down ({@code SetItemDamageFunction}); half a point
	 * more makes the rounding land exactly on the whole point.
	 */
	public static String command(HeldTool tool, int wear) {
		double share = (wear + 0.5) / tool.maxDamage();
		return "item modify entity @s hotbar." + tool.slot()
			+ " {type:\"minecraft:set_damage\",damage:-" + String.format(Locale.ROOT, "%.9f", share) + ",add:true}";
	}
}
