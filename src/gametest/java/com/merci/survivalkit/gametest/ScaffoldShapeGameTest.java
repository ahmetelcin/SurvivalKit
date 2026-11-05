package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.scaffold.Ramp;
import com.merci.survivalkit.scaffold.YLock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * The bridge's shape, with the real keys: width 3 reaches both sides and width 2 the nearer one;
 * walking slantwise the corners go in and the bridge is joined edge to edge; a ramp climbs a block
 * for every block on — jumping, with the lock sliding along — and sinks a block going down.
 */
public class ScaffoldShapeGameTest implements FabricClientGameTest {
	private static final String COBBLE = "minecraft:cobblestone";
	private static final String STONE = "minecraft:stone";
	private static final String AIR = "minecraft:air";
	/** How far the slantwise stage reaches east and south of the start. */
	private static final int SLANT = 12;

	/** The first block of a slantwise bridge not joined edge to edge, -1 with none; and the farthest, both as dx + dz. */
	private record Slant(int unjoined, int far, String cells) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			ScaffoldTestSupport stage = new ScaffoldTestSupport(context, world);
			stage.enable(true);

			try {
				width(stage);
				diagonal(context, stage);
				rampUpWithTheLock(context, stage);
				rampDown(stage);
			} catch (AssertionError e) {
				SurvivalKit.LOGGER.error("[gametest-hata] {}", e.getMessage());
				throw e;
			}

