package com.merci.survivalkit.wdl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The region and entity files of one dimension.
 *
 * <p>Vanilla wraps region files in an {@code IOWorker}, whose constructor is not public,
 * so this runs the same pattern on its own single thread: {@link RegionFileStorage} is
 * not thread safe, and every call here is queued onto that one thread. Writes therefore
 * never touch the render thread, and the game does not stutter while a chunk is saved.
 */
public final class DimensionWriter implements AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(DimensionWriter.class);
	private static final long SHUTDOWN_SECONDS = 60;

	private final ExecutorService io;
	private final RegionFileStorage chunks;
	private final RegionFileStorage entities;
	private final java.util.Set<ChunkPos> chunksWithEntities = new java.util.HashSet<>();

	public DimensionWriter(String levelId, ResourceKey<Level> dimension, Path dimensionPath) {
		RegionStorageInfo info = new RegionStorageInfo(levelId, dimension, "chunk");
		this.chunks = new RegionFileStorage(info, dimensionPath.resolve("region"), false);
		this.entities = new RegionFileStorage(info.withTypeSuffix("entities"), dimensionPath.resolve("entities"), false);
		this.io = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "SurvivalKit WDL " + dimension.identifier().getPath());
			thread.setDaemon(true);
			return thread;
		});
	}

	public void storeChunk(ChunkPos pos, CompoundTag tag) {
		// Read back first: continuing a world, the chunk may already be on disk from an
		// earlier download, and what that one saw inside its chests is not sent again.
		submit("chunk " + pos, () -> chunks.write(pos, ChunkContinuation.carryOver(tag, chunks.read(pos))));
	}

	public void storeEntities(ChunkPos pos, CompoundTag tag) {
		submit("entity " + pos, () -> {
			if (tag != null) {
				chunksWithEntities.add(pos);
				entities.write(pos, tag);
			} else if (chunksWithEntities.remove(pos)) {
				entities.write(pos, null);
			}
		});
	}

	/** Blocks until everything queued so far has reached the disk. */
	public void flush() {
		submit("flush", () -> {
			chunks.flush();
			entities.flush();
		});
		awaitQueue();
	}

	@Override
	public void close() {
		submit("close", () -> {
			chunksWithEntities.clear();
			chunks.close();
			entities.close();
		});
		io.shutdown();

		try {
			if (!io.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
				LOGGER.error("Bölge dosyaları {} saniyede kapanmadı, yazım yarıda kalmış olabilir", SHUTDOWN_SECONDS);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void awaitQueue() {
		try {
			io.submit(() -> {
			}).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			LOGGER.error("Yazım kuyruğu beklenirken hata", e);
		}
	}

	private void submit(String what, IoTask task) {
		io.execute(() -> {
			try {
				task.run();
			} catch (IOException e) {
				LOGGER.error("WDL yazımı başarısız: {}", what, e);
			}
		});
	}

	@FunctionalInterface
	private interface IoTask {
		void run() throws IOException;
	}
}
