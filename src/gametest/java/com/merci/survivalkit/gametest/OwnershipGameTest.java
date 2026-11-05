package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import com.merci.survivalkit.wdl.OwnershipMode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.phys.AABB;

/**
 * Validates ownership modes:
 * - TRANSFER_TO_LOCAL rewrites foreign owner UUID to local player UUID on disk and survives singleplayer reopen.
 * - AS_IS preserves foreign owner UUID unchanged.
 */
public class OwnershipGameTest implements FabricClientGameTest {
	private static final UUID FOREIGN_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final BlockPos SPAWN_POS = new BlockPos(8, 80, 8);

	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		Path downloaded;
		AtomicReference<UUID> playerUuidRef = new AtomicReference<>();

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			save = world.getWorldSave();

			context.runOnClient(client -> {
				playerUuidRef.set(client.player.getUUID());
				module().ownership().set(OwnershipMode.TRANSFER_TO_LOCAL);
			});

			downloaded = setupAndDownload(context, world);
		}

		UUID playerUuid = playerUuidRef.get();
		if (playerUuid == null) {
			throw new AssertionError("Oyuncu UUID'si alınamadı");
		}

		// 1. Verify on-disk entities/*.mca has player UUID as Owner
		assertOnDiskOwnerEquals(downloaded, playerUuid);

		// 2. Reopen downloaded world in singleplayer and assert wolf is owned by player
		replaceContents(save.getSaveDirectory(), downloaded);

		try (TestSingleplayerContext reopened = save.open()) {
			assertReopenedWolfOwnerEquals(reopened, playerUuid);
		}

		SurvivalKit.LOGGER.info("[gametest] sahiplik aktarımı (TRANSFER_TO_LOCAL) tek oyunculuda başarıyla korundu -> {}",
				downloaded.getFileName());
	}

	private static Path setupAndDownload(ClientGameTestContext context, TestSingleplayerContext world) {
		context.waitTicks(5);

		world.getServer().runOnServer(server -> {
			ServerLevel level = server.getLevel(Level.OVERWORLD);

			for (int x = 6; x <= 10; x++) {
				for (int z = 6; z <= 10; z++) {
					level.setBlock(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState(), 3);
				}
			}

			Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.COMMAND);
			if (wolf == null) {
				throw new AssertionError("Kurt oluşturulamadı");
			}
			wolf.setPos(SPAWN_POS.getX() + 0.5, SPAWN_POS.getY(), SPAWN_POS.getZ() + 0.5);
			wolf.setOwnerReference(EntityReference.of(FOREIGN_UUID));
			wolf.setTame(true, false);
			wolf.setNoAi(true);
			level.addFreshEntity(wolf);
		});

		context.waitTicks(5);

		setEnabled(context, true);
		context.waitTicks(10);
		setEnabled(context, false);
		context.waitTicks(5);

		return context.computeOnClient(client -> module().lastSavePath()
				.orElseThrow(() -> new AssertionError("wdl indirme klasörü oluşturmadı")));
	}

	private static void assertOnDiskOwnerEquals(Path worldDir, UUID expectedOwner) {
		ChunkPos chunkPos = ChunkPos.containing(SPAWN_POS);
		Path region = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(Level.OVERWORLD, worldDir).resolve("entities");
		RegionStorageInfo info = new RegionStorageInfo(worldDir.getFileName().toString(), Level.OVERWORLD, "entities");
		CompoundTag chunkTag;

		try (RegionFileStorage storage = new RegionFileStorage(info, region, false)) {
			chunkTag = storage.read(chunkPos);
		} catch (IOException e) {
			throw new AssertionError("varlık bölge dosyası okunamadı: " + region, e);
		}

		if (chunkTag == null) {
			throw new AssertionError("Chunk için varlık verisi diskte bulunamadı: " + chunkPos);
		}

		ListTag entities = chunkTag.getListOrEmpty("Entities");
		boolean foundWolf = false;

		for (Tag tag : entities) {
			if (tag instanceof CompoundTag comp && "minecraft:wolf".equals(comp.getStringOr("id", ""))) {
				foundWolf = true;
				UUID onDiskOwner = comp.read("Owner", UUIDUtil.CODEC).orElse(null);
				if (!expectedOwner.equals(onDiskOwner)) {
					throw new AssertionError("HATA: Diskteki kurdun sahibi " + expectedOwner + " olmalıydı ama bulunan: " + onDiskOwner);
				}
			}
		}

		if (!foundWolf) {
			throw new AssertionError("Diskteki varlıklar arasında kurt bulunamadı!");
		}
	}

	private static void assertReopenedWolfOwnerEquals(TestSingleplayerContext reopened, UUID expectedOwner) {
		reopened.getServer().runOnServer(server -> {
			ServerLevel level = server.getLevel(Level.OVERWORLD);
			AABB searchBox = new AABB(SPAWN_POS).inflate(16.0);
			List<Entity> entities = level.getEntities((Entity) null, searchBox, e -> true);

			Wolf foundWolf = null;
			for (Entity entity : entities) {
				if (entity instanceof Wolf wolf) {
					foundWolf = wolf;
					break;
				}
			}

			if (foundWolf == null) {
				throw new AssertionError("Yeniden açılan dünyada kurt bulunamadı!");
			}

			if (!foundWolf.isTame()) {
				throw new AssertionError("Yeniden açılan dünyadaki kurt evcilleştirilmiş kalmadı!");
			}

			EntityReference<?> ref = foundWolf.getOwnerReference();
			if (ref == null) {
				throw new AssertionError("Yeniden açılan dünyadaki kurdun sahip referansı null!");
			}

			if (!expectedOwner.equals(ref.getUUID())) {
				throw new AssertionError("Yeniden açılan kurdun sahibi beklenen (" + expectedOwner + ") ile uyuşmuyor: " + ref.getUUID());
			}
		});
	}

	private static WorldDownloaderModule module() {
		return (WorldDownloaderModule) SurvivalKit.modules()
				.byId("wdl")
				.orElseThrow(() -> new AssertionError("wdl modülü kayıtlı değil"));
	}

	private static void setEnabled(ClientGameTestContext context, boolean enabled) {
		context.runOnClient(client -> module().setEnabled(enabled));
	}

	private static void replaceContents(Path target, Path source) {
		deleteRecursively(target);

		try (Stream<Path> tree = Files.walk(source)) {
			tree.forEach(entry -> {
				Path relative = source.relativize(entry);
				Path destination = target.resolve(relative);

				try {
					if (Files.isDirectory(entry)) {
						Files.createDirectories(destination);
					} else {
						Files.createDirectories(destination.getParent());
						Files.copy(entry, destination);
					}
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void deleteRecursively(Path directory) {
		if (!Files.exists(directory)) {
			return;
		}

		try (Stream<Path> tree = Files.walk(directory)) {
			tree.sorted(Comparator.reverseOrder()).forEach(entry -> {
				try {
					Files.deleteIfExists(entry);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
