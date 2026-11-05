package com.merci.survivalkit.wdl;

import com.mojang.serialization.Lifecycle;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.util.FileUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;

/**
 * Writes the two files that make a folder of region files into a world the game will open:
 * {@code level.dat} and {@code data/minecraft/world_gen_settings.dat}.
 *
 * <p>The world gen settings are built by hand rather than through
 * {@code WorldGenSettings.of}, because that needs the LEVEL_STEM registry and a connected
 * client never receives it — LEVEL_STEM sits in {@code RegistryDataLoader.DIMENSION_REGISTRIES},
 * not in {@code SYNCHRONIZED_REGISTRIES}. What is written here is the vanilla default set of
 * three dimensions, which is what an unmodified server generated anyway.
 */
public final class LevelDataWriter {
	private LevelDataWriter() {
	}

	public static void write(LevelStorageSource.LevelStorageAccess access, String worldName, long seed) throws IOException {
		write(access, worldName, seed, null, null);
	}

	/**
	 * @param info       the world as the client last saw it, or null to write defaults
	 * @param registries what the clock and weather files are encoded with; only read with {@code info}
	 */
	public static void write(LevelStorageSource.LevelStorageAccess access, String worldName, long seed, WorldInfo info,
			net.minecraft.core.HolderLookup.Provider registries) throws IOException {
		LevelSettings settings = new LevelSettings(
			worldName,
			GameType.CREATIVE,
			new LevelSettings.DifficultySettings(info == null ? Difficulty.NORMAL : info.difficulty(), false,
				info != null && info.difficultyLocked()),
			true,
			WorldDataConfiguration.DEFAULT
		);
		PrimaryLevelData levelData = new PrimaryLevelData(settings, PrimaryLevelData.SpecialWorldProperty.NONE, Lifecycle.stable());
		levelData.setInitialized(true);

		if (info != null) {
			levelData.setGameTime(info.gameTime());
			levelData.setSpawn(info.spawn());
		}

		access.saveDataTag(levelData);

		if (info != null && registries != null) {
			writeClockAndWeather(access.getLevelPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data"), info, registries);
		}

		Path folder = access.getLevelPath(net.minecraft.world.level.storage.LevelResource.ROOT);
		writeWorldGenSettings(folder, Seed.keep(seed, existingSeed(folder)));
	}

	/**
	 * The time of day and the weather, which 26.2 keeps out of {@code level.dat}: the overworld
	 * clock in {@code world_clocks}, the weather in {@code weather}. Written through the game's own
	 * saved data storage, so they land where and how it reads them.
	 *
	 * <p>The weather is written with no time left on it: the game then picks how long it lasts,
	 * the way it would have, rather than it ending on the first tick.
	 */
	private static void writeClockAndWeather(Path dataFolder, WorldInfo info, net.minecraft.core.HolderLookup.Provider registries)
			throws IOException {
		var ops = registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
		var overworld = registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_CLOCK)
			.getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD);
		var clocks = new net.minecraft.world.clock.PackedClockStates(
			java.util.Map.of(overworld, new net.minecraft.world.clock.ClockState(info.clockTicks(), 0.0F, 1.0F, false)));
		// Written by hand in the file's own shape: the clock manager only packs the clocks it was
		// handed by a running server, and one built outside a server writes them empty.
		CompoundTag clockFile = new CompoundTag();
		clockFile.put("data", net.minecraft.world.clock.PackedClockStates.CODEC.encodeStart(ops, clocks).getOrThrow());
		NbtUtils.addCurrentDataVersion(clockFile);
		Path clockPath = dataFolder.resolve("minecraft").resolve("world_clocks.dat");
		FileUtil.createDirectoriesSafe(clockPath.getParent());
		NbtIo.writeCompressed(clockFile, clockPath);

		try (var storage = new net.minecraft.world.level.storage.SavedDataStorage(dataFolder,
				net.minecraft.util.datafix.DataFixers.getDataFixer(), registries)) {
			var weather = new net.minecraft.world.level.saveddata.WeatherData(0, 0, 0, info.raining(), info.thundering());
			weather.setDirty();
			storage.set(net.minecraft.world.level.saveddata.WeatherData.TYPE, weather);
			storage.saveAndJoin();
		}
	}

	/** The seed an earlier download into this folder wrote, if there was one. */
	private static java.util.OptionalLong existingSeed(Path worldFolder) {
		Path file = worldGenSettings(worldFolder);

		if (!java.nio.file.Files.exists(file)) {
			return java.util.OptionalLong.empty();
		}

		try {
			CompoundTag data = NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap()).getCompoundOrEmpty("data");
			return data.contains("seed") ? java.util.OptionalLong.of(data.getLongOr("seed", Seed.NONE)) : java.util.OptionalLong.empty();
		} catch (IOException unreadable) {
			return java.util.OptionalLong.empty();
		}
	}

	private static Path worldGenSettings(Path worldFolder) {
		return worldFolder.resolve("data").resolve("minecraft").resolve("world_gen_settings.dat");
	}

	private static void writeWorldGenSettings(Path worldFolder, long seed) throws IOException {
		CompoundTag dimensions = new CompoundTag();
		dimensions.put("minecraft:overworld", stem("minecraft:overworld", noise("minecraft:overworld", multiNoise("minecraft:overworld"))));
		dimensions.put("minecraft:the_nether", stem("minecraft:the_nether", noise("minecraft:nether", multiNoise("minecraft:nether"))));
		dimensions.put("minecraft:the_end", stem("minecraft:the_end", noise("minecraft:end", theEndBiomeSource())));

		CompoundTag data = new CompoundTag();
		data.putLong("seed", seed);
		data.putBoolean("generate_structures", true);
		data.putBoolean("bonus_chest", false);
		data.put("dimensions", dimensions);

		CompoundTag root = new CompoundTag();
		root.put("data", data);
		NbtUtils.addCurrentDataVersion(root);

		// The game resolves saved data by namespaced id, so the namespace is part of the path:
		// data/<namespace>/<path>.dat. Writing it flat under data/ leaves a world the game
		// refuses to load, falling back to a random seed before giving up.
		Path file = worldGenSettings(worldFolder);
		FileUtil.createDirectoriesSafe(file.getParent());
		NbtIo.writeCompressed(root, file);
	}

	private static CompoundTag stem(String dimensionType, CompoundTag generator) {
		CompoundTag stem = new CompoundTag();
		stem.putString("type", dimensionType);
		stem.put("generator", generator);
		return stem;
	}

	private static CompoundTag noise(String noiseSettings, CompoundTag biomeSource) {
		CompoundTag generator = new CompoundTag();
		generator.putString("type", "minecraft:noise");
		generator.putString("settings", noiseSettings);
		generator.put("biome_source", biomeSource);
		return generator;
	}

	private static CompoundTag multiNoise(String preset) {
		CompoundTag source = new CompoundTag();
		source.putString("type", "minecraft:multi_noise");
		source.putString("preset", preset);
		return source;
	}

	private static CompoundTag theEndBiomeSource() {
		CompoundTag source = new CompoundTag();
		source.putString("type", "minecraft:the_end");
		return source;
	}
}
