package com.merci.survivalkit.wdl;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerStateWriterTest {
	@TempDir
	Path tempDir;

	@Test
	void writeToFilesCreatesPlayerDataAndInjectsIntoLevelDat() throws IOException {
		String uuidStr = UUID.randomUUID().toString();

		// Create mock level.dat
		CompoundTag levelRoot = new CompoundTag();
		CompoundTag dataTag = new CompoundTag();
		dataTag.putString("LevelName", "TestWorld");
		levelRoot.put("Data", dataTag);
		levelRoot.putInt("DataVersion", 4500);
		Path levelDat = tempDir.resolve("level.dat");
		NbtIo.writeCompressed(levelRoot, levelDat);

		// Build sample player tag with all 9 required properties:
		// inventory, armor, offhand, pos, rotation, xp, health, hunger, ender chest
		CompoundTag playerTag = new CompoundTag();
		playerTag.putString("UUID", uuidStr);

		// 1. Pos
		ListTag posList = new ListTag();
		posList.add(DoubleTag.valueOf(10.5));
		posList.add(DoubleTag.valueOf(65.0));
		posList.add(DoubleTag.valueOf(-20.5));
		playerTag.put("Pos", posList);

		// 2. Rotation
		ListTag rotList = new ListTag();
		rotList.add(FloatTag.valueOf(45.0f));
		rotList.add(FloatTag.valueOf(15.0f));
		playerTag.put("Rotation", rotList);

		// 3. Health
		playerTag.putFloat("Health", 18.5f);

		// 4. Food
		playerTag.putInt("foodLevel", 19);
		playerTag.putFloat("foodSaturationLevel", 5.0f);
		playerTag.putFloat("foodExhaustionLevel", 0.5f);

		// 5. XP
		playerTag.putInt("XpLevel", 30);
		playerTag.putFloat("XpP", 0.75f);
		playerTag.putInt("XpTotal", 1395);

		// 6. Inventory (hotbar, armor, offhand)
		ListTag inventoryList = new ListTag();
		CompoundTag sword = new CompoundTag();
		sword.putByte("Slot", (byte) 0);
		sword.putString("id", "minecraft:diamond_sword");
		sword.putInt("count", 1);
		inventoryList.add(sword);

		CompoundTag chestplate = new CompoundTag();
		chestplate.putByte("Slot", (byte) 102);
		chestplate.putString("id", "minecraft:diamond_chestplate");
		chestplate.putInt("count", 1);
		inventoryList.add(chestplate);

		CompoundTag shield = new CompoundTag();
		shield.putByte("Slot", (byte) -106);
		shield.putString("id", "minecraft:shield");
		shield.putInt("count", 1);
		inventoryList.add(shield);
		playerTag.put("Inventory", inventoryList);

		// 7. Ender Chest items
		ListTag enderList = new ListTag();
		CompoundTag emerald = new CompoundTag();
		emerald.putByte("Slot", (byte) 0);
		emerald.putString("id", "minecraft:emerald");
		emerald.putInt("count", 64);
		enderList.add(emerald);
		playerTag.put("EnderItems", enderList);

		// Write to files
		PlayerStateWriter.writeToFiles(tempDir, uuidStr, playerTag);

		// Verify players/data/<UUID>.dat
		Path modernPlayerFile = tempDir.resolve("players").resolve("data").resolve(uuidStr + ".dat");
		assertTrue(Files.exists(modernPlayerFile), "players/data/<UUID>.dat dosyası oluşturulmalı");
		CompoundTag modernReadPlayer = NbtIo.readCompressed(modernPlayerFile, NbtAccounter.unlimitedHeap());
		assertEquals(18.5f, modernReadPlayer.getFloatOr("Health", 0.0f));
		assertEquals(19, modernReadPlayer.getIntOr("foodLevel", 0));
		assertEquals(30, modernReadPlayer.getIntOr("XpLevel", 0));
		assertEquals(3, modernReadPlayer.getListOrEmpty("Inventory").size());
		assertEquals(1, modernReadPlayer.getListOrEmpty("EnderItems").size());

		// Verify level.dat Data.Player
		CompoundTag readLevelDat = NbtIo.readCompressed(levelDat, NbtAccounter.unlimitedHeap());
		CompoundTag readData = readLevelDat.getCompoundOrEmpty("Data");
		assertTrue(readData.contains("Player"), "level.dat Data.Player içermeli");
		CompoundTag injectedPlayer = readData.getCompoundOrEmpty("Player");
		assertEquals(18.5f, injectedPlayer.getFloatOr("Health", 0.0f));
		assertEquals(19, injectedPlayer.getIntOr("foodLevel", 0));
		assertEquals(30, injectedPlayer.getIntOr("XpLevel", 0));
		assertEquals(3, injectedPlayer.getListOrEmpty("Inventory").size());
		assertEquals(1, injectedPlayer.getListOrEmpty("EnderItems").size());
	}

	@Test
	void writeToFilesSucceedsEvenIfLevelDatMissing() throws IOException {
		String uuidStr = UUID.randomUUID().toString();
		CompoundTag playerTag = new CompoundTag();
		playerTag.putFloat("Health", 20.0f);

		PlayerStateWriter.writeToFiles(tempDir, uuidStr, playerTag);

		Path playerFile = tempDir.resolve("players").resolve("data").resolve(uuidStr + ".dat");
		assertTrue(Files.exists(playerFile));
		CompoundTag read = NbtIo.readCompressed(playerFile, NbtAccounter.unlimitedHeap());
		assertEquals(20.0f, read.getFloatOr("Health", 0.0f));
	}

	@Test
	void legacyPlayerDirectoryIsNotCreated() throws IOException {
		String uuidStr = UUID.randomUUID().toString();
		CompoundTag playerTag = new CompoundTag();
		playerTag.putFloat("Health", 20.0f);

		PlayerStateWriter.writeToFiles(tempDir, uuidStr, playerTag);

		Path legacyPlayerDir = tempDir.resolve("playerdata");
		assertFalse(Files.exists(legacyPlayerDir), "Eski playerdata klasörü oluşturulmamalı");
		assertTrue(Files.exists(tempDir.resolve("players").resolve("data").resolve(uuidStr + ".dat")));
	}

	@Test
	void writeToFilesIgnoresNullInputs() {
		assertDoesNotThrow(() -> PlayerStateWriter.writeToFiles(null, "uuid", new CompoundTag()));
		assertDoesNotThrow(() -> PlayerStateWriter.writeToFiles(tempDir, null, new CompoundTag()));
		assertDoesNotThrow(() -> PlayerStateWriter.writeToFiles(tempDir, "uuid", null));
	}
}
