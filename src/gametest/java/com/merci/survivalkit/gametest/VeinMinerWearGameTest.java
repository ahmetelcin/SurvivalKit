package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.merci.survivalkit.vein.Trigger;
import net.minecraft.world.level.block.Blocks;

/**
 * Damar Kırıcı wears the tool in hand through the integrated server's real /item. An iron axe
 * (250) with 12 damage takes a seven-log tree: 19 damage after, exactly a point a log — 12, not
 * any number: from 12 the float sum /item does lands one short without the half point, so this
 * shows the half point is needed. With 7
 * left the same tree would break it, so nothing at all is sent and the axe is as it was; with 8
 * it goes and the axe is left with exactly 1.
 */
public class VeinMinerWearGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			VeinTestSupport stage = new VeinTestSupport(context, world);
			stage.set("tool_wear", true);

			int worn = aLogAPoint(context, stage);
			int byHand = breakingByHandCountsItsOwnPoint(context, stage);
			aToolThatWouldBreakStartsNothing(context, stage);
			int left = negativeControlOneMorePointAndItGoesDownToOne(context, stage);

			SurvivalKit.LOGGER.info("[gametest] Damar Kırıcı alet aşınması doğrulandı (7 kütük: demir balta hasarı 12 -> {}; basılı tutup kırda biri elle, yine 12 -> {}; "
				+ "7 dayanıklılık kala iş başlamadı, hiç komut gitmedi; 8 kala gitti, {} kaldı)", worn, byHand, left);
		}
	}

	private static int aLogAPoint(ClientGameTestContext context, VeinTestSupport stage) {
		List<BlockPos> logs = treeWithAxe(stage, 12);
		int before = stage.sentSoFar();

		stage.pressAndWait();

		if (!stage.stillThere(logs, Blocks.OAK_LOG).isEmpty() || stage.carried(Items.OAK_LOG) != logs.size()) {
			throw new AssertionError("ağaç gitmeli, 7 kütük gelmeliydi; kalan " + stage.stillThere(logs, Blocks.OAK_LOG) + ", gelen " + stage.carried(Items.OAK_LOG));
		}

		if (!stage.sentSince(before).getFirst().startsWith("item modify entity @s hotbar.0 ")) {
			throw new AssertionError("ilk komut aletin aşınması olmalıydı, giden: " + stage.sentSince(before));
		}

		int damage = axeDamage(stage);

		if (damage != 19) {
			throw new AssertionError("baltanın hasarı 12'den tam 19'a çıkmalıydı, olan: " + damage);
		}

		return damage;
	}

	/**
	 * The log under the crosshair broken by hand with the key held: the server wears the axe for that one
	 * itself, the module adds the other six. Still exactly 19 — the module adds to what the server has,
	 * it does not write over the point the server was adding.
	 */
	private static int breakingByHandCountsItsOwnPoint(ClientGameTestContext context, VeinTestSupport stage) {
		List<BlockPos> logs = treeWithAxe(stage, 12);
		stage.set("mode", Trigger.HOLD_AND_BREAK);

		try {
			stage.dig(stage.target(stage.base.getX(), stage.base.getZ()), true);
			stage.waitUntilDone();
		} finally {
			stage.set("mode", Trigger.LOOK_AND_PRESS);
		}

		if (!stage.stillThere(logs, Blocks.OAK_LOG).isEmpty()) {
			throw new AssertionError("ağaç gitmeliydi, kalan: " + stage.stillThere(logs, Blocks.OAK_LOG));
		}

		int damage = axeDamage(stage);

		if (damage != 19) {
			throw new AssertionError("elle kırılan dahil baltanın hasarı 12'den tam 19'a çıkmalıydı, olan: " + damage);
		}

		return damage;
	}

	private static void aToolThatWouldBreakStartsNothing(ClientGameTestContext context, VeinTestSupport stage) {
		List<BlockPos> logs = treeWithAxe(stage, 243);
		stage.watchChat();
		int before = stage.sentSoFar();

		stage.pressAndWait();

		if (stage.stillThere(logs, Blocks.OAK_LOG).size() != logs.size() || stage.sentSoFar() != before) {
			throw new AssertionError("alet yetmezken hiçbir şey kırılmamalıydı; kalan " + stage.stillThere(logs, Blocks.OAK_LOG).size()
				+ "/" + logs.size() + ", giden: " + stage.sentSince(before));
		}

		String line = context.computeOnClient(client -> Lang.of(Keys.VEIN_TOOL_TOO_WORN, 7, 7));

		if (stage.linesSaying(line) != 1) {
			throw new AssertionError("sohbette \"" + line + "\" bir kez olmalıydı, olan: " + stage.linesSaying(line));
		}

		if (axeDamage(stage) != 243) {
			throw new AssertionError("reddedilen iş baltaya dokunmamalıydı, hasar: " + axeDamage(stage));
		}
	}

	private static int negativeControlOneMorePointAndItGoesDownToOne(ClientGameTestContext context, VeinTestSupport stage) {
		List<BlockPos> logs = treeWithAxe(stage, 242);

		stage.pressAndWait();

		if (!stage.stillThere(logs, Blocks.OAK_LOG).isEmpty()) {
			throw new AssertionError("negatif kontrol başarısız: 8 dayanıklılık kala ağaç gitmeliydi");
		}

		int left = stage.computeOnServer(server -> {
			ItemStack axe = server.getPlayerList().getPlayers().getFirst().getInventory().getItem(0);
			return axe.is(Items.IRON_AXE) ? axe.getMaxDamage() - axe.getDamageValue() : -1;
		});

		if (left != 1) {
			throw new AssertionError("balta tam 1 dayanıklılıkla kalmalıydı (kırılmadan), olan: " + left);
		}

		return left;
	}

	/** A seven-log tree in front and an iron axe with this much damage in hand. */
	private static List<BlockPos> treeWithAxe(VeinTestSupport stage, int damage) {
		stage.clear("minecraft:iron_axe[minecraft:damage=" + damage + "]");
		int x = stage.base.getX();
		int z = stage.base.getZ();
		List<BlockPos> logs = stage.tree(x, z);
		stage.standFacing(x, z);
		return logs;
	}

	/** The axe's damage in hotbar slot 0, read on the server; -1 if it is not there. */
	private static int axeDamage(VeinTestSupport stage) {
		return stage.computeOnServer(server -> {
			ItemStack axe = server.getPlayerList().getPlayers().getFirst().getInventory().getItem(0);
			return axe.is(Items.IRON_AXE) ? axe.getDamageValue() : -1;
		});
	}
}
