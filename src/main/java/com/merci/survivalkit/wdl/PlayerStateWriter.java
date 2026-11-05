package com.merci.survivalkit.wdl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.FileUtil;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.TagValueOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saves the local player's state (inventory, armor, offhand, position, rotation,
 * health, hunger, XP, and opened ender chest) to disk.
 *
 * <p>Writes to both {@code playerdata/<UUID>.dat} and {@code level.dat} under {@code Data.Player},
 * ensuring that singleplayer worlds load the exact state of the player.
 */
public final class PlayerStateWriter {
	private static final Logger LOGGER = LoggerFactory.getLogger(PlayerStateWriter.class);

	private PlayerStateWriter() {
	}

	public static CompoundTag buildPlayerTag(Player player, Optional<NonNullList<ItemStack>> enderChestItems) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
		player.saveWithoutId(output);
		CompoundTag tag = output.buildResult();

		// If ender chest was opened and captured, ensure it is written to EnderItems
		if (enderChestItems != null && enderChestItems.isPresent()) {
			NonNullList<ItemStack> items = enderChestItems.get();
			List<ItemStackWithSlot> enderList = new ArrayList<>();
			for (int i = 0; i < items.size(); i++) {
				ItemStack stack = items.get(i);
				if (!stack.isEmpty()) {
					enderList.add(new ItemStackWithSlot(i, stack.copy()));
				}
			}
			tag.store("EnderItems", ItemStackWithSlot.CODEC.listOf(),
					player.registryAccess().createSerializationContext(NbtOps.INSTANCE), enderList);
		}

		if (player.level() != null) {
			tag.putString("Dimension", player.level().dimension().identifier().toString());
		}

		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc != null && mc.gameMode != null && mc.gameMode.getPlayerMode() != null) {
			tag.putInt("playerGameType", mc.gameMode.getPlayerMode().getId());
		}

		NbtUtils.addCurrentDataVersion(tag);
		return tag;
	}

	public static void writeToFiles(Path worldDir, String uuid, CompoundTag playerTag) throws IOException {
		if (worldDir == null || uuid == null || playerTag == null) {
			return;
		}

		// 1. Write players/data/<UUID>.dat (Minecraft 26.2 standard)
		Path playerDir = worldDir.resolve("players").resolve("data");
		FileUtil.createDirectoriesSafe(playerDir);
		Path playerFile = playerDir.resolve(uuid + ".dat");
		NbtIo.writeCompressed(playerTag, playerFile);

		// 2. Inject into level.dat under Data.Player and Data.singleplayer_uuid
		Path levelDatPath = worldDir.resolve("level.dat");
		if (Files.exists(levelDatPath)) {
			try {
				CompoundTag root = NbtIo.readCompressed(levelDatPath, NbtAccounter.unlimitedHeap());
				CompoundTag dataTag = root.getCompoundOrEmpty("Data");
				dataTag.put("Player", playerTag);
				try {
					UUID playerUuid = UUID.fromString(uuid);
					dataTag.store("singleplayer_uuid", UUIDUtil.CODEC, playerUuid);
				} catch (Exception e) {
					LOGGER.warn("singleplayer_uuid yazılamadı: {}", uuid, e);
				}
				root.put("Data", dataTag);
				NbtIo.writeCompressed(root, levelDatPath);
			} catch (Exception e) {
				LOGGER.warn("level.dat içine Data.Player / singleplayer_uuid yazılamadı: {}", levelDatPath, e);
			}
		}
	}

	public static void write(LevelStorageSource.LevelStorageAccess access, Player player, Optional<NonNullList<ItemStack>> enderChestItems) throws IOException {
		if (player == null || access == null) {
			return;
		}

		CompoundTag playerTag = buildPlayerTag(player, enderChestItems);
		Path worldDir = access.getLevelPath(LevelResource.ROOT);
		writeToFiles(worldDir, player.getStringUUID(), playerTag);

		LOGGER.info("[wdl] Oyuncu durumu kaydedildi: UUID={}, Pos=({}, {}, {}), Can={}, Açlık={}, XP={}",
				player.getStringUUID(), player.getX(), player.getY(), player.getZ(),
				player.getHealth(), player.getFoodData().getFoodLevel(), player.experienceLevel);
	}
}
