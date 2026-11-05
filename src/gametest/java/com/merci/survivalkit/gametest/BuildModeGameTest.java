package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.area.Corners;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.merci.survivalkit.vein.Pos;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Yapı Modu with the real mouse: the wand marks two corners and no block changes, though the left
 * button is held long enough to break dirt and the right one would place planks; the area is
 * drawn. Off, the same clicks break and place and nothing is drawn. /bolge typed into chat marks
 * and clears; another dimension has no area.
 */
public class BuildModeGameTest implements FabricClientGameTest {
	/** Dirt breaks by hand in about 15 ticks; 40 is well past it. */
	private static final int HOLD_TICKS = 40;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			VeinTestSupport stage = new VeinTestSupport(context, world);
			// The stage is Damar Kırıcı's; it has nothing to do here.
			context.runOnClient(client -> VeinTestSupport.module().setEnabled(false));

			int x = stage.base.getX();
			int y = stage.base.getY();
			int z = stage.base.getZ();
			BlockPos first = new BlockPos(x - 1, y, z);
			BlockPos second = new BlockPos(x + 1, y + 1, z + 1);

			theWandMarksTwoCornersAndNothingChanges(context, stage, first, second);
			offTheWandIsAnOrdinarySwordAndNothingIsDrawn(context, stage, first, second);
			theCommandMarksAndClears(context, stage);
			anotherDimensionHasNoArea(context, stage, world);

