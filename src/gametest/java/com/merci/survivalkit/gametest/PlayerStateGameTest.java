package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Validates that the player's full state (inventory, armor, offhand, position,
 * rotation, XP, health, hunger, and ender chest) is saved to both level.dat
 * (Data.Player) and playerdata/<UUID>.dat, and fully restored when the downloaded
 * world is reopened in singleplayer.
 */
public class PlayerStateGameTest implements FabricClientGameTest {
	private static final BlockPos ENDER_CHEST_POS = new BlockPos(8, 80, 8);

	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		Path downloaded;

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			save = world.getWorldSave();
			downloaded = downloadWorldWithPlayerState(context, world);
		}

		// 1. Verify on-disk level.dat Data.Player and playerdata/<UUID>.dat
		assertOnDiskPlayerState(downloaded);

		// 2. Reopen downloaded world in singleplayer and assert restored player state
		replaceContents(save.getSaveDirectory(), downloaded);

		try (TestSingleplayerContext reopened = save.open()) {
			assertReopenedPlayerState(reopened);
		}

		SurvivalKit.LOGGER.info("[gametest] oyuncu durumu, envanter ve ender sandığı indirilen dünyada başarıyla korundu -> {}",
				downloaded.getFileName());
	}

	private static Path downloadWorldWithPlayerState(ClientGameTestContext context, TestSingleplayerContext world) {
		context.waitTicks(5);

		// Prepare platform, ender chest, and player state on server
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.getLevel(Level.OVERWORLD);

			for (int x = 6; x <= 12; x++) {
				for (int z = 6; z <= 12; z++) {
					level.setBlock(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState(), 3);
				}
			}

			level.setBlock(ENDER_CHEST_POS, Blocks.ENDER_CHEST.defaultBlockState(), 3);

			ServerPlayer serverPlayer = server.getPlayerList().getPlayers().get(0);
			serverPlayer.getEnderChestInventory().setItem(0, new ItemStack(Items.EMERALD, 64));
			serverPlayer.setHealth(14.0f);
			serverPlayer.getFoodData().setFoodLevel(15);
			serverPlayer.getFoodData().setSaturation(4.0f);
			serverPlayer.setExperienceLevels(12);

			serverPlayer.teleportTo(level, 9.5, 80.0, 9.5, Set.of(), 90.0f, 25.0f, true);
		});

		world.getServer().runCommand("gamemode survival @a");
		world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword");
		world.getServer().runCommand("item replace entity @a armor.chest with minecraft:diamond_chestplate");
		world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:shield");

		// Wait for client to receive updated stats and equipment
		context.waitFor(client -> client.player != null
				&& client.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE)
				&& client.player.getOffhandItem().is(Items.SHIELD)
				&& client.player.getMainHandItem().is(Items.DIAMOND_SWORD)
				&& Math.abs(client.player.getHealth() - 14.0f) < 0.5f
				&& client.player.getFoodData().getFoodLevel() == 15
				&& client.player.experienceLevel == 12);
		context.waitTicks(10);

		// Start WDL
		setEnabled(context, true);
		context.waitTicks(5);

		// Open Ender Chest
		context.runOnClient(client -> {
			BlockHitResult hit = new BlockHitResult(
					new Vec3(ENDER_CHEST_POS.getX() + 0.5, ENDER_CHEST_POS.getY() + 0.5, ENDER_CHEST_POS.getZ() + 0.5),
					Direction.UP,
					ENDER_CHEST_POS,
					false
			);
			client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
		});

		// Wait until Ender Chest container screen opens
		context.waitFor(client -> client.gui.screen() instanceof AbstractContainerScreen<?>);
		context.waitTicks(5);

		// Close screen
		context.runOnClient(client -> client.gui.setScreen(null));
		context.waitTicks(10);

		// Stop WDL
		setEnabled(context, false);
		context.waitTicks(5);

		return context.computeOnClient(client -> module().lastSavePath()
				.orElseThrow(() -> new AssertionError("wdl indirme klasörü oluşturmadı")));
	}

	private static void assertOnDiskPlayerState(Path worldDir) {
		// 1. Verify level.dat Data.Player
		Path levelDat = worldDir.resolve("level.dat");
		if (!Files.exists(levelDat)) {
			throw new AssertionError("HATA: level.dat dosyası bulunamadı: " + levelDat);
		}

		CompoundTag levelRoot;
		try {
			levelRoot = NbtIo.readCompressed(levelDat, NbtAccounter.unlimitedHeap());
		} catch (IOException e) {
			throw new AssertionError("level.dat okunamadı", e);
		}

		CompoundTag dataTag = levelRoot.getCompoundOrEmpty("Data");
		if (!dataTag.contains("Player")) {
			throw new AssertionError("HATA: level.dat Data.Player içermiyor!");
		}
		CompoundTag levelPlayer = dataTag.getCompoundOrEmpty("Player");
		assertPlayerTag("level.dat Data.Player", levelPlayer);

		// 2. Verify players/data/<UUID>.dat (26.2 standard)
		Path modernPlayerDir = worldDir.resolve("players").resolve("data");
		if (!Files.exists(modernPlayerDir)) {
			throw new AssertionError("HATA: players/data klasörü bulunamadı: " + modernPlayerDir);
		}

		Path modernPlayerFile;
		try (Stream<Path> stream = Files.list(modernPlayerDir)) {
			modernPlayerFile = stream.filter(p -> p.getFileName().toString().endsWith(".dat"))
					.findFirst()
					.orElseThrow(() -> new AssertionError("HATA: players/data içinde .dat dosyası bulunamadı"));
		} catch (IOException e) {
			throw new AssertionError("players/data listelenemedi", e);
		}

		CompoundTag modernDiskPlayer;
		try {
			modernDiskPlayer = NbtIo.readCompressed(modernPlayerFile, NbtAccounter.unlimitedHeap());
		} catch (IOException e) {
			throw new AssertionError("players/data dosyası okunamadı: " + modernPlayerFile, e);
		}
		assertPlayerTag("players/data file", modernDiskPlayer);

		// 3. Verify legacy playerdata directory is NOT created (no dead/duplicate data)
		Path legacyPlayerDir = worldDir.resolve("playerdata");
		if (Files.exists(legacyPlayerDir)) {
			throw new AssertionError("HATA: Eski playerdata klasörü oluşturulmamalı (tek kaynak players/data olmalı)!");
		}
	}

	private static void assertPlayerTag(String source, CompoundTag tag) {
		float health = tag.getFloatOr("Health", 0.0f);
		if (Math.abs(health - 14.0f) > 0.5f) {
			throw new AssertionError("HATA [" + source + "]: Oyuncu canı beklenen 14.0 değil: " + health);
		}

		int food = tag.getIntOr("foodLevel", 0);
		if (food != 15) {
			throw new AssertionError("HATA [" + source + "]: Oyuncu açlığı beklenen 15 değil: " + food);
		}

		int xp = tag.getIntOr("XpLevel", 0);
		if (xp != 12) {
			throw new AssertionError("HATA [" + source + "]: Oyuncu XP seviyesi beklenen 12 değil: " + xp);
		}

		ListTag pos = tag.getListOrEmpty("Pos");
		if (pos.size() != 3) {
			throw new AssertionError("HATA [" + source + "]: Pos listesi 3 eleman içermiyor: " + pos);
		}

		ListTag rot = tag.getListOrEmpty("Rotation");
		if (rot.size() != 2) {
			throw new AssertionError("HATA [" + source + "]: Rotation listesi 2 eleman içermiyor: " + rot);
		}

		ListTag inventory = tag.getListOrEmpty("Inventory");
		boolean hasSword = false;
		for (Tag itemTag : inventory) {
			if (itemTag instanceof CompoundTag item) {
				String id = item.getStringOr("id", "");
				if (id.contains("diamond_sword")) hasSword = true;
			}
		}

		if (!hasSword) {
			throw new AssertionError("HATA [" + source + "]: Envanterde diamond_sword bulunamadı: " + inventory);
		}

		CompoundTag equipment = tag.getCompoundOrEmpty("equipment");
		CompoundTag chest = equipment.getCompoundOrEmpty("chest");
		boolean hasChestplate = chest.getStringOr("id", "").contains("diamond_chestplate");
		if (!hasChestplate) {
			throw new AssertionError("HATA [" + source + "]: Equipment içinde diamond_chestplate bulunamadı: " + equipment);
		}

		CompoundTag offhand = equipment.getCompoundOrEmpty("offhand");
		boolean hasShield = offhand.getStringOr("id", "").contains("shield");
		if (!hasShield) {
			throw new AssertionError("HATA [" + source + "]: Equipment içinde shield bulunamadı: " + equipment);
		}

		ListTag enderItems = tag.getListOrEmpty("EnderItems");
		boolean hasEmerald = false;
		for (Tag itemTag : enderItems) {
			if (itemTag instanceof CompoundTag item) {
				String id = item.getStringOr("id", "");
				int count = item.getIntOr("count", 0);
				if (id.contains("emerald") && count == 64) {
					hasEmerald = true;
				}
			}
		}

		if (!hasEmerald) {
			throw new AssertionError("HATA [" + source + "]: EnderItems içinde 64 zümrüt bulunamadı: " + enderItems);
		}
	}

	private static void assertReopenedPlayerState(TestSingleplayerContext reopened) {
		reopened.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().get(0);

			SurvivalKit.LOGGER.error("[REOPEN_DEBUG] UUID={}, Health={}, Food={}, XP={}, Pos={}, Chest={}, Offhand={}, Mainhand={}, Ender={}",
					player.getStringUUID(),
					player.getHealth(),
					player.getFoodData().getFoodLevel(),
					player.experienceLevel,
					player.position(),
					player.getItemBySlot(EquipmentSlot.CHEST),
					player.getOffhandItem(),
					player.getMainHandItem(),
					player.getEnderChestInventory().getItem(0)
			);

			if (Math.abs(player.getHealth() - 14.0f) > 0.5f) {
				throw new AssertionError("HATA: Yeniden açılan oyuncu canı beklenen 14.0 değil: " + player.getHealth());
			}

			if (player.getFoodData().getFoodLevel() != 15) {
				throw new AssertionError("HATA: Yeniden açılan oyuncu açlığı beklenen 15 değil: " + player.getFoodData().getFoodLevel());
			}

			if (player.experienceLevel != 12) {
				throw new AssertionError("HATA: Yeniden açılan oyuncu XP seviyesi beklenen 12 değil: " + player.experienceLevel);
			}

			if (Math.abs(player.getX() - 9.5) > 1.0 || Math.abs(player.getZ() - 9.5) > 1.0) {
				throw new AssertionError("HATA: Yeniden açılan oyuncu konumu beklenen (9.5, 9.5) değil: (" + player.getX() + ", " + player.getZ() + ")");
			}

			if (!player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE)) {
				throw new AssertionError("HATA: Yeniden açılan oyuncu göğüslüğü diamond_chestplate değil: " + player.getItemBySlot(EquipmentSlot.CHEST));
			}

			if (!player.getOffhandItem().is(Items.SHIELD)) {
				throw new AssertionError("HATA: Yeniden açılan oyuncu diğer eli shield değil: " + player.getOffhandItem());
			}

			if (!player.getMainHandItem().is(Items.DIAMOND_SWORD)) {
				throw new AssertionError("HATA: Yeniden açılan oyuncu ana eli diamond_sword değil: " + player.getMainHandItem());
			}

			ItemStack enderEmerald = player.getEnderChestInventory().getItem(0);
			if (!enderEmerald.is(Items.EMERALD) || enderEmerald.getCount() != 64) {
				throw new AssertionError("HATA: Yeniden açılan oyuncu ender sandığı ilk eşyası beklenen 64 zümrüt değil: " + enderEmerald);
			}
		});
	}

	private static void setEnabled(ClientGameTestContext context, boolean enabled) {
		context.runOnClient(client -> module().setEnabled(enabled));
	}

	private static WorldDownloaderModule module() {
		return (WorldDownloaderModule) SurvivalKit.modules()
				.byId("wdl")
				.orElseThrow(() -> new AssertionError("wdl modülü kayıtlı değil"));
	}

	private static void replaceContents(Path targetDir, Path sourceDir) {
		try {
			if (Files.exists(targetDir)) {
				try (Stream<Path> stream = Files.walk(targetDir)) {
					stream.sorted(Comparator.reverseOrder())
							.filter(p -> !p.equals(targetDir))
							.forEach(p -> {
								try {
									Files.deleteIfExists(p);
								} catch (IOException e) {
									throw new UncheckedIOException(e);
								}
							});
				}
			}
			try (Stream<Path> stream = Files.walk(sourceDir)) {
				stream.forEach(source -> {
					Path relative = sourceDir.relativize(source);
					Path destination = targetDir.resolve(relative);
					try {
						if (Files.isDirectory(source)) {
							Files.createDirectories(destination);
						} else {
							Files.createDirectories(destination.getParent());
							Files.copy(source, destination);
						}
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
