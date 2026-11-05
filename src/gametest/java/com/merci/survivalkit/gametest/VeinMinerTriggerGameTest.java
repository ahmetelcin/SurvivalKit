package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.vein.Trigger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Damar Kırıcı's "hold and break" trigger and its outline, with the real keys: the trigger key
 * held while attack is held on a log until it breaks, the way a player does it.
 */
public class VeinMinerTriggerGameTest implements FabricClientGameTest {
	private static final String AXE = "minecraft:iron_axe";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			VeinTestSupport stage = new VeinTestSupport(context, world);
			stage.set("mode", Trigger.HOLD_AND_BREAK);

			int byCommand = breakingALogWithTheKeyHeldTakesTheTree(context, stage);
			breakingALogWithoutTheKeyTakesOnlyThatLog(context, stage);
			Path screenshot = holdingTheKeyOutlinesTheTreeAndNothingElse(context, stage);

			SurvivalKit.LOGGER.info("[gametest] Damar Kırıcı tetiği doğrulandı (tuş basılıyken bir kütük elle kırıldı, kalan {} kütük komutla gitti, "
				+ "kırılan kütük tekrar gönderilmedi; tuşsuz kırınca yalnız o kütük gitti; tuş basılıyken ağacın 7 kütüğü çerçevelendi, "
				+ "tuş bırakınca, ev için ve ayar kapalıyken çerçeve yok; ekran görüntüsü: {})", byCommand, screenshot);
		}
	}

	/** @return how many logs went by command */
	private int breakingALogWithTheKeyHeldTakesTheTree(ClientGameTestContext context, VeinTestSupport stage) {
		stage.clear(AXE);
		int x = stage.base.getX();
		int z = stage.base.getZ();
		List<BlockPos> logs = stage.tree(x, z);
		BlockPos target = stage.target(x, z);
		stage.standFacing(x, z);
		stage.assertLookingAt(target);
		int before = stage.sentSoFar();

		stage.dig(target, true);
		stage.waitUntilDone();

		if (!stage.stillThere(logs, Blocks.OAK_LOG).isEmpty()) {
			throw new AssertionError("tuş basılıyken bir kütük kırılınca bütün ağaç gitmeliydi, kalan: " + stage.stillThere(logs, Blocks.OAK_LOG));
		}

		List<String> sent = new ArrayList<>(stage.sentSince(before));
		long loots = sent.stream().filter(command -> command.startsWith("loot ")).count();
		String mineTarget = "mine " + target.getX() + " " + target.getY() + " " + target.getZ() + " ";

		if (loots != logs.size() - 1 || sent.stream().anyMatch(command -> command.contains(mineTarget))) {
			throw new AssertionError("elle kırılan dışındaki " + (logs.size() - 1) + " kütük için /loot gitmeliydi, kırılan için hiç; giden: " + sent);
		}

		int total = stage.carried(Items.OAK_LOG) + stage.onTheGround(Items.OAK_LOG);

		if (total != logs.size()) {
			throw new AssertionError(logs.size() + " kütüğün hepsi gelmeliydi (komutla gelenler ve elle kırılanın düşürdüğü), gelen: " + total);
		}

		return (int) loots;
	}

	/** Negative control: the same dig without the trigger key. One log goes, the game's own doing. */
	private void breakingALogWithoutTheKeyTakesOnlyThatLog(ClientGameTestContext context, VeinTestSupport stage) {
		stage.clear(AXE);
		int x = stage.base.getX();
		int z = stage.base.getZ();
		List<BlockPos> logs = stage.tree(x, z);
		BlockPos target = stage.target(x, z);
		stage.standFacing(x, z);
		int before = stage.sentSoFar();

		stage.dig(target, false);
		context.waitTicks(40);

		if (stage.stillThere(logs, Blocks.OAK_LOG).size() != logs.size() - 1 || stage.sentSoFar() != before) {
			throw new AssertionError("negatif kontrol başarısız: tuşsuz kırınca yalnız o kütük gitmeliydi; kalan "
				+ stage.stillThere(logs, Blocks.OAK_LOG).size() + "/" + logs.size() + ", giden komut " + (stage.sentSoFar() - before));
		}
	}

	/**
	 * The frames the game is about to draw, read off its gizmos: all seven logs while the key is
	 * held, none once it is let go, none on a log house, none with the setting off.
	 *
	 * @return the screenshot taken with the frames up
	 */
	private Path holdingTheKeyOutlinesTheTreeAndNothingElse(ClientGameTestContext context, VeinTestSupport stage) {
		stage.clear(AXE);
		int x = stage.base.getX();
		int y = stage.base.getY();
		int z = stage.base.getZ();
		Set<BlockPos> tree = new HashSet<>(stage.tree(x, z));

		for (int dx = 3; dx <= 5; dx++) {
			for (int dy = 0; dy <= 2; dy++) {
				stage.put(new BlockPos(x + dx, y + dy, z), "minecraft:oak_log");
			}
		}

		context.waitTicks(3);
		stage.standFacing(x, z);

		if (!stage.outlinedOnScreen().isEmpty()) {
			throw new AssertionError("tuşa basılmadan çerçeve olmamalıydı: " + stage.outlinedOnScreen());
		}

		stage.holdTrigger(true);
		Set<BlockPos> outlined = stage.outlinedOnScreen();
		Path screenshot = context.takeScreenshot("damar-kirici-cerceve");
		stage.holdTrigger(false);

		if (!outlined.equals(tree)) {
			throw new AssertionError("tuş basılıyken ağacın 7 kütüğü çerçevelenmeliydi; çerçeveli: " + outlined + ", ağaç: " + tree);
		}

		if (!stage.outlinedOnScreen().isEmpty()) {
			throw new AssertionError("tuş bırakınca çerçeve kalkmalıydı: " + stage.outlinedOnScreen());
		}

		stage.standFacing(x + 4, z);
		stage.holdTrigger(true);
		Set<BlockPos> onTheHouse = stage.outlinedOnScreen();
		stage.holdTrigger(false);

		if (!onTheHouse.isEmpty()) {
			throw new AssertionError("yapraksız kütük evinde çerçeve olmamalıydı (gitmeyecek): " + onTheHouse);
		}

		stage.standFacing(x, z);
		stage.set("show_outline", false);
		stage.holdTrigger(true);
		Set<BlockPos> settingOff = stage.outlinedOnScreen();
		stage.holdTrigger(false);
		stage.reset("show_outline");

		if (!settingOff.isEmpty()) {
			throw new AssertionError("ayar kapalıyken çerçeve olmamalıydı: " + settingOff);
		}

		return screenshot;
	}
}
