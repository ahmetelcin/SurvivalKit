package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.AABB;

/**
 * Proves that entities seen by the client are saved into the downloaded world's
 * entities/*.mca files and survive reopening in singleplayer:
 * 1. An item frame with a specific item inside
 * 2. An armor stand wearing armor
 * 3. A named animal
 *
 * Also verifies that players (both self and others) are strictly excluded from entity storage.
 */
public class EntityDownloadGameTest implements FabricClientGameTest {
	private static final BlockPos SUPPORT_BLOCK = new BlockPos(8, 81, 8);
	private static final BlockPos FRAME_POS = new BlockPos(8, 81, 9);
	private static final double STAND_X = 9.5;
	private static final double STAND_Y = 80.0;
	private static final double STAND_Z = 8.5;
	private static final double COW_X = 7.5;
	private static final double COW_Y = 80.0;
	private static final double COW_Z = 8.5;
	private static final String COW_NAME = "Pamuk";

	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		Path downloaded;

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			save = world.getWorldSave();
			downloaded = downloadWorldWithEntities(context, world);
		}

		// Verify on-disk entity file before reopening
		assertOnDiskEntitiesFileHasNoPlayersAndHasEntities(downloaded);

		// Reopen downloaded world in singleplayer
		replaceContents(save.getSaveDirectory(), downloaded);

		try (TestSingleplayerContext reopened = save.open()) {
			assertEntitiesSurvivedTheReopen(reopened);
		}

		SurvivalKit.LOGGER.info("[gametest] varlıklar indirilen dünyada başarıyla korundu -> {}",
				downloaded.getFileName());
	}

	private static Path downloadWorldWithEntities(ClientGameTestContext context, TestSingleplayerContext world) {
		context.waitTicks(5);

		// Spawn markers on server
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.getLevel(Level.OVERWORLD);

			// Platform under entities
			for (int x = 6; x <= 11; x++) {
				for (int z = 6; z <= 11; z++) {
					level.setBlock(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState(), 3);
				}
			}

			// Support block
			level.setBlock(SUPPORT_BLOCK, Blocks.STONE.defaultBlockState(), 3);

			// 1. Item frame with a golden sword
			ItemFrame frame = new ItemFrame(level, FRAME_POS, Direction.SOUTH);
			frame.setItem(new ItemStack(Items.GOLDEN_SWORD));
			level.addFreshEntity(frame);

			// 2. Armor stand with diamond chestplate
			ArmorStand stand = new ArmorStand(level, STAND_X, STAND_Y, STAND_Z);
			stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
			stand.setNoGravity(true);
			level.addFreshEntity(stand);

			// 3. Named cow "Pamuk"
			Cow cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
			if (cow == null) {
				throw new AssertionError("inek varlığı oluşturulamadı");
			}
			cow.setPos(COW_X, COW_Y, COW_Z);
			cow.setNoGravity(true);
			cow.setCustomName(Component.literal(COW_NAME));
			cow.setCustomNameVisible(true);
			level.addFreshEntity(cow);
		});

		// Position player nearby so entities are well within entity tracking range
		world.getServer().runCommand("tp @a 8.5 81.0 11.0 0 0");
		context.waitTicks(10);

		// Wait until client receives and loads all 3 marker entities
		context.waitFor(client -> {
			if (client.level == null) return false;
			boolean hasFrame = false;
			boolean hasStand = false;
			boolean hasCow = false;
			for (Entity e : client.level.entitiesForRendering()) {
				if (e instanceof ItemFrame f && f.getItem().is(Items.GOLDEN_SWORD)) {
					hasFrame = true;
				}
				if (e instanceof ArmorStand s && s.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE)) {
					hasStand = true;
				}
				if (e instanceof Cow c && c.getCustomName() != null && COW_NAME.equals(c.getCustomName().getString())) {
					hasCow = true;
				}
			}
			return hasFrame && hasStand && hasCow;
		});
		context.waitTicks(5);

		// Start WDL, download, stop
		setEnabled(context, true);
		context.waitTicks(5);
		setEnabled(context, false);
		context.waitTicks(5);

		return context.computeOnClient(client -> module().lastSavePath()
				.orElseThrow(() -> new AssertionError("indirme klasörü oluşmadı")));
	}

	private static void assertOnDiskEntitiesFileHasNoPlayersAndHasEntities(Path downloaded) {
		ChunkPos chunkPos = ChunkPos.containing(FRAME_POS);
		Path region = DimensionType.getStorageFolder(Level.OVERWORLD, downloaded).resolve("entities");
		RegionStorageInfo info = new RegionStorageInfo(downloaded.getFileName().toString(), Level.OVERWORLD, "entities");
		CompoundTag stored;

		try (RegionFileStorage storage = new RegionFileStorage(info, region, false)) {
			stored = storage.read(chunkPos);
		} catch (IOException e) {
			throw new AssertionError("varlık bölge dosyası okunamadı: " + region, e);
		}

		if (stored == null) {
			throw new AssertionError("varlıklar kaydedilmemiş, " + chunkPos + " boş");
		}

		ListTag entities = stored.getListOrEmpty("Entities");
		if (entities.isEmpty()) {
			throw new AssertionError("Entities listesi boş");
		}

		for (Tag tag : entities) {
			if (tag instanceof CompoundTag comp) {
				String id = comp.getStringOr("id", "");
				if ("minecraft:player".equals(id)) {
					throw new AssertionError("HATA: Oyuncu varlık dosyasına kaydedilmiş!");
				}
			}
		}
	}

	private static void assertEntitiesSurvivedTheReopen(TestSingleplayerContext reopened) {
		reopened.getServer().runOnServer(server -> {
			ServerLevel level = server.getLevel(Level.OVERWORLD);
			AABB searchBox = new AABB(4, 70, 4, 15, 90, 15);
			List<Entity> entities = level.getEntities((Entity) null, searchBox, e -> true);

			ItemFrame foundFrame = null;
			ArmorStand foundStand = null;
			Cow foundCow = null;

			for (Entity entity : entities) {
				if (entity instanceof ItemFrame frame) {
					foundFrame = frame;
				} else if (entity instanceof ArmorStand stand) {
					foundStand = stand;
				} else if (entity instanceof Cow cow) {
					foundCow = cow;
				}
			}

			if (foundFrame == null) {
				throw new AssertionError("yeniden açılan dünyada eşya çerçevesi bulunamadı");
			}
			if (!foundFrame.getItem().is(Items.GOLDEN_SWORD)) {
				throw new AssertionError("eşya çerçevesindeki eşya korunmadı, bulunan: " + foundFrame.getItem());
			}

			if (foundStand == null) {
				throw new AssertionError("yeniden açılan dünyada zırh standı bulunamadı");
			}
			if (!foundStand.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE)) {
				throw new AssertionError("zırh standındaki zırh korunmadı, bulunan göğüslük: "
						+ foundStand.getItemBySlot(EquipmentSlot.CHEST));
			}

			if (foundCow == null) {
				throw new AssertionError("yeniden açılan dünyada adı verilmiş hayvan bulunamadı");
			}
			if (foundCow.getCustomName() == null || !COW_NAME.equals(foundCow.getCustomName().getString())) {
				throw new AssertionError("hayvanın adı korunmadı, bulunan: " + foundCow.getCustomName());
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
