package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.clock.WorldClocks;

/**
 * The world around the chunks comes through the download too: the time of day, a thunderstorm,
 * where players spawn, and the difficulty.
 *
 * <p>Each is set to something the downloaded world would not have by itself — night instead of
 * morning, a storm instead of clear sky, a spawn away from the origin, hard instead of normal —
 * so a value that fell back to its default fails the check rather than passing by luck.
 */
public class WorldInfoGameTest implements FabricClientGameTest {
	private static final long NIGHT = 13000;
	/** The reopened world runs a few ticks before it is looked at. */
	private static final long CLOCK_SLACK = 200;
	private static final BlockPos SPAWN = new BlockPos(24, 90, -17);

	@Override
	public void runTest(ClientGameTestContext context) {
		WdlTestSupport.downloadThenReopen(context, world -> {
			context.waitTicks(5);
			world.getServer().runCommand("time set " + NIGHT);

			if (serverClock(world) < NIGHT) {
				// 26.2 keeps time per clock; name it if the short form did not reach it.
				world.getServer().runCommand("time of minecraft:overworld set " + NIGHT);
			}

			if (serverClock(world) < NIGHT) {
				throw new AssertionError("hazırlık başarısız: sunucu saati ayarlanamadı, " + serverClock(world));
			}

			world.getServer().runCommand("weather thunder");
			world.getServer().runCommand("setworldspawn " + SPAWN.getX() + " " + SPAWN.getY() + " " + SPAWN.getZ());
			world.getServer().runCommand("difficulty hard");
			// The client fades a storm in, a hundredth a tick: it only counts as one past nine tenths.
			context.waitTicks(150);

			boolean seen = context.computeOnClient(client -> client.level.isThundering()
					&& client.level.clockManager().getInstance(client.level.registryAccess()
						.lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD)).totalTicks() >= NIGHT
					&& client.level.getLevelData().getDifficulty() == Difficulty.HARD
					&& client.level.getRespawnData().pos().equals(SPAWN));

			if (!seen) {
				throw new AssertionError("hazırlık başarısız: istemci fırtınayı, zorluğu ya da doğma noktasını görmedi: "
						+ context.computeOnClient(client -> "saat " + client.level.clockManager().getInstance(client.level.registryAccess()
						.lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD)).totalTicks()
						+ ", gök gürültüsü " + client.level.isThundering() + ", zorluk "
						+ client.level.getLevelData().getDifficulty() + ", doğma " + client.level.getRespawnData().pos()));
			}

			WdlTestSupport.setEnabled(context, true);
			context.waitTicks(30);
			WdlTestSupport.setEnabled(context, false);
		}, reopened -> reopened.getServer().runOnServer(server -> {
			var level = server.overworld();
			var clock = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
			long ticks = server.clockManager().getInstance(clock).totalTicks();

			if (ticks < NIGHT || ticks > NIGHT + CLOCK_SLACK) {
				throw new AssertionError("saat korunmadı: " + NIGHT + " civarı bekleniyordu, bulunan " + ticks);
			}

			if (!level.isThundering() || !level.isRaining()) {
				throw new AssertionError("fırtına korunmadı: yağmur " + level.isRaining() + ", gök gürültüsü " + level.isThundering());
			}

			BlockPos spawn = level.getRespawnData().pos();

			if (!spawn.equals(SPAWN)) {
				throw new AssertionError("doğma noktası korunmadı: beklenen " + SPAWN + ", bulunan " + spawn);
			}

			if (server.getWorldData().getDifficulty() != Difficulty.HARD) {
				throw new AssertionError("zorluk korunmadı: beklenen HARD, bulunan " + server.getWorldData().getDifficulty());
			}
		}));

		SurvivalKit.LOGGER.info("[gametest] dünya bilgileri indirildi: saat {}, fırtına, doğma noktası {}, zorluk zor", NIGHT, SPAWN);
	}

	private static long serverClock(net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> server.clockManager().getInstance(
				server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD)).totalTicks());
	}
}
