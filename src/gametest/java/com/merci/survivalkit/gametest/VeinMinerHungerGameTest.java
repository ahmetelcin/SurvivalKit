package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.merci.survivalkit.vein.HungerCost;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * Damar Kırıcı's hunger cost against the integrated server's real Hunger effect. At two blocks a
 * point a seven-log tree costs three points and carries one block: the food bar goes from 16 to
 * exactly 13. Three, not two: at two points the game's float sum happens to pass 8 even with no
 * margin over 4 a point, at three it does not, so only three shows the margin is needed. Then with
 * 10 food the next tree (seven and the one carried: four points) would leave 6, under the sprint
 * line, so nothing at all is sent; with 11 it goes, down to exactly 7.
 */
public class VeinMinerHungerGameTest implements FabricClientGameTest {
	private static final String AXE = "minecraft:iron_axe";
	private static final int BLOCKS_PER_POINT = 2;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			VeinTestSupport stage = new VeinTestSupport(context, world);
			// The stage feeds the player; here the bar has to move. Peaceful would not drain it.
			stage.command("effect clear @a minecraft:saturation");
			stage.command("difficulty normal");
			stage.set("blocks_per_hunger_point", BLOCKS_PER_POINT);

			float[] paid = theCostComesOffTheBar(context, stage);
			tooHungryNothingIsBroken(context, stage);
			float[] justEnough = negativeControlOneMoreFoodAndItGoes(context, stage);

			SurvivalKit.LOGGER.info("[gametest] Damar Kırıcı açlık bedeli doğrulandı (7 kütük, 2 blokta 1 puan: açlık 16 -> {}, doygunluk {}; "
				+ "açlık 10'da 4 puanlık iş başlamadı, hiç komut gitmedi; 11'de gitti, açlık {})", (int) paid[0], paid[1], (int) justEnough[0]);
		}
	}

	private static float[] theCostComesOffTheBar(ClientGameTestContext context, VeinTestSupport stage) {
		List<BlockPos> logs = treeInFront(stage);
		stage.setHunger(16, 0.0f);
		int before = stage.sentSoFar();

		stage.pressAndWait();

		if (!stage.stillThere(logs, Blocks.OAK_LOG).isEmpty()) {
			throw new AssertionError("ağaç gitmeliydi, kalan: " + stage.stillThere(logs, Blocks.OAK_LOG));
		}

		List<String> sent = stage.sentSince(before);
		String effect = HungerCost.charge(3).command();

		if (!sent.getFirst().equals(effect) || sent.stream().filter(command -> command.startsWith("effect ")).count() != 1) {
			throw new AssertionError("ilk ve tek açlık komutu \"" + effect + "\" olmalıydı, giden: " + sent);
		}

		float[] hunger = stage.hunger();

		if (hunger[0] != 13 || hunger[1] != 0) {
			throw new AssertionError("açlık 16'dan tam 13'e inmeliydi (doygunluk 0), olan: " + Arrays.toString(hunger));
		}

		return hunger;
	}

	private static void tooHungryNothingIsBroken(ClientGameTestContext context, VeinTestSupport stage) {
		List<BlockPos> logs = treeInFront(stage);
		stage.setHunger(10, 0.0f);
		stage.watchChat();
		int before = stage.sentSoFar();

		stage.pressAndWait();

		if (stage.stillThere(logs, Blocks.OAK_LOG).size() != logs.size() || stage.sentSoFar() != before) {
			throw new AssertionError("açlık yetmezken hiçbir şey kırılmamalıydı; kalan " + stage.stillThere(logs, Blocks.OAK_LOG).size()
				+ "/" + logs.size() + ", giden: " + stage.sentSince(before));
		}

		String line = context.computeOnClient(client -> Lang.of(Keys.VEIN_TOO_HUNGRY, 4));

		if (stage.linesSaying(line) != 1) {
			throw new AssertionError("sohbette \"" + line + "\" bir kez olmalıydı, olan: " + stage.linesSaying(line));
		}

		if (stage.hunger()[0] != 10) {
			throw new AssertionError("reddedilen iş açlığa dokunmamalıydı: " + Arrays.toString(stage.hunger()));
		}
	}

	/** The same tree, one food more: it goes, and the bar stops at the line, not under it. */
	private static float[] negativeControlOneMoreFoodAndItGoes(ClientGameTestContext context, VeinTestSupport stage) {
		List<BlockPos> logs = treeInFront(stage);
		stage.setHunger(11, 0.0f);

		stage.pressAndWait();

		if (!stage.stillThere(logs, Blocks.OAK_LOG).isEmpty()) {
			throw new AssertionError("negatif kontrol başarısız: açlık 11'ken ağaç gitmeliydi");
		}

		float[] hunger = stage.hunger();

		if (hunger[0] != HungerCost.LEAST_FOOD_LEFT) {
			throw new AssertionError("açlık 11'den tam " + HungerCost.LEAST_FOOD_LEFT + "'ye inmeliydi, olan: " + Arrays.toString(hunger));
		}

		return hunger;
	}

	private static List<BlockPos> treeInFront(VeinTestSupport stage) {
		stage.clear(AXE);
		int x = stage.base.getX();
		int z = stage.base.getZ();
		List<BlockPos> logs = stage.tree(x, z);
		stage.standFacing(x, z);
		return logs;
	}
}
