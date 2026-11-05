package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.scaffold.SafeWalk;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * İskele's safety with the real keys and screens: SafeWalk stops the player at the edge with no
 * blocks left, or crouches all the way; a screen, a fall and lava each pause it; with Yapı Modu's
 * area the bridge ends at the area's edge and SafeWalk stops the player there.
 */
public class ScaffoldSafetyGameTest implements FabricClientGameTest {
	private static final String COBBLE = "minecraft:cobblestone";
	private static final String AIR = "minecraft:air";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			ScaffoldTestSupport stage = new ScaffoldTestSupport(context, world);
			stage.enable(true);

			// Logged before the world closes: a failure here has been seen to hang the close, and the
			// assertion would otherwise only be printed once it lets go.
			try {
				atTheEdgeWithNoBlocksSafeWalkStopsThePlayer(context, stage);
				alwaysCrouchesTheWholeWayAndStillBuilds(context, stage);
				aScreenOpenPausesIt(context, stage);
				aFallPausesIt(stage);
				lavaPausesIt(stage);
				theBridgeEndsAtTheAreasEdge(context, stage);
			} catch (AssertionError e) {
				SurvivalKit.LOGGER.error("[gametest-hata] {}", e.getMessage());
				throw e;
			}

			SurvivalKit.LOGGER.info("[gametest] İskele güvenlik doğrulandı (blok yokken kenarda durdu, kapalıyken düştü; 'her zaman' eğilerek köprü kurdu; "
				+ "ekran, düşüş ve lav duraklattı, kapalıyken koydu; Yapı Modu alanının kenarında köprü bitti ve durdu, alan yokken sürdü)");
		}
	}

	/** No blocks at all: walking at the edge, the player stops on it, crouched only there; off, falls. */
	private static void atTheEdgeWithNoBlocksSafeWalkStopsThePlayer(ClientGameTestContext context, ScaffoldTestSupport stage) {
		stage.set("safe_walk", SafeWalk.AT_EDGE);
		stage.stage(null, null);
		double level = stage.feetY();
		double startZ = stage.feetZ();

		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(8);
		boolean crouchedOnTheWay = stage.sneakDown();
		double eightTicks = stage.feetZ() - startZ;
		context.getInput().releaseKey(options -> options.keyUp);

		if (crouchedOnTheWay || eightTicks < 1.0) {
			throw new AssertionError("kenardan uzakta eğilmemeli, normal hızda yürümeliydi: eğik " + crouchedOnTheWay + ", 8 tick'te " + eightTicks + " blok");
		}

		double lowest = stage.walk(40);
		double edge = stage.gap(0).getZ();

		if (lowest < level - 0.01 || stage.feetZ() > edge + 0.31) {
			throw new AssertionError("blok yokken kenarda durmalıydı: en alçak " + lowest + " (başlangıç " + level + "), z " + stage.feetZ()
				+ " (kenar " + edge + ")");
		}

		// Held back on the edge it stays crouched; a step back from it, and it stands up.
		context.getInput().holdKeyFor(options -> options.keyDown, 10);
		context.waitTicks(10);

		if (stage.sneakDown() || stage.feetZ() > edge - 0.5) {
			throw new AssertionError("kenardan geri çekilince eğilme bırakılmalıydı: eğik " + stage.sneakDown() + ", z " + stage.feetZ());
		}

		stage.set("safe_walk", SafeWalk.OFF);
		stage.stage(null, null);
		lowest = stage.walk(40);

		if (lowest > level - ScaffoldTestSupport.DROP + 0.01) {
			throw new AssertionError("negatif kontrol başarısız: SafeWalk kapalıyken blok yokken düşmeliydi, en alçak " + lowest);
		}
	}

	private static void alwaysCrouchesTheWholeWayAndStillBuilds(ClientGameTestContext context, ScaffoldTestSupport stage) {
		stage.set("safe_walk", SafeWalk.ALWAYS);
		stage.stage("hotbar.2", COBBLE + " 64");
		double level = stage.feetY();

		if (!stage.sneakDown()) {
			throw new AssertionError("'her zaman' iken yürümeden de eğilmeliydi");
		}

		double lowest = stage.walk(80);

		if (!COBBLE.equals(stage.block(stage.gap(0))) || lowest < level - 0.01) {
			throw new AssertionError("eğilerek de köprü kurulmalı, düşmemeliydi: " + stage.block(stage.gap(0)) + ", en alçak " + lowest);
		}

		stage.set("safe_walk", SafeWalk.OFF);
		context.waitTicks(2);

		if (stage.sneakDown()) {
			throw new AssertionError("SafeWalk kapanınca eğilme bırakılmalıydı");
		}
	}

	/**
	 * Standing on the walkway's last block with the middle of the feet already over the gap: the
	 * block goes down at once — unless the inventory is open. Off, it goes down with it open.
	 */
	private static void aScreenOpenPausesIt(ClientGameTestContext context, ScaffoldTestSupport stage) {
		BlockPos gap = stage.gap(0);

		for (boolean pause : new boolean[] {true, false}) {
			stage.set("pause_with_screen", pause);
			stage.enable(false);
			stage.stage("hotbar.2", COBBLE + " 64");
			stage.tp(gap.getX() + 0.5, gap.getY() + 1, gap.getZ() + 0.2);
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitTicks(3);

			if (!context.computeOnClient(client -> client.gui.screen() != null)) {
				throw new AssertionError("hazırlık: envanter açılmadı");
			}

			stage.enable(true);
			context.waitTicks(10);
			String underOpen = stage.block(gap);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(10);

			if (pause && (!AIR.equals(underOpen) || !COBBLE.equals(stage.block(gap)))) {
				throw new AssertionError("envanter açıkken blok konmamalı, kapanınca konmalıydı: açıkken " + underOpen + ", sonra " + stage.block(gap));
			}

			if (!pause && !COBBLE.equals(underOpen)) {
				throw new AssertionError("negatif kontrol başarısız: 'ekran açıkken duraklat' kapalıyken envanter açıkken de konmalıydı: " + underOpen);
			}
		}

		stage.set("pause_with_screen", true);
	}

	/**
	 * Teleported five blocks above the gap beside the walkway's end: falling past the walkway's level
	 * nothing is caught, and the player lands on the floor below. Off, the fall is caught on a block.
	 */
	private static void aFallPausesIt(ScaffoldTestSupport stage) {
		BlockPos gap = stage.gap(0);

		for (boolean pause : new boolean[] {true, false}) {
			stage.set("pause_while_falling", pause);
			stage.stage("hotbar.2", COBBLE + " 64");
			double level = stage.feetY();
			stage.tp(gap.getX() + 0.5, gap.getY() + 6, gap.getZ() + 0.5);
			double landed = waitToLand(stage);

			if (pause && (!AIR.equals(stage.block(gap)) || landed > level - ScaffoldTestSupport.DROP + 0.01)) {
				throw new AssertionError("düşerken blok konmamalı, oyuncu aşağı inmeliydi: " + stage.block(gap) + ", inilen " + landed);
			}

			if (!pause && (!COBBLE.equals(stage.block(gap)) || landed < level - 0.01)) {
				throw new AssertionError("negatif kontrol başarısız: 'düşerken duraklat' kapalıyken düşüş blokla yakalanmalıydı: " + stage.block(gap) + ", inilen " + landed);
			}
		}

		stage.set("pause_while_falling", true);
	}

	private static double waitToLand(ScaffoldTestSupport stage) {
		for (int tick = 0; tick < 60; tick++) {
			stage.waitTicks(1);

			if (stage.onGround()) {
				return stage.feetY();
			}
		}

		throw new AssertionError("hazırlık: oyuncu yere inmedi");
	}

	/** Standing in lava on the walkway's last block, walking for the gap: nothing goes down. Off, it does. */
	private static void lavaPausesIt(ScaffoldTestSupport stage) {
		BlockPos gap = stage.gap(0);
		BlockPos last = gap.north();

		for (boolean pause : new boolean[] {true, false}) {
			stage.set("pause_in_lava", pause);
			stage.stage("hotbar.2", COBBLE + " 64");
			stage.tp(last.getX() + 0.5, last.getY() + 1, last.getZ() + 0.4);
			stage.put(last.above(), "minecraft:lava");
			stage.waitTicks(3);

			if (!stage.inLava()) {
				throw new AssertionError("hazırlık: oyuncu lavın içinde olmalıydı");
			}

			stage.walk(12);
			String under = stage.block(gap);
			stage.put(last.above(), "minecraft:air");

			if (pause && COBBLE.equals(under)) {
				throw new AssertionError("lavın içindeyken blok konmamalıydı: " + under);
			}

			if (!pause && !COBBLE.equals(under)) {
				throw new AssertionError("negatif kontrol başarısız: 'lavda duraklat' kapalıyken lavda da konmalıydı: " + under);
			}
		}

		stage.set("pause_in_lava", true);
	}

	/**
	 * Yapı Modu's area takes in the walkway and the first two gap blocks. With SafeWalk at the edge
	 * the bridge stops at the area's end and so does the player; with Yapı Modu off it carries on.
	 */
	private static void theBridgeEndsAtTheAreasEdge(ClientGameTestContext context, ScaffoldTestSupport stage) {
		BlockPos start = stage.start;
		BuildTestSupport.enable(context, true);
		// Something to stand on at each corner, so the feet are still there when the command arrives.
		stage.put(start.offset(-1, -2, 0), "minecraft:glass");
		stage.put(new BlockPos(start.getX() + 1, start.getY() + 1, stage.gap(1).getZ()), "minecraft:glass");
		stage.tp(start.getX() - 0.5, start.getY() - 1, start.getZ() + 0.5);
		BuildTestSupport.type(context, "bolge kose1");
		stage.tp(start.getX() + 1.5, start.getY() + 2, stage.gap(1).getZ() + 0.5);
		BuildTestSupport.type(context, "bolge kose2");

		if (context.computeOnClient(client -> BuildTestSupport.module().area()).isEmpty()) {
			throw new AssertionError("hazırlık: alan seçilmedi");
		}

		stage.set("safe_walk", SafeWalk.AT_EDGE);
		stage.stage("hotbar.2", COBBLE + " 64");
		double level = stage.feetY();
		double lowest = stage.walk(40);

		String first = stage.block(stage.gap(0));
		String second = stage.block(stage.gap(1));
		String outside = stage.block(stage.gap(2));

		if (!COBBLE.equals(first) || !COBBLE.equals(second) || !AIR.equals(outside)) {
			throw new AssertionError("köprü alanın içinde kalmalıydı: gap(0) " + first + ", gap(1) " + second + ", gap(2) " + outside);
		}

		if (lowest < level - 0.01 || stage.feetZ() > stage.gap(2).getZ() + 0.31) {
			throw new AssertionError("alanın kenarında durmalıydı: en alçak " + lowest + ", z " + stage.feetZ());
		}

		BuildTestSupport.enable(context, false);
		stage.stage("hotbar.2", COBBLE + " 64");
		stage.walk(40);

		if (!COBBLE.equals(stage.block(stage.gap(2)))) {
			throw new AssertionError("negatif kontrol başarısız: Yapı Modu kapalıyken köprü alanın ötesine sürmeliydi: " + stage.block(stage.gap(2)));
		}

		BuildTestSupport.enable(context, true);
		BuildTestSupport.type(context, "bolge temizle");
		BuildTestSupport.enable(context, false);
		stage.set("safe_walk", SafeWalk.OFF);
	}
}
