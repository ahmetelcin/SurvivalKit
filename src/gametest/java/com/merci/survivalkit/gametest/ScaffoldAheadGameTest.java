package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * İskele ahead and up, with the real keys: the bridge reaches ahead of the feet; holding jump
 * builds a tower at vanilla's own pace, only while the key is held if so asked; "keep Y" keeps
 * a bridge on its level over a stair on the way.
 */
public class ScaffoldAheadGameTest implements FabricClientGameTest {
	private static final String COBBLE = "minecraft:cobblestone";
	private static final String AIR = "minecraft:air";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			ScaffoldTestSupport stage = new ScaffoldTestSupport(context, world);
			stage.enable(true);

			theBridgeReachesAheadOfTheFeet(stage);
			holdingJumpBuildsATower(stage);
			onlyWithJumpHeldATapBuildsNothing(stage);
			keepYHoldsTheLevelOverAStair(stage);

			SurvivalKit.LOGGER.info("[gametest] İskele ileri ve yukarı doğrulandı (köprü ayağın 3 blok önünde; zıplama tuşuyla kule, "
				+ "kapalıyken yok; 'sadece zıplama tuşu basılıyken' dokunuşta blok yok, kapalıyken var; 'Y seviyesini koru' merdivende seviyeyi tuttu)");
		}
	}

	/** The moment the feet are over the first gap block, the third one past it is already down; with no reach, the next one is not. */
	private static void theBridgeReachesAheadOfTheFeet(ScaffoldTestSupport stage) {
		stage.set("extend", 3);
		stage.set("per_tick", 4);
		stage.stage("hotbar.2", COBBLE + " 64");

		if (!stage.walkUntil(stage.gap(0).getZ() + 0.1, 60)) {
			throw new AssertionError("hazırlık: oyuncu boşluğa varmadı");
		}

		String third = stage.clientBlock(stage.gap(3));
		String fourth = stage.clientBlock(stage.gap(4));

		if (!COBBLE.equals(third)) {
			throw new AssertionError("ayak boşluğun ilk bloğundayken üç blok ötesi konmuş olmalıydı, gap(3): " + third);
		}

		if (!AIR.equals(fourth)) {
			throw new AssertionError("üç bloktan ötesi konmamalıydı, gap(4): " + fourth);
		}

		stage.set("extend", 0);
		stage.stage("hotbar.2", COBBLE + " 64");
		stage.walkUntil(stage.gap(0).getZ() + 0.1, 60);
		String next = stage.clientBlock(stage.gap(2));

		if (!AIR.equals(next)) {
			throw new AssertionError("negatif kontrol başarısız: uzatma 0 iken iki blok ötesi boş olmalıydı, gap(2): " + next);
		}

		stage.set("per_tick", 1);
	}

	/** Holding jump on the walkway: a block under the feet on every jump, at the game's own pace. Off, nothing. */
	private static void holdingJumpBuildsATower(ScaffoldTestSupport stage) {
		stage.stage("hotbar.2", COBBLE + " 64");
		double ground = stage.feetY();
		BlockPos column = stage.start;

		double highest = stage.jump(60);
		double landed = stage.feetY();

		for (int up = 1; up <= 3; up++) {
			if (!COBBLE.equals(stage.block(column.above(up)))) {
				throw new AssertionError("kulede " + up + ". blok parke taşı olmalıydı: " + stage.block(column.above(up)) + ", inilen " + landed);
			}
		}

		if (landed < ground + 3 - 0.01) {
			throw new AssertionError("oyuncu en az üç blok yükselmiş olmalıydı: " + ground + " → " + landed);
		}

		// Vanilla's jump is about 1.25 blocks; nothing is to lift the player faster than the blocks rise.
		if (highest > landed + 1.3) {
			throw new AssertionError("oyuncu vanilla zıplamasından fazla yükselmemeliydi: en yüksek " + highest + ", inilen " + landed);
		}

		stage.set("tower", false);
		stage.stage("hotbar.2", COBBLE + " 64");
		stage.jump(30);

		if (!AIR.equals(stage.block(column.above())) || stage.feetY() > ground + 0.01) {
			throw new AssertionError("negatif kontrol başarısız: kule kapalıyken blok konmamalıydı: " + stage.block(column.above()));
		}

		stage.set("tower", true);
	}

	/** A tap of jump: with "only with jump held" the key is up again before the feet are a block up, so nothing; off, a block. */
	private static void onlyWithJumpHeldATapBuildsNothing(ScaffoldTestSupport stage) {
		BlockPos column = stage.start;
		stage.stage("hotbar.2", COBBLE + " 64");
		stage.jump(2);

		if (!AIR.equals(stage.block(column.above()))) {
			throw new AssertionError("tuş bırakılmışken kule yapılmamalıydı: " + stage.block(column.above()));
		}

		stage.set("tower_only_with_jump", false);
		stage.stage("hotbar.2", COBBLE + " 64");
		stage.jump(2);

		if (!COBBLE.equals(stage.block(column.above()))) {
			throw new AssertionError("'sadece zıplama tuşu basılıyken' kapalıyken her zıplamada blok konmalıydı: " + stage.block(column.above()));
		}

		stage.set("tower_only_with_jump", true);
	}

	/**
	 * Over the gap, a stone stair one block up on the way, then the gap again. With "keep Y" the
	 * bridge carries on at the walkway's level past the stair; without it, it goes on a block higher.
	 */
	private static void keepYHoldsTheLevelOverAStair(ScaffoldTestSupport stage) {
		BlockPos stair = stage.gap(2).above();
		BlockPos low = stage.gap(3);
		BlockPos high = stage.gap(3).above();

		stage.set("keep_y", true);
		stairStage(stage, stair);
		stage.walk(40);

		if (!COBBLE.equals(stage.block(low)) || !AIR.equals(stage.block(high))) {
			throw new AssertionError("'Y seviyesini koru' açıkken köprü merdivenden sonra eski seviyede sürmeliydi: alt " + stage.block(low)
				+ ", üst " + stage.block(high));
		}

		stage.set("keep_y", false);
		stairStage(stage, stair);
		stage.walk(40);

		if (!COBBLE.equals(stage.block(high)) || !AIR.equals(stage.block(low))) {
			throw new AssertionError("negatif kontrol başarısız: kapalıyken köprü merdivenden sonra bir üstte sürmeliydi: alt " + stage.block(low)
				+ ", üst " + stage.block(high));
		}
	}

	private static void stairStage(ScaffoldTestSupport stage, BlockPos stair) {
		stage.stage("hotbar.2", COBBLE + " 64");
		stage.put(stair.below(), "minecraft:stone");
		stage.put(stair, "minecraft:stone_stairs[facing=south]");
	}
}
