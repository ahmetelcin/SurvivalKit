package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.scaffold.YLock;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * İskele's Y lock, with the real keys: running and jumping with jump held the bridge stays on one
 * y, where without the lock the tower lifts it; a set Y of 60 puts the bridge at 60, a block under
 * the walkway it starts from; the lock key brings the lock to the feet, both kinds, and says so on
 * the action bar.
 */
public class ScaffoldYLockGameTest implements FabricClientGameTest {
	private static final String COBBLE = "minecraft:cobblestone";
	private static final String AIR = "minecraft:air";
	private static final String KEY = "key.keyboard.y";
	/** How far past the walkway the runway is cleared: a running jump goes further than the stage's own. */
	private static final int RUNWAY = 40;
	private static final int SET_Y = 60;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			ScaffoldTestSupport stage = new ScaffoldTestSupport(context, world);
			// These scenes read the action bar, which since the HUD hears Scaffold only when asked to.
			stage.set("action_bar", true);
			stage.enable(true);

			try {
				aRunningJumpKeepsTheBridgeFlat(context, stage);
				aSetYOfSixtyPutsTheBridgeAtSixty(stage);
				theKeyMovesASetY(context, stage);
				theKeyMovesAStartLock(context, stage);
			} catch (AssertionError e) {
				SurvivalKit.LOGGER.error("[gametest-hata] {}", e.getMessage());
				throw e;
			}

