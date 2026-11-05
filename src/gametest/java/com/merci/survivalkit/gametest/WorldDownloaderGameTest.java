package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import com.merci.survivalkit.settings.TextSetting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

/**
 * Downloads a chunk out of a live client and reads it back off the disk with vanilla's own
 * parser, so "it wrote a file" is not mistaken for "it wrote a world".
 */
public class WorldDownloaderGameTest implements FabricClientGameTest {
	private static final long SEED = 1234567890123L;
	private static final BlockPos MARKER = new BlockPos(8, 80, 8);

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(5);

			// A block the generator would never place here, so a match cannot be luck.
			world.getServer().runCommand("setblock " + MARKER.getX() + " " + MARKER.getY() + " " + MARKER.getZ() + " minecraft:gold_block");
			world.getServer().runCommand("tp @a " + MARKER.getX() + " " + (MARKER.getY() + 2) + " " + MARKER.getZ());
			context.waitTicks(20);

			assertClientSees(context);

			context.runOnClient(client -> module().settings().<TextSetting>byId("seed").orElseThrow().set(String.valueOf(SEED)));
			setEnabled(context, true);
			context.waitTicks(5);
			setEnabled(context, false);
			context.waitTicks(5);

			Path savePath = context.computeOnClient(client -> module().lastSavePath()
					.orElseThrow(() -> new AssertionError("indirme klasörü oluşmadı")));

			assertSavedWorldHasTheMarker(context, savePath);
			assertTheWorldCanBeOpened(world, savePath);
			SurvivalKit.LOGGER.info("[gametest] WDL doğrulandı -> {}", savePath);
		}
	}

	private static void assertClientSees(ClientGameTestContext context) {
		boolean visible = context.computeOnClient(client -> client.level.getBlockState(MARKER).is(Blocks.GOLD_BLOCK));

		if (!visible) {
			throw new AssertionError("hazırlık başarısız: işaret bloğu client'a ulaşmadı");
		}
	}

	/** Reads the written region file back through vanilla's own chunk parser. */
	private static void assertSavedWorldHasTheMarker(ClientGameTestContext context, Path savePath) {
		ChunkPos chunkPos = ChunkPos.containing(MARKER);
		// 26.2 stores every dimension under dimensions/<namespace>/<path>, the overworld included.
		Path region = DimensionType.getStorageFolder(Level.OVERWORLD, savePath).resolve("region");
		RegionStorageInfo info = new RegionStorageInfo(savePath.getFileName().toString(), Level.OVERWORLD, "chunk");
		CompoundTag stored;

		try (RegionFileStorage storage = new RegionFileStorage(info, region, false)) {
			stored = storage.read(chunkPos);
		} catch (IOException e) {
			throw new AssertionError("bölge dosyası okunamadı: " + region, e);
		}

		if (stored == null) {
			throw new AssertionError("chunk " + chunkPos + " kaydedilmemiş: " + region);
		}

		BlockState saved = context.computeOnClient(client -> {
			SerializableChunkData data = SerializableChunkData.parse(client.level, client.level.palettedContainerFactory(), stored);
			return blockAt(data, MARKER);
		});

		if (!saved.is(Blocks.GOLD_BLOCK)) {
			throw new AssertionError("kaydedilen dünyada işaret bloğu yok, bulunan: " + saved);
		}
	}

	private static BlockState blockAt(SerializableChunkData data, BlockPos pos) {
		int sectionY = pos.getY() >> 4;

		for (SerializableChunkData.SectionData section : data.sectionData()) {
			LevelChunkSection contents = section.chunkSection();

			if (section.y() == sectionY && contents != null) {
				return contents.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
			}
		}

		throw new AssertionError("kaydedilen chunk'ta " + sectionY + " numaralı bölüm yok");
	}

	private static WorldDownloaderModule module() {
		return (WorldDownloaderModule) SurvivalKit.modules()
				.byId("wdl")
				.orElseThrow(() -> new AssertionError("wdl modülü kayıtlı değil"));
	}

	private static void setEnabled(ClientGameTestContext context, boolean enabled) {
		context.runOnClient(client -> module().setEnabled(enabled));
	}

	/**
	 * level.dat and world_gen_settings.dat are hand-built, so they are decoded again with
	 * vanilla's own codecs — on the server, the only side that holds the worldgen registries.
	 */
	private static void assertTheWorldCanBeOpened(TestSingleplayerContext world, Path savePath) {
		world.getServer().runOnServer(server -> {
			CompoundTag levelData = readCompressed(savePath.resolve("level.dat")).getCompoundOrEmpty("Data");

			if (levelData.getStringOr("LevelName", "").isEmpty()) {
				throw new AssertionError("level.dat okunabilir bir dünya adı içermiyor");
			}

			CompoundTag stored = readCompressed(savePath.resolve("data").resolve("minecraft").resolve("world_gen_settings.dat"));
			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
			WorldGenSettings settings = WorldGenSettings.CODEC
					.parse(ops, stored.getCompoundOrEmpty("data"))
					.getOrThrow(message -> new AssertionError("world_gen_settings.dat çözülemedi: " + message));

			if (settings.options().seed() != SEED) {
				throw new AssertionError("seed korunmadı, bulunan " + settings.options().seed());
			}

			int dimensions = settings.dimensions().dimensions().size();

			if (dimensions != 3) {
				throw new AssertionError("üç boyut bekleniyordu, bulunan " + dimensions);
			}
		});
	}

	private static CompoundTag readCompressed(Path file) {
		try {
			return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		} catch (IOException e) {
			throw new AssertionError("okunamadı: " + file, e);
		}
	}
}
