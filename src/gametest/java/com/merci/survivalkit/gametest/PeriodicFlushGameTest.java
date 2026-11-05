package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Proves that periodic disk flushing automatically writes chunks to the .mca region file
 * on disk while the download is actively running, without closing the session.
 */
public class PeriodicFlushGameTest implements FabricClientGameTest {
	private static final BlockPos MARKER = new BlockPos(8, 80, 8);

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(5);

			// Position player and place an initial block before downloading starts
			world.getServer().runCommand("setblock " + MARKER.getX() + " " + MARKER.getY() + " " + MARKER.getZ() + " minecraft:stone");
			world.getServer().runCommand("tp @a 8.5 81.0 8.5 0 0");
			context.waitTicks(10);

			boolean stoneSeen = context.computeOnClient(client ->
					client.level != null && client.level.getBlockState(MARKER).is(Blocks.STONE));
			if (!stoneSeen) {
				throw new AssertionError("hazırlık başarısız: başlangıç taşı client'a ulaşmadı");
			}

			// Configure flush interval to 1 second (20 ticks) so periodic flush fires quickly
			context.runOnClient(client -> module().flushInterval().set(1));

			// Start downloading. onEnable() writes the initial state (stone) to disk
			setEnabled(context, true);
			context.waitTicks(5);

			Path savePath = context.computeOnClient(client -> module().lastSavePath()
					.orElseThrow(() -> new AssertionError("indirme klasörü oluşmadı")));

			// Change block to emerald on server. Client receives the update in memory,
			// but has NOT unloaded the chunk, and session has NOT stopped.
			world.getServer().runCommand("setblock " + MARKER.getX() + " " + MARKER.getY() + " " + MARKER.getZ() + " minecraft:emerald_block");
			context.waitFor(client -> client.level != null && client.level.getBlockState(MARKER).is(Blocks.EMERALD_BLOCK));

			// Wait 25 ticks (> 20 ticks = 1 second) for flushTicker to trigger periodic flush
			context.waitTicks(25);

			// Verify that the download session is STILL OPEN and active
			boolean sessionActive = context.computeOnClient(client -> module().isEnabled() && module().session().isPresent());
			if (!sessionActive) {
				throw new AssertionError("oturum indirme sırasında açık kalmalıydı");
			}

			// Read directly from disk region file to prove periodic flush wrote to disk
			assertSavedRegionHasBlock(context, savePath, MARKER, Blocks.EMERALD_BLOCK);

			// Clean shutdown
			setEnabled(context, false);
			context.waitTicks(5);

			SurvivalKit.LOGGER.info("[gametest] periyodik kayıt oturum açıkken başarıyla diske yazdı -> {}", savePath);
		}
	}

	private static void assertSavedRegionHasBlock(ClientGameTestContext context, Path savePath, BlockPos pos, net.minecraft.world.level.block.Block expectedBlock) {
		ChunkPos chunkPos = ChunkPos.containing(pos);
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
			return blockAt(data, pos);
		});

		if (!saved.is(expectedBlock)) {
			throw new AssertionError("periyodik kayıt diske yazılmadı: beklenen " + expectedBlock + ", diskteki: " + saved);
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
}