			SurvivalKit.LOGGER.info("[gametest] Yapı Modu doğrulandı (değnekle iki köşe, {} tick basılı sol tık ve sağ tık hiçbir bloğu değiştirmedi, "
				+ "çerçeve çizildi; kapalıyken aynı tıklar kırdı ve koydu, çerçeve yok; /bolge kose1 ve temizle; Nether'de alan yok)", HOLD_TICKS);
		}
	}

	/** Dirt at both corners, a floor, the sword in hand and planks beside it; the area cleared. */
	private static void stageFor(ClientGameTestContext context, VeinTestSupport stage, BlockPos first, BlockPos second) {
		int x = stage.base.getX();
		int y = stage.base.getY();
		int z = stage.base.getZ();
		stage.clear("minecraft:wooden_sword");
		stage.command("fill " + (x - 2) + " " + (y - 1) + " " + (z - 3) + " " + (x + 2) + " " + (y - 1) + " " + (z + 2) + " minecraft:stone");
		stage.put(first, "minecraft:dirt");
		stage.put(second, "minecraft:dirt");
		stage.command("item replace entity @a hotbar.1 with minecraft:oak_planks 16");
		stage.standFacing(x, z);
		BuildTestSupport.type(context, "bolge temizle");
	}

	private static void theWandMarksTwoCornersAndNothingChanges(ClientGameTestContext context, VeinTestSupport stage, BlockPos first, BlockPos second) {
		BuildTestSupport.enable(context, true);
		stageFor(context, stage, first, second);
		Map<BlockPos, String> before = blocks(context, stage);
		stage.watchChat();

		BuildTestSupport.holdLeft(context, first, HOLD_TICKS);
		BuildTestSupport.rightClick(context, second);

		Corners marked = context.computeOnClient(client -> BuildTestSupport.module().cornersHere());

		if (!new Corners(pos(first), pos(second)).equals(marked)) {
			throw new AssertionError("köşeler " + first + " ve " + second + " olmalıydı, olan: " + marked);
		}

		String areaLine = context.computeOnClient(client -> Lang.of(Keys.BUILD_AREA, text(first), text(second), 3, 2, 2, 12L));
		String cornerLine = context.computeOnClient(client -> Lang.of(Keys.BUILD_CORNER, 1, text(first)));

		if (stage.linesSaying(areaLine) != 1 || stage.linesSaying(cornerLine) != 1) {
			throw new AssertionError("sohbette köşe ve hacim satırı birer kez olmalıydı: \"" + cornerLine + "\" "
				+ stage.linesSaying(cornerLine) + ", \"" + areaLine + "\" " + stage.linesSaying(areaLine));
		}

		Set<AABB> frames = BuildTestSupport.framesOnScreen(context);

		if (!BuildTestSupport.framed(frames, BuildTestSupport.frameOf(first, second))) {
			throw new AssertionError("alanın çerçevesi çizilmeliydi, çizilen: " + frames);
		}

		// Planks as the wand: a right click with them would place one. It marks instead.
		BuildTestSupport.setWand(context, List.of("minecraft:wooden_sword", "minecraft:oak_planks"));
		context.getInput().pressKey(options -> options.keyHotbarSlots[1]);
		context.waitTicks(3);
		BuildTestSupport.rightClick(context, second);
		context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
		context.waitTicks(3);

		Map<BlockPos, String> after = blocks(context, stage);

		if (!before.equals(after)) {
			throw new AssertionError("değnekle tıklamak hiçbir bloğu değiştirmemeliydi: " + changes(before, after));
		}
	}

	/**
	 * The negative control for the one above: the same clicks with the module off. The dirt breaks,
	 * the planks go down, no corner is said, nothing is drawn — though the area is still stored.
	 */
	private static void offTheWandIsAnOrdinarySwordAndNothingIsDrawn(ClientGameTestContext context, VeinTestSupport stage, BlockPos first, BlockPos second) {
		BuildTestSupport.enable(context, false);
		stage.watchChat();

		if (!BuildTestSupport.framesOnScreen(context).isEmpty()) {
			throw new AssertionError("Yapı Modu kapalıyken çerçeve çizilmemeliydi");
		}

		Map<BlockPos, String> before = blocks(context, stage);
		context.getInput().pressKey(options -> options.keyHotbarSlots[1]);
		context.waitTicks(3);
		BuildTestSupport.rightClick(context, second);
		context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
		context.waitTicks(3);
		BuildTestSupport.holdLeft(context, first, HOLD_TICKS);
		Map<BlockPos, String> after = blocks(context, stage);

		BlockPos placed = second.north();

		if (!"minecraft:air".equals(after.get(first)) || !"minecraft:oak_planks".equals(after.get(placed))) {
			throw new AssertionError("negatif kontrol başarısız: kapalıyken sol tık toprağı kırmalı, sağ tık tahta koymalıydı: " + changes(before, after));
		}

		String cornerLine = context.computeOnClient(client -> Lang.of(Keys.BUILD_CORNER, 1, text(first)));

		if (stage.linesSaying(cornerLine) != 0) {
			throw new AssertionError("kapalıyken köşe seçilmemeliydi");
		}

		if (!BuildTestSupport.framesOnScreen(context).isEmpty()) {
			throw new AssertionError("Yapı Modu kapalıyken çerçeve çizilmemeliydi");
		}

		BuildTestSupport.setWand(context, List.of("minecraft:wooden_sword"));
	}

	private static void theCommandMarksAndClears(ClientGameTestContext context, VeinTestSupport stage) {
		BuildTestSupport.enable(context, true);
		BlockPos feet = context.computeOnClient(client -> client.player.blockPosition());

		BuildTestSupport.type(context, "bolge kose1");
		BuildTestSupport.type(context, "bolge kose2");
		Corners marked = context.computeOnClient(client -> BuildTestSupport.module().cornersHere());

		if (!new Corners(pos(feet), pos(feet)).equals(marked)) {
			throw new AssertionError("/bolge kose1 ve kose2 köşeleri ayağının olduğu yere koymalıydı (" + feet + "), olan: " + marked);
		}

		if (!BuildTestSupport.framed(BuildTestSupport.framesOnScreen(context), BuildTestSupport.frameOf(feet, feet))) {
			throw new AssertionError("komutla seçilen alan çizilmeliydi");
		}

		BuildTestSupport.type(context, "bolge temizle");

		if (!context.computeOnClient(client -> BuildTestSupport.module().cornersHere()).isEmpty()
			|| !BuildTestSupport.framesOnScreen(context).isEmpty()) {
			throw new AssertionError("/bolge temizle alanı silmeliydi");
		}
	}

	/** An area in the Overworld; in the Nether there is none and nothing is drawn; back home it is there again. */
	private static void anotherDimensionHasNoArea(ClientGameTestContext context, VeinTestSupport stage, TestSingleplayerContext world) {
		BuildTestSupport.enable(context, true);
		BuildTestSupport.type(context, "bolge kose1");
		BuildTestSupport.type(context, "bolge kose2");
		BlockPos home = context.computeOnClient(client -> client.player.blockPosition());

		stage.command("execute in minecraft:the_nether run setblock " + home.getX() + " 129 " + home.getZ() + " minecraft:stone");
		stage.command("execute in minecraft:the_nether run tp @a " + (home.getX() + 0.5) + " 130 " + (home.getZ() + 0.5));
		waitForDimension(context, Level.NETHER.identifier().toString());
		context.waitTicks(5);

		if (context.computeOnClient(client -> BuildTestSupport.module().area()).isPresent()
			|| !BuildTestSupport.framesOnScreen(context).isEmpty()) {
			throw new AssertionError("Nether'de Overworld'ün alanı geçerli olmamalı, çizilmemeliydi");
		}

		stage.command("execute in minecraft:overworld run tp @a " + (home.getX() + 0.5) + " " + home.getY() + " " + (home.getZ() + 0.5));
		waitForDimension(context, Level.OVERWORLD.identifier().toString());
		context.waitTicks(5);

		if (context.computeOnClient(client -> BuildTestSupport.module().area()).isEmpty()) {
			throw new AssertionError("negatif kontrol başarısız: Overworld'e dönünce alan yine geçerli olmalıydı");
		}

		BuildTestSupport.type(context, "bolge temizle");
	}

	private static void waitForDimension(ClientGameTestContext context, String dimension) {
		for (int tick = 0; tick < 400; tick++) {
			if (context.computeOnClient(client -> client.level != null && client.player != null
				&& client.level.dimension().identifier().toString().equals(dimension))) {
				return;
			}

			context.waitTick();
		}

		throw new AssertionError("hazırlık: " + dimension + " boyutuna geçilmedi");
	}

	/** Every block of the stage, read on the server. */
	private static Map<BlockPos, String> blocks(ClientGameTestContext context, VeinTestSupport stage) {
		int x = stage.base.getX();
		int y = stage.base.getY();
		int z = stage.base.getZ();

		return stage.computeOnServer(server -> {
			Map<BlockPos, String> blocks = new LinkedHashMap<>();

			for (BlockPos pos : BlockPos.betweenClosed(x - 2, y - 1, z - 3, x + 2, y + 3, z + 2)) {
				blocks.put(pos.immutable(), BuiltInRegistries.BLOCK.getKey(server.overworld().getBlockState(pos).getBlock()).toString());
			}

			return blocks;
		});
	}

	private static String changes(Map<BlockPos, String> before, Map<BlockPos, String> after) {
		StringBuilder changes = new StringBuilder();
		before.forEach((pos, block) -> {
			if (!block.equals(after.get(pos))) {
				changes.append(pos.toShortString()).append(": ").append(block).append(" -> ").append(after.get(pos)).append("; ");
			}
		});
		return changes.isEmpty() ? "değişiklik yok" : changes.toString();
	}

	private static Pos pos(BlockPos pos) {
		return new Pos(pos.getX(), pos.getY(), pos.getZ());
	}

	private static String text(BlockPos pos) {
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}
}