			SurvivalKit.LOGGER.info("[gametest] İskele Y kilidi doğrulandı (koşarak zıplarken köprü tek seviyede, kilitsiz kule yükseltti; "
				+ "belirli Y=60'ta köprü 60'ta, kapalıyken 61'de; tuş belirli Y'yi ve başlangıç kilidini ayağa çekti, eylem çubuğunda söyledi)");
		}
	}

	/**
	 * Forward, sprint and jump held together from the walkway's end. With the lock every block is
	 * on the walkway's level and the player never drops below it; without, the tower takes the
	 * jumps and puts blocks above it.
	 */
	private static void aRunningJumpKeepsTheBridgeFlat(ClientGameTestContext context, ScaffoldTestSupport stage) {
		int level = stage.start.getY();

		stage.set("y_lock", YLock.START);
		runway(stage);
		// Switched on standing on the walkway: that is where it started.
		stage.enable(false);
		stage.enable(true);
		double lowest = runningJump(context, stage, 40);
		int bridged = bridged(stage, level);

		if (bridged < 8) {
			throw new AssertionError("kilitliyken köprü en az 8 blok sürmeliydi, sürdü: " + bridged + ", en alçak ayak " + lowest);
		}

		int above = aboveTheLevel(stage, level);

		if (above >= 0) {
			throw new AssertionError("kilitliyken seviyenin üstüne blok konmamalıydı, gap(" + above + ") üstünde parke taşı var");
		}

		if (lowest < level + 1 - 0.01) {
			throw new AssertionError("oyuncu köprüden düşmemeliydi: en alçak ayak " + lowest + ", köprünün üstü " + (level + 1));
		}

		SurvivalKit.LOGGER.info("[gametest] Y kilidi koşarak zıplama: {} blok tek seviyede, en alçak ayak {} (seviyenin üstü {})",
			bridged, lowest, level + 1);
		stage.set("y_lock", YLock.OFF);
		runway(stage);
		runningJump(context, stage, 40);

		if (aboveTheLevel(stage, level) < 0) {
			throw new AssertionError("negatif kontrol başarısız: kilitsiz koşarak zıplarken kule köprüyü yükseltmeliydi");
		}
	}

	/**
	 * A walkway two blocks thick with its top at 61, nothing past it, a catch floor below. Locked at
	 * 60 the bridge goes a block under the walkway's top and the player steps down onto it; off, it
	 * carries on at 61.
	 */
	private static void aSetYOfSixtyPutsTheBridgeAtSixty(ScaffoldTestSupport stage) {
		stage.set("y_lock", YLock.FIXED);
		stage.set("fixed_y", SET_Y);
		double lowest = highWalkway(stage);

		for (int n = 0; n < 3; n++) {
			BlockPos at = highGap(stage, n, SET_Y);

			if (!COBBLE.equals(stage.block(at)) || !AIR.equals(stage.block(at.above()))) {
				throw new AssertionError("belirli Y=60'ta gap(" + n + ") 60'ta parke taşı, 61'de hava olmalıydı: "
					+ stage.block(at) + " / " + stage.block(at.above()));
			}
		}

		if (lowest < SET_Y + 1 - 0.01) {
			throw new AssertionError("oyuncu 60'taki köprünün üstünde yürümeliydi, en alçak ayak: " + lowest);
		}

		stage.set("y_lock", YLock.OFF);
		highWalkway(stage);
		BlockPos low = highGap(stage, 0, SET_Y);

		if (!AIR.equals(stage.block(low)) || !COBBLE.equals(stage.block(low.above()))) {
			throw new AssertionError("negatif kontrol başarısız: kilitsiz köprü 61'de sürmeliydi: 60 " + stage.block(low)
				+ ", 61 " + stage.block(low.above()));
		}
	}

	/** Set to 60, high above the walkway: nothing goes down. The key on the walkway: the number becomes its level and the bridge follows. */
	private static void theKeyMovesASetY(ClientGameTestContext context, ScaffoldTestSupport stage) {
		int level = stage.start.getY();
		stage.set("y_lock_key", KEY);
		stage.set("y_lock", YLock.FIXED);
		stage.set("fixed_y", SET_Y);

		stage.stage("hotbar.2", COBBLE + " 64");
		stage.walk(30);

		if (!AIR.equals(stage.block(stage.gap(0)))) {
			throw new AssertionError("hazırlık: kilit 60'tayken yürüyüş yolunun seviyesine blok konmamalıydı: " + stage.block(stage.gap(0)));
		}

		stage.stage("hotbar.2", COBBLE + " 64");
		String said = press(context, stage);
		int fixedY = context.computeOnClient(client -> ScaffoldTestSupport.module().fixedY());

		if (fixedY != level) {
			throw new AssertionError("tuş belirli Y'yi ayağın altına çekmeliydi: " + fixedY + ", beklenen " + level);
		}

		if (!Lang.of(Keys.SCAFFOLD_Y_LOCKED, level).equals(said)) {
			throw new AssertionError("eylem çubuğu yeni kilidi söylemeliydi: " + said);
		}

		stage.walk(40);

		for (int n = 0; n < 3; n++) {
			if (!COBBLE.equals(stage.block(stage.gap(n)))) {
				throw new AssertionError("tuştan sonra köprü yürüyüş yolunun seviyesinde sürmeliydi, gap(" + n + "): " + stage.block(stage.gap(n)));
			}
		}
	}

	/**
	 * Switched on standing on the catch floor, the lock starts down there and the walkway's gap is
	 * left alone; the key on the walkway brings it up, and the bridge with it.
	 */
	private static void theKeyMovesAStartLock(ClientGameTestContext context, ScaffoldTestSupport stage) {
		int level = stage.start.getY();
		int floor = level - ScaffoldTestSupport.DROP;
		stage.set("y_lock", YLock.START);

		stage.stage("hotbar.2", COBBLE + " 64");
		stage.tp(stage.start.getX() + 0.5, floor + 1, stage.start.getZ() + 0.5);
		stage.enable(false);
		stage.enable(true);
		stage.stage("hotbar.2", COBBLE + " 64");
		stage.walk(30);

		if (!AIR.equals(stage.block(stage.gap(0)))) {
			throw new AssertionError("hazırlık: kilit taban seviyesindeyken yürüyüş yolunun seviyesine blok konmamalıydı: " + stage.block(stage.gap(0)));
		}

		stage.stage("hotbar.2", COBBLE + " 64");
		String said = press(context, stage);

		if (!Lang.of(Keys.SCAFFOLD_Y_LOCKED, level).equals(said)) {
			throw new AssertionError("eylem çubuğu yeni kilidi söylemeliydi: " + said);
		}

		stage.walk(40);

		for (int n = 0; n < 3; n++) {
			if (!COBBLE.equals(stage.block(stage.gap(n)))) {
				throw new AssertionError("tuştan sonra başlangıç kilidi yürüyüş yolunun seviyesine çıkmalıydı, gap(" + n + "): " + stage.block(stage.gap(n)));
			}
		}
	}

	/** The stage, and the air and catch floor carried on far enough for a running jump. */
	private static void runway(ScaffoldTestSupport stage) {
		stage.stage("hotbar.2", COBBLE + " 64");
		int x = stage.start.getX();
		int y = stage.start.getY();
		int z = stage.start.getZ();
		int drop = ScaffoldTestSupport.DROP;
		stage.command("fill " + (x - 4) + " " + (y - drop - 1) + " " + (z + 15) + " " + (x + 4) + " " + (y + 4) + " " + (z + RUNWAY) + " minecraft:air");
		stage.command("fill " + (x - 4) + " " + (y - drop) + " " + (z + 15) + " " + (x + 4) + " " + (y - drop) + " " + (z + RUNWAY) + " minecraft:glass");
		stage.waitTicks(5);
	}

	/** Forward, sprint and jump held for {@code ticks}; the lowest the feet got. */
	private static double runningJump(ClientGameTestContext context, ScaffoldTestSupport stage, int ticks) {
		double lowest = stage.feetY();
		context.getInput().holdKey(options -> options.keyUp);
		context.getInput().holdKey(options -> options.keySprint);
		context.getInput().holdKey(options -> options.keyJump);

		try {
			for (int tick = 0; tick < ticks; tick++) {
				context.waitTick();
				lowest = Math.min(lowest, stage.feetY());
			}
		} finally {
			context.getInput().releaseKey(options -> options.keyJump);
			context.getInput().releaseKey(options -> options.keySprint);
			context.getInput().releaseKey(options -> options.keyUp);
		}

		stage.waitTicks(10);
		return lowest;
	}

	/** How many gap blocks in a row, from the first, are cobblestone on {@code level}. */
	private static int bridged(ScaffoldTestSupport stage, int level) {
		int n = 0;

		while (n < RUNWAY - ScaffoldTestSupport.WALKWAY && COBBLE.equals(stage.block(stage.gap(n)))) {
			n++;
		}

		return n;
	}

	/** The first gap column with cobblestone one or two above {@code level}, or -1. */
	private static int aboveTheLevel(ScaffoldTestSupport stage, int level) {
		for (int n = 0; n < RUNWAY - ScaffoldTestSupport.WALKWAY; n++) {
			BlockPos at = stage.gap(n);

			if (COBBLE.equals(stage.block(at.above())) || COBBLE.equals(stage.block(at.above(2)))) {
				return n;
			}
		}

		return -1;
	}

	/**
	 * Air round x, z of the stage from 56 to 66, a glass floor at 57, a stone walkway at 60 and 61
	 * three long; the player on it at the north end, walked forward 40 ticks. The lowest the feet got.
	 */
	private static double highWalkway(ScaffoldTestSupport stage) {
		stage.stage("hotbar.2", COBBLE + " 64");
		int x = stage.start.getX();
		int z = stage.start.getZ();
		stage.command("fill " + (x - 4) + " 56 " + (z - 2) + " " + (x + 4) + " 66 " + (z + 20) + " minecraft:air");
		stage.command("fill " + (x - 4) + " 57 " + (z - 2) + " " + (x + 4) + " 57 " + (z + 20) + " minecraft:glass");
		stage.command("fill " + x + " 60 " + z + " " + x + " 61 " + (z + ScaffoldTestSupport.WALKWAY - 1) + " minecraft:stone");
		stage.tp(x + 0.5, 62, z + 0.5);
		stage.waitTicks(5);
		return stage.walk(40);
	}

	/** The high walkway's gap block {@code n} at {@code y}. */
	private static BlockPos highGap(ScaffoldTestSupport stage, int n, int y) {
		BlockPos gap = stage.gap(n);
		return new BlockPos(gap.getX(), y, gap.getZ());
	}

	/** Presses the lock key for real, and what the action bar then says. */
	private static String press(ClientGameTestContext context, ScaffoldTestSupport stage) {
		context.runOnClient(client -> setOverlay(client.gui.hud, null));
		context.getInput().holdKeyFor(InputConstants.getKey(KEY), 2);
		stage.waitTicks(2);
		Component shown = context.computeOnClient(client -> getOverlay(client.gui.hud));
		return shown == null ? "yok" : shown.getString();
	}

	private static Component getOverlay(Hud hud) {
		try {
			return (Component) overlay().get(hud);
		} catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}

	private static void setOverlay(Hud hud, Component value) {
		try {
			overlay().set(hud, value);
		} catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}

	/** The action bar's text has no getter; the field is read directly. */
	private static Field overlay() {
		try {
			Field field = Hud.class.getDeclaredField("overlayMessageString");
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("eylem çubuğu alanı bulunamadı", e);
		}
	}
}
