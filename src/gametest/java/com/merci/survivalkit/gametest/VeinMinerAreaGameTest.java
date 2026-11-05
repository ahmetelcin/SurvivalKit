package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.area.WithoutArea;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Damar Kırıcı inside Yapı Modu's area. The area is marked with the wand for real — the lower
 * half of a tree's trunk — and the tree is taken with the real key: only the logs inside go, the
 * rest stands. With Yapı Modu off the same press takes the rest. And set to work nowhere without
 * an area, it does nothing until there is one.
 */
public class VeinMinerAreaGameTest implements FabricClientGameTest {
	private static final String AXE = "minecraft:iron_axe";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			VeinTestSupport stage = new VeinTestSupport(context, world);

			onlyTheLogsInsideTheAreaGo(context, stage);
			withNoAreaAndNowhereNothingGoes(context, stage);

			SurvivalKit.LOGGER.info("[gametest] Damar Kırıcı alanda doğrulandı (yarısı alanda olan ağaçta 3 kütük gitti, 4'ü kaldı; "
				+ "Yapı Modu kapanınca kalanlar gitti; alan yokken 'hiç çalışma' hiçbir şey kırmadı, 'her yerde' kırdı)");
		}
	}

	private static void onlyTheLogsInsideTheAreaGo(ClientGameTestContext context, VeinTestSupport stage) {
		stage.clear(AXE);
		stage.command("item replace entity @a hotbar.1 with minecraft:wooden_sword");
		int x = stage.base.getX();
		int z = stage.base.getZ();
		List<BlockPos> logs = stage.tree(x, z);
		stage.standFacing(x, z);

		// The wand, the area: from the trunk's foot to its third log.
		BuildTestSupport.enable(context, true);
		BuildTestSupport.type(context, "bolge temizle");
		context.getInput().pressKey(options -> options.keyHotbarSlots[1]);
		context.waitTicks(3);
		BuildTestSupport.holdLeft(context, logs.get(0), 2);
		BuildTestSupport.rightClick(context, logs.get(2));
		context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
		context.waitTicks(3);

		if (context.computeOnClient(client -> BuildTestSupport.module().area()).isEmpty()) {
			throw new AssertionError("hazırlık: değnekle alan seçilmedi");
		}

		List<BlockPos> inside = logs.subList(0, 3);
		List<BlockPos> outside = logs.subList(3, logs.size());

		if (stage.stillThere(logs, Blocks.OAK_LOG).size() != logs.size()) {
			throw new AssertionError("hazırlık: değnekle tıklamak kütük kırmamalıydı");
		}

		BuildTestSupport.lookAt(context, logs.get(1));
		stage.pressAndWait();

		if (!stage.stillThere(inside, Blocks.OAK_LOG).isEmpty()) {
			throw new AssertionError("alanın içindeki kütükler gitmeliydi, kalan: " + stage.stillThere(inside, Blocks.OAK_LOG));
		}

		if (stage.stillThere(outside, Blocks.OAK_LOG).size() != outside.size()) {
			throw new AssertionError("alanın dışındaki kütükler kalmalıydı, kalan " + stage.stillThere(outside, Blocks.OAK_LOG).size() + "/" + outside.size());
		}

		if (stage.carried(Items.OAK_LOG) != inside.size()) {
			throw new AssertionError(inside.size() + " kütük envantere gelmeliydi, gelen: " + stage.carried(Items.OAK_LOG));
		}

		// Negative control: the same press on what is left, with Yapı Modu off. The area is still
		// stored, but holds nothing back, and the rest of the tree goes.
		BuildTestSupport.enable(context, false);
		BuildTestSupport.lookAt(context, logs.get(3));
		stage.pressAndWait();

		if (!stage.stillThere(outside, Blocks.OAK_LOG).isEmpty()) {
			throw new AssertionError("negatif kontrol başarısız: Yapı Modu kapalıyken ağacın kalanı gitmeliydi, kalan: " + stage.stillThere(outside, Blocks.OAK_LOG));
		}
	}

	private static void withNoAreaAndNowhereNothingGoes(ClientGameTestContext context, VeinTestSupport stage) {
		stage.clear(AXE);
		int x = stage.base.getX();
		int z = stage.base.getZ();
		List<BlockPos> logs = stage.tree(x, z);
		stage.standFacing(x, z);
		stage.set("without_area", WithoutArea.NOWHERE);
		stage.watchChat();
		int before = stage.sentSoFar();

		stage.pressAndWait();

		if (stage.stillThere(logs, Blocks.OAK_LOG).size() != logs.size() || stage.sentSoFar() != before) {
			throw new AssertionError("alan yokken 'hiç çalışma' hiçbir şey kırmamalıydı; kalan " + stage.stillThere(logs, Blocks.OAK_LOG).size()
				+ "/" + logs.size() + ", giden komut " + (stage.sentSoFar() - before));
		}

		String line = context.computeOnClient(client -> Lang.of(Keys.VEIN_NO_AREA));

		if (stage.linesSaying(line) != 1) {
			throw new AssertionError("sohbette alan yok satırı bir kez olmalıydı, olan: " + stage.linesSaying(line));
		}

		stage.reset("without_area");
		stage.pressAndWait();

		if (!stage.stillThere(logs, Blocks.OAK_LOG).isEmpty()) {
			throw new AssertionError("negatif kontrol başarısız: 'her yerde çalış' ile ağaç gitmeliydi");
		}
	}
}