			SurvivalKit.LOGGER.info("[gametest] İskele köprü biçimi doğrulandı (genişlik 3 iki yana, 2 yakın yana, 1 yansız; "
				+ "çapraz yürüyüşte köprü kenardan bağlı, kapalıyken ilk köşede kaldı; rampa zıplayarak her blokta bir yukarı, kilit kaydı; "
				+ "aşağı rampa her blokta bir aşağı, kapalıyken düz)");
		}
	}

	/** Width 3 puts a block either side of every block on the way, 2 on the side the feet are nearer, 1 none. */
	private static void width(ScaffoldTestSupport stage) {
		stage.set("width", 3);
		stage.stage("hotbar.2", COBBLE + " 64");
		stage.walk(40);

		for (int n = 0; n < 3; n++) {
			BlockPos at = stage.gap(n);
			expect(stage, at.west(), COBBLE, "genişlik 3: gap(" + n + ") batısı");
			expect(stage, at, COBBLE, "genişlik 3: gap(" + n + ")");
			expect(stage, at.east(), COBBLE, "genişlik 3: gap(" + n + ") doğusu");
		}

		stage.set("width", 2);
		stage.stage("hotbar.2", COBBLE + " 64");
		// A quarter of a block in from the west side: that side is nearer.
		stage.tp(stage.start.getX() + 0.25, stage.start.getY() + 1, stage.start.getZ() + 0.5);
		stage.walk(40);

		for (int n = 0; n < 3; n++) {
			BlockPos at = stage.gap(n);
			expect(stage, at.west(), COBBLE, "genişlik 2, batıya yakın: gap(" + n + ") batısı");
			expect(stage, at, COBBLE, "genişlik 2: gap(" + n + ")");
			expect(stage, at.east(), AIR, "genişlik 2, batıya yakın: gap(" + n + ") doğusu boş kalmalıydı");
		}

		stage.set("width", 1);
		stage.stage("hotbar.2", COBBLE + " 64");
		stage.walk(40);

		for (int n = 0; n < 3; n++) {
			BlockPos at = stage.gap(n);
			expect(stage, at, COBBLE, "genişlik 1: gap(" + n + ")");

			if (!AIR.equals(stage.block(at.west())) || !AIR.equals(stage.block(at.east()))) {
				throw new AssertionError("negatif kontrol başarısız: genişlik 1'de gap(" + n + ") yanları boş kalmalıydı: "
					+ stage.block(at.west()) + " / " + stage.block(at.east()));
			}
		}
	}

	/**
	 * Facing south-east from a single block, forward held: with "diagonal" every block of the
	 * bridge is joined to the start edge to edge and it goes on; without, the next block on the
	 * line meets the last only at an edge, has no face to be put against, and the bridge stops there.
	 */
	private static void diagonal(ClientGameTestContext context, ScaffoldTestSupport stage) {
		Slant on = slant(context, stage, true);

		if (on.unjoined() >= 0) {
			throw new AssertionError("çapraz yürüyüş açıkken köprü kenardan bağlı olmalıydı, bağsız blok: " + on.unjoined());
		}

		if (on.far() < 6) {
			throw new AssertionError("çapraz köprü en az 6 blok (x+z) gitmeliydi, gitti: " + on.far());
		}

		Slant off = slant(context, stage, false);

		if (off.far() > 1) {
			throw new AssertionError("negatif kontrol başarısız: çapraz yürüyüş kapalıyken köprü ilk köşede kalmalıydı "
				+ "(yalnız kenarıyla değen bloğun yanına konacak yüzü yok): " + off.cells());
		}

		stage.set("diagonal", true);
	}

	/**
	 * The slantwise walk, from a stage of its own: air east and south of the start, one stone
	 * block to stand on, a catch floor. The first block of bridge not joined to the start edge to
	 * edge, as dx + dz from the start (-1 with all joined), and how far the bridge got.
	 */
	private static Slant slant(ClientGameTestContext context, ScaffoldTestSupport stage, boolean on) {
		stage.set("diagonal", on);
		stage.stage("hotbar.2", COBBLE + " 64");
		int x = stage.start.getX();
		int y = stage.start.getY();
		int z = stage.start.getZ();
		int drop = ScaffoldTestSupport.DROP;
		stage.command("fill " + (x - 2) + " " + (y - drop - 1) + " " + (z - 2) + " " + (x + SLANT) + " " + (y + 4) + " " + (z + SLANT) + " minecraft:air");
		stage.command("fill " + (x - 2) + " " + (y - drop) + " " + (z - 2) + " " + (x + SLANT) + " " + (y - drop) + " " + (z + SLANT) + " minecraft:glass");
		stage.put(stage.start, STONE);
		stage.tp(x + 0.5, y + 1, z + 0.5);
		context.runOnClient(client -> client.player.setYRot(-45.0f));
		stage.waitTicks(2);
		stage.walk(30);

		Set<BlockPos> bridge = new HashSet<>();

		for (int dx = -2; dx <= SLANT; dx++) {
			for (int dz = -2; dz <= SLANT; dz++) {
				BlockPos at = new BlockPos(x + dx, y, z + dz);

				if (!AIR.equals(stage.block(at))) {
					bridge.add(at);
				}
			}
		}

		Set<BlockPos> reached = new HashSet<>();
		Deque<BlockPos> next = new ArrayDeque<>();
		next.add(stage.start);
		reached.add(stage.start);

		while (!next.isEmpty()) {
			BlockPos at = next.poll();

			for (BlockPos side : new BlockPos[] {at.north(), at.south(), at.east(), at.west()}) {
				if (bridge.contains(side) && reached.add(side)) {
					next.add(side);
				}
			}
		}

		return new Slant(
			bridge.stream().filter(at -> !reached.contains(at)).mapToInt(at -> at.getX() - x + at.getZ() - z).min().orElse(-1),
			bridge.stream().mapToInt(at -> at.getX() - x + at.getZ() - z).max().orElse(0),
			bridge.stream().map(at -> (at.getX() - x) + ":" + (at.getZ() - z)).sorted().toList().toString());
	}

	/**
	 * Up, with the lock on where it started: from the walkway's end, forward and jump held, every
	 * block on is a step a block higher than the one before — the lock slides up with the ramp —
	 * and the player climbs them.
	 */
	private static void rampUpWithTheLock(ClientGameTestContext context, ScaffoldTestSupport stage) {
		int y = stage.start.getY();
		stage.set("ramp", Ramp.UP);
		stage.set("y_lock", YLock.START);
		atTheWalkwaysEnd(stage);
		double highest = stage.feetY();
		context.getInput().holdKey(options -> options.keyUp);
		context.getInput().holdKey(options -> options.keyJump);

		try {
			for (int tick = 0; tick < 60; tick++) {
				context.waitTick();
				if (stage.onGround()) {
					highest = Math.max(highest, stage.feetY());
				}
			}
		} finally {
			context.getInput().releaseKey(options -> options.keyJump);
			context.getInput().releaseKey(options -> options.keyUp);
		}

		stage.waitTicks(10);

		for (int n = 0; n < 3; n++) {
			BlockPos step = stage.gap(n).atY(y + 1 + n);
			expect(stage, step, COBBLE, "yukarı rampa: gap(" + n + ") basamağı Y " + (y + 1 + n));
			expect(stage, step.above(), AIR, "yukarı rampa: gap(" + n + ") basamağının üstü boş kalmalıydı");
		}

		if (highest < y + 4 - 0.01) {
			throw new AssertionError("oyuncu üçüncü basamağa çıkmalıydı (ayak Y " + (y + 4) + "), en yüksek yerdeki ayak: " + highest);
		}

		SurvivalKit.LOGGER.info("[gametest] yukarı rampa: basamaklar Y {}..{}, en yüksek yerdeki ayak {}", y + 1, y + 3, highest);
		stage.set("ramp", Ramp.OFF);
		stage.set("y_lock", YLock.OFF);
	}

	/** Down: walking off the walkway's end, every block on is a block lower; off, the bridge is flat. */
	private static void rampDown(ScaffoldTestSupport stage) {
		int y = stage.start.getY();
		stage.set("ramp", Ramp.DOWN);
		atTheWalkwaysEnd(stage);
		double lowest = stage.walk(40);

		for (int n = 0; n < 2; n++) {
			BlockPos step = stage.gap(n).atY(y - 1 - n);
			expect(stage, step, COBBLE, "aşağı rampa: gap(" + n + ") basamağı Y " + (y - 1 - n));
			expect(stage, step.above(), AIR, "aşağı rampa: gap(" + n + ") basamağının üstü boş kalmalıydı");
		}

		if (lowest > y - 1 + 0.01) {
			throw new AssertionError("oyuncu ikinci basamağa inmeliydi (ayak Y " + (y - 1) + "), en alçak ayak: " + lowest);
		}

		stage.set("ramp", Ramp.OFF);
		atTheWalkwaysEnd(stage);
		stage.walk(40);

		if (!COBBLE.equals(stage.block(stage.gap(0))) || !AIR.equals(stage.block(stage.gap(1).below()))) {
			throw new AssertionError("negatif kontrol başarısız: rampa kapalıyken köprü düz sürmeliydi: gap(0) "
				+ stage.block(stage.gap(0)) + ", gap(1) altı " + stage.block(stage.gap(1).below()));
		}
	}

	/** The stage, the player on the walkway's last block, and Scaffold switched on there: the ramp starts there. */
	private static void atTheWalkwaysEnd(ScaffoldTestSupport stage) {
		stage.stage("hotbar.2", COBBLE + " 64");
		BlockPos end = stage.gap(-1);
		stage.tp(end.getX() + 0.5, end.getY() + 1, end.getZ() + 0.5);
		stage.enable(false);
		stage.enable(true);
	}

	private static void expect(ScaffoldTestSupport stage, BlockPos at, String block, String what) {
		String found = stage.block(at);

		if (!block.equals(found)) {
			throw new AssertionError(what + ": " + block + " olmalıydı, " + found);
		}
	}
}
