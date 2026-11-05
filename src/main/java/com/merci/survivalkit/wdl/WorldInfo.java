package com.merci.survivalkit.wdl;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.storage.LevelData;

/**
 * What the world was like around the download, beyond its chunks: the time of day, the weather,
 * where players spawn and how hard the game is.
 *
 * <p>Taken from the overworld the client is shown. In 26.2 these no longer all live in
 * {@code level.dat}: the time of day is the overworld clock, and the weather is a saved data file
 * of its own. {@link LevelDataWriter} puts each where the game reads it.
 *
 * @param gameTime   ticks the world has run, which scheduled things count from
 * @param clockTicks the overworld clock: the time of day, and how many days have gone by
 */
public record WorldInfo(long gameTime, long clockTicks, boolean raining, boolean thundering,
		LevelData.RespawnData spawn, Difficulty difficulty, boolean difficultyLocked) {

	public static WorldInfo of(ClientLevel level) {
		Holder<WorldClock> overworld = level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
		ClientLevel.ClientLevelData data = level.getLevelData();

		return new WorldInfo(data.getGameTime(), level.clockManager().getInstance(overworld).totalTicks(), level.isRaining(),
				level.isThundering(), data.getRespawnData(), data.getDifficulty(), data.isDifficultyLocked());
	}
}
