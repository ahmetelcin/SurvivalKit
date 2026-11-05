package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.scaffold.SafeWalk;
import com.merci.survivalkit.scaffold.YLock;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * İskele's extras, with the real keys: a press of sneak takes the locked bridge a block down and
 * "stop at edges" lets the player walk down the step; with "same block" the bridge is the
 * walkway's stone; torches hang on the bridge's side every few blocks, and running out is said.
 */
public class ScaffoldExtrasGameTest implements FabricClientGameTest {
	private static final String COBBLE = "minecraft:cobblestone";
	private static final String STONE = "minecraft:stone";
	private static final String AIR = "minecraft:air";
	private static final String WALL_TORCH = "minecraft:wall_torch";
	/** How many gap columns are looked at: a long walk ends about there. */
	private static final int COLUMNS = 10;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			ScaffoldTestSupport stage = new ScaffoldTestSupport(context, world);
			// These scenes read the action bar, which since the HUD hears Scaffold only when asked to.
			stage.set("action_bar", true);
			stage.enable(true);

			try {
				descend(context, stage);
				sameBlock(stage);
				torches(context, stage);
			} catch (AssertionError e) {
				SurvivalKit.LOGGER.error("[gametest-hata] {}", e.getMessage());
				throw e;
			}

			SurvivalKit.LOGGER.info("[gametest] İskele ekstraları doğrulandı (eğilince kilit bir iner, kenarda dur basamaktan indirdi, "
				+ "kapalıyken köprü düz; aynı blok taş, kapalıyken parke taşı; her 3 blokta yan yüze meşale, yolda yok, "
				+ "meşale bitince söylendi, kapalıyken meşale yok)");
		}
	}

	/**
	 * Locked where the walkway is, "stop at edges" on: walk a while, press sneak once, walk on. The
	 * bridge carries on a block lower from there, and the player walks down onto it rather than
	 * being held at the edge. With "descending bridge" off the same press changes nothing.
	 */
	private static void descend(ClientGameTestContext context, ScaffoldTestSupport stage) {
		int y = stage.start.getY();
		stage.set("y_lock", YLock.START);
		stage.set("safe_walk", SafeWalk.AT_EDGE);
		stage.set("descend", true);

		String said = walkPressWalk(context, stage);

		if (!Lang.of(Keys.SCAFFOLD_Y_LOCKED, y - 1).equals(said)) {
			throw new AssertionError("eylem çubuğu inen kilidi söylemeliydi: " + said);
		}

		List<String> columns = columns(stage, y);
		int first = columns.indexOf("aşağı");

		if (first < 1 || columns.subList(first, Math.min(first + 3, columns.size())).stream().anyMatch(c -> !c.equals("aşağı"))
			|| columns.subList(first, columns.size()).contains("düz")) {
			throw new AssertionError("eğildikten sonra köprü bir aşağıda sürmeliydi (önce düz, sonra en az 3 aşağı, sonra hiç düz yok): " + columns);
		}

		double feet = stage.feetZ();
		double past = stage.gap(first + 2).getZ() + 0.5;

		if (feet < past) {
			throw new AssertionError("kenarda dur oyuncuyu basamakta tutmamalıydı: ayak z " + feet + ", gap(" + (first + 2) + ") " + past);
		}

		SurvivalKit.LOGGER.info("[gametest] inen köprü: {}", columns);
		stage.set("descend", false);
		walkPressWalk(context, stage);

		if (columns(stage, y).contains("aşağı")) {
			throw new AssertionError("negatif kontrol başarısız: inen köprü kapalıyken eğilmek köprüyü indirmemeliydi: " + columns(stage, y));
		}

		stage.set("y_lock", YLock.OFF);
		stage.set("safe_walk", SafeWalk.OFF);
	}

	/** Walk 25 ticks, stop, press the real sneak key for two ticks, walk 40; what the action bar said after the press. */
	private static String walkPressWalk(ClientGameTestContext context, ScaffoldTestSupport stage) {
		stage.stage("hotbar.2", COBBLE + " 64");
		// The lock is where Scaffold is switched on: the walkway.
		stage.enable(false);
		stage.enable(true);
		stage.walk(25);
		context.getInput().holdKey(options -> options.keyShift);
		stage.waitTicks(2);
		context.getInput().releaseKey(options -> options.keyShift);
		String said = stage.actionBar();
		stage.waitTicks(5);
		stage.walk(40);
		return said;
	}

	/** Each gap column: "düz" with cobblestone on the walkway's level, "aşağı" with it a block lower and air above, else "-". */
	private static List<String> columns(ScaffoldTestSupport stage, int y) {
		List<String> columns = new ArrayList<>();

		for (int n = 0; n < COLUMNS; n++) {
			BlockPos at = stage.gap(n).atY(y);

			if (COBBLE.equals(stage.block(at))) {
				columns.add("düz");
			} else if (AIR.equals(stage.block(at)) && COBBLE.equals(stage.block(at.below()))) {
				columns.add("aşağı");
			} else {
				columns.add("-");
			}
		}

		return columns;
	}

	/** Cobblestone first on the list and stone further along: standing on the stone walkway, "same block" builds with stone. */
	private static void sameBlock(ScaffoldTestSupport stage) {
		stage.set("same_block", true);
		stoneAndCobble(stage);

		for (int n = 0; n < 3; n++) {
			if (!STONE.equals(stage.block(stage.gap(n)))) {
				throw new AssertionError("aynı blok: gap(" + n + ") yürüyüş yolu gibi taş olmalıydı: " + stage.block(stage.gap(n)));
			}
		}

		stage.set("same_block", false);
		stoneAndCobble(stage);

		if (!COBBLE.equals(stage.block(stage.gap(0)))) {
			throw new AssertionError("negatif kontrol başarısız: aynı blok kapalıyken listedeki parke taşı konmalıydı: " + stage.block(stage.gap(0)));
		}
	}

	private static void stoneAndCobble(ScaffoldTestSupport stage) {
		stage.stage("hotbar.2", COBBLE + " 64");
		stage.command("item replace entity @a hotbar.3 with " + STONE + " 64");
		stage.waitTicks(2);
		stage.walk(40);
	}

	/**
	 * Every third block of bridge a wall torch on its east face, on the bridge's own level — never
	 * on the walkway above it — and the player walks past them. Out of torches the action bar says
	 * so; off, no torch goes down.
	 */
	private static void torches(ClientGameTestContext context, ScaffoldTestSupport stage) {
		int y = stage.start.getY();
		stage.set("torches", true);
		stage.set("torch_interval", 3);
		stage.stage("hotbar.2", COBBLE + " 64");
		stage.command("item replace entity @a hotbar.4 with minecraft:torch 16");
		stage.waitTicks(2);
		stage.walk(50);

		List<Integer> lit = new ArrayList<>();

		for (int n = 0; n < COLUMNS; n++) {
			BlockPos at = stage.gap(n).atY(y);

			if (WALL_TORCH.equals(stage.block(at.east())) || WALL_TORCH.equals(stage.block(at.west()))) {
				lit.add(n);
			}

			for (BlockPos above : new BlockPos[] {at.above(), at.above().east(), at.above().west()}) {
				if (!AIR.equals(stage.block(above))) {
					throw new AssertionError("yürüme yolunda bir şey olmamalıydı: gap(" + n + ") üstü " + stage.block(above));
				}
			}
		}

		if (!lit.containsAll(List.of(2, 5)) || lit.contains(0) || lit.contains(1) || lit.contains(3) || lit.contains(4)) {
			throw new AssertionError("meşale her 3 blokta, 3. ve 6. köprü bloğunun yanında olmalıydı (gap 2 ve 5): " + lit);
		}

		if (stage.feetZ() < stage.gap(6).getZ()) {
			throw new AssertionError("oyuncu meşalelerin yanından geçip gitmeliydi, ayak z " + stage.feetZ());
		}

		SurvivalKit.LOGGER.info("[gametest] meşaleler gap {} yanında", lit);
		stage.set("torch_interval", 1);
		stage.stage("hotbar.2", COBBLE + " 64");
		boolean warned = false;
		String warning = Lang.of(Keys.SCAFFOLD_NO_TORCHES);
		context.getInput().holdKey(options -> options.keyUp);

		try {
			for (int tick = 0; tick < 40 && !warned; tick++) {
				context.waitTick();
				warned = warning.equals(stage.actionBar());
			}
		} finally {
			context.getInput().releaseKey(options -> options.keyUp);
		}

		if (!warned) {
			throw new AssertionError("meşale yokken eylem çubuğu söylemeliydi: " + warning);
		}

		stage.set("torches", false);
		stage.stage("hotbar.2", COBBLE + " 64");
		stage.command("item replace entity @a hotbar.4 with minecraft:torch 16");
		stage.waitTicks(2);
		stage.walk(50);

		for (int n = 0; n < COLUMNS; n++) {
			BlockPos at = stage.gap(n).atY(y);

			if (WALL_TORCH.equals(stage.block(at.east())) || WALL_TORCH.equals(stage.block(at.west()))) {
				throw new AssertionError("negatif kontrol başarısız: meşale kapalıyken gap(" + n + ") yanına meşale konmamalıydı");
			}
		}
	}
}
