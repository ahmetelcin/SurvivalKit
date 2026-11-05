package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.merci.survivalkit.vein.Drops;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Damar Kırıcı on trees, with its real key, against the integrated server's real commands:
 * the whole tree goes and its logs arrive; a log house beside it does not; nothing at all
 * happens without the commands; and the rate is kept.
 */
public class VeinMinerTreeGameTest implements FabricClientGameTest {
	private static final String AXE = "minecraft:iron_axe";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			VeinTestSupport stage = new VeinTestSupport(context, world);

			int quietReplies = theWholeTreeGoesIntoTheInventory(context, stage);
			int loudReplies = atTheBlockTheLogsLieOnTheGround(context, stage);
			theLogHouseBesideTheTreeIsLeftAlone(context, stage);
			withoutTheCommandsNothingGoesAndTheWarningComesOnce(context, stage, world);
			List<Integer> perTick = theRateIsNeverExceeded(context, stage);

			SurvivalKit.LOGGER.info("[gametest] Damar Kırıcı ağaçta doğrulandı (7 kütük gitti ve envantere geldi, sohbette {} başarı yanıtı; "
				+ "bloğun yerine modunda 7 kütük yerde, gizleme kapalıyken {} yanıt göründü; yapraksız kütük evi korundu, ayar kapalıyken gitti; "
				+ "yetkisizken hiçbir blok gitmedi, komut gitmedi, uyarı bir kez; saniyede 5 sınırında tick başına komutlar {})",
				quietReplies, loudReplies, perTick);
		}
	}

	/** @return the success replies that reached chat, which should be none */
	private int theWholeTreeGoesIntoTheInventory(ClientGameTestContext context, VeinTestSupport stage) {
		stage.clear(AXE);
		int x = stage.base.getX();
		int z = stage.base.getZ();
		List<BlockPos> logs = stage.tree(x, z);
		stage.standFacing(x, z);
		stage.assertLookingAt(stage.target(x, z));
		stage.watchChat();

		stage.pressAndWait();

		if (!stage.stillThere(logs, Blocks.OAK_LOG).isEmpty()) {
			throw new AssertionError("ağacın bütün kütükleri gitmeliydi, kalan: " + stage.stillThere(logs, Blocks.OAK_LOG));
		}

		if (stage.carried(Items.OAK_LOG) != logs.size()) {
			throw new AssertionError("envantere " + logs.size() + " meşe kütüğü gelmeliydi, gelen: " + stage.carried(Items.OAK_LOG));
		}

		int replies = stage.successReplies();

		if (replies != 0) {
			throw new AssertionError("başarı yanıtları sohbete düşmemeliydi, düşen: " + replies);
		}

		return replies;
	}

	/**
	 * The other drops setting, and with the replies let through: the second half is the
	 * negative control for the hiding above — the replies were there to hide.
	 *
	 * @return the success replies that reached chat
	 */
	private int atTheBlockTheLogsLieOnTheGround(ClientGameTestContext context, VeinTestSupport stage) {
		stage.clear(AXE);
		int x = stage.base.getX();
		int z = stage.base.getZ();
		List<BlockPos> logs = stage.tree(x, z);
		int y = stage.base.getY();
		// A floor for the logs to land on, and every other slot full so none can be picked up:
		// what is counted on the ground is then everything that dropped.
		stage.command("fill " + (x - 2) + " " + (y - 1) + " " + (z - 2) + " " + (x + 2) + " " + (y - 1) + " " + (z + 2) + " minecraft:stone");
		stage.fillInventoryExceptHand("minecraft:cobblestone 64");
		stage.standFacing(x, z);
		stage.set("drops", Drops.AT_BLOCK);
		stage.set("hide_feedback", false);
		stage.watchChat();

		stage.pressAndWait();
		stage.reset("drops");
		stage.reset("hide_feedback");

		if (!stage.stillThere(logs, Blocks.OAK_LOG).isEmpty()) {
			throw new AssertionError("ağacın bütün kütükleri gitmeliydi, kalan: " + stage.stillThere(logs, Blocks.OAK_LOG));
		}

		if (stage.onTheGround(Items.OAK_LOG) != logs.size() || stage.carried(Items.OAK_LOG) != 0) {
			throw new AssertionError(logs.size() + " kütük yerde olmalıydı, envanterde değil; yerde " + stage.onTheGround(Items.OAK_LOG)
				+ ", envanterde " + stage.carried(Items.OAK_LOG));
		}

		int replies = stage.successReplies();

		if (replies == 0) {
			throw new AssertionError("negatif kontrol başarısız: gizleme kapalıyken başarı yanıtları sohbette görünmeliydi");
		}

		return replies;
	}

	/**
	 * A tree and, two blocks east of it, a wall of the same logs with no leaves. The tree goes
	 * and the wall stays; looking at the wall itself does nothing; and with the setting off
	 * the wall goes too, which shows it was the setting that kept it.
	 */
	private void theLogHouseBesideTheTreeIsLeftAlone(ClientGameTestContext context, VeinTestSupport stage) {
		stage.clear(AXE);
		int x = stage.base.getX();
		int y = stage.base.getY();
		int z = stage.base.getZ();
		List<BlockPos> tree = stage.tree(x, z);
		List<BlockPos> house = new ArrayList<>();

		for (int dx = 3; dx <= 5; dx++) {
			for (int dy = 0; dy <= 2; dy++) {
				BlockPos log = new BlockPos(x + dx, y + dy, z);
				house.add(log);
				stage.put(log, "minecraft:oak_log");
			}
		}

		context.waitTicks(3);
		stage.standFacing(x, z);
		stage.pressAndWait();

		if (!stage.stillThere(tree, Blocks.OAK_LOG).isEmpty()) {
			throw new AssertionError("ağaç gitmeliydi, kalan: " + stage.stillThere(tree, Blocks.OAK_LOG));
		}

		if (stage.stillThere(house, Blocks.OAK_LOG).size() != house.size()) {
			throw new AssertionError("ağacın yanındaki kütük evi korunmalıydı, kalan " + stage.stillThere(house, Blocks.OAK_LOG).size() + "/" + house.size());
		}

		stage.standFacing(x + 4, z);
		stage.assertLookingAt(stage.target(x + 4, z));
		int before = stage.sentSoFar();
		stage.pressAndWait();

		if (stage.stillThere(house, Blocks.OAK_LOG).size() != house.size() || stage.sentSoFar() != before) {
			throw new AssertionError("yapraksız kütük yığınına dokunulmamalıydı; kalan " + stage.stillThere(house, Blocks.OAK_LOG).size()
				+ "/" + house.size() + ", giden komut " + (stage.sentSoFar() - before));
		}

		stage.set("natural_trees_only", false);
		stage.pressAndWait();
		stage.reset("natural_trees_only");

		if (!stage.stillThere(house, Blocks.OAK_LOG).isEmpty()) {
			throw new AssertionError("negatif kontrol başarısız: ayar kapalıyken kütük yığını gitmeliydi, kalan " + stage.stillThere(house, Blocks.OAK_LOG).size());
		}
	}

	/**
	 * The world's cheats switched off, which takes /setblock, /fill and /loot out of the
	 * command tree the server sends. Two presses: no block goes, no command leaves, and the
	 * warning is in chat once. Then the cheats come back and the same press takes the tree, so
	 * it was the permission that stopped it.
	 */
	private void withoutTheCommandsNothingGoesAndTheWarningComesOnce(ClientGameTestContext context, VeinTestSupport stage, TestSingleplayerContext world) {
		stage.clear(AXE);
		int x = stage.base.getX();
		int z = stage.base.getZ();
		List<BlockPos> logs = stage.tree(x, z);
		stage.standFacing(x, z);

		stage.allowCommands(false);

		try {
			stage.watchChat();
			int before = stage.sentSoFar();
			stage.pressAndWait();
			stage.pressAndWait();
			context.waitTicks(20);

			if (stage.stillThere(logs, Blocks.OAK_LOG).size() != logs.size()) {
				throw new AssertionError("yetki yokken hiçbir blok gitmemeliydi, kalan " + stage.stillThere(logs, Blocks.OAK_LOG).size() + "/" + logs.size());
			}

			if (stage.sentSoFar() != before) {
				throw new AssertionError("yetki yokken hiç komut gitmemeliydi, giden: " + (stage.sentSoFar() - before));
			}

			String warning = context.computeOnClient(client -> Lang.of(Keys.VEIN_NO_PERMISSION));

			if (stage.linesSaying(warning) != 1) {
				throw new AssertionError("iki basışta uyarı sohbette bir kez olmalıydı, olan: " + stage.linesSaying(warning));
			}
		} finally {
			stage.allowCommands(true);
		}

		stage.pressAndWait();

		if (!stage.stillThere(logs, Blocks.OAK_LOG).isEmpty()) {
			throw new AssertionError("negatif kontrol başarısız: yetki geri gelince ağaç gitmeliydi, kalan " + stage.stillThere(logs, Blocks.OAK_LOG).size());
		}
	}

	/**
	 * Five a second against a tree that needs nine commands (seven /loot, two /fill). Every
	 * tick the count of commands sent is written down; no 20 ticks in a row may hold more
	 * than five, and the nine take more than a second.
	 */
	private List<Integer> theRateIsNeverExceeded(ClientGameTestContext context, VeinTestSupport stage) {
		final int rate = 5;
		stage.clear(AXE);
		int x = stage.base.getX();
		int z = stage.base.getZ();
		List<BlockPos> logs = stage.tree(x, z);
		stage.standFacing(x, z);
		stage.set("commands_per_second", rate);

		List<Integer> perTick = new ArrayList<>();

		try {
			int start = stage.sentSoFar();
			int last = start;
			// Held and let go by hand rather than with a helper that waits: every tick from the
			// press on has to be written down, one entry per tick.
			context.getInput().holdKey(InputConstants.getKey(VeinTestSupport.KEY));

			for (int tick = 0; tick < 600 && (stage.busy() || perTick.size() < 5); tick++) {
				if (tick == 2) {
					context.getInput().releaseKey(InputConstants.getKey(VeinTestSupport.KEY));
				}

				int now = stage.sentSoFar();
				perTick.add(now - last);
				last = now;
				context.waitTick();
			}

			perTick.add(stage.sentSoFar() - last);
			context.waitTicks(10);

			int total = stage.sentSoFar() - start;

			if (total != logs.size() + 2) {
				throw new AssertionError("7 /loot ve 2 /fill gitmeliydi, giden: " + total + " " + perTick);
			}
		} finally {
			stage.reset("commands_per_second");
		}

		for (int from = 0; from < perTick.size(); from++) {
			int inSecond = perTick.subList(from, Math.min(from + 20, perTick.size())).stream().mapToInt(Integer::intValue).sum();

			if (inSecond > rate) {
				throw new AssertionError("bir saniyede " + inSecond + " komut gitti, sınır " + rate + ": " + perTick);
			}
		}

		if (!stage.stillThere(logs, Blocks.OAK_LOG).isEmpty()) {
			throw new AssertionError("hız sınırıyla da ağaç sonunda gitmeliydi, kalan: " + stage.stillThere(logs, Blocks.OAK_LOG));
		}

		return perTick;
	}
}
