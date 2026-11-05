package com.merci.survivalkit.wdl;

import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.TagValueOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serializes entities in a given chunk from the client's live level into the
 * format expected by vanilla's {@code entities/*.mca} region storage.
 *
 * <p>Player entities (both local and remote) are strictly filtered out and never saved.
 * Passengers are skipped at top level since vanilla saves them recursively inside
 * their root vehicle's {@code Passengers} tag.
 */
public final class ClientEntitySerializer {
	private static final Logger LOGGER = LoggerFactory.getLogger(ClientEntitySerializer.class);

	public Optional<CompoundTag> serialize(ClientLevel level, ChunkPos chunkPos) {
		return serialize(level, chunkPos, OwnershipMode.AS_IS, null);
	}

	public Optional<CompoundTag> serialize(ClientLevel level, ChunkPos chunkPos, OwnershipMode ownershipMode, java.util.UUID localPlayerUuid) {
		return serialize(level, chunkPos, ownershipMode, localPlayerUuid, null);
	}

	public Optional<CompoundTag> serialize(ClientLevel level, ChunkPos chunkPos, OwnershipMode ownershipMode, java.util.UUID localPlayerUuid, VillagerTradeOverlay tradeOverlay) {
		return serialize(level, chunkPos, ownershipMode, localPlayerUuid, tradeOverlay, null);
	}

	public Optional<CompoundTag> serialize(ClientLevel level, ChunkPos chunkPos, OwnershipMode ownershipMode, java.util.UUID localPlayerUuid,
			VillagerTradeOverlay tradeOverlay, EntityInventoryOverlay inventoryOverlay) {
		ListTag entityList = new ListTag();

		for (Entity entity : level.entitiesForRendering()) {
			if (!shouldSave(entity, chunkPos)) {
				continue;
			}

			try {
				TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
				CompoundTag entityTag;
				if (entity.save(output)) {
					entityTag = output.buildResult();
				} else {
					output.putString("id", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
					entity.saveWithoutId(output);
					entityTag = output.buildResult();
				}

				if (ownershipMode == OwnershipMode.TRANSFER_TO_LOCAL && localPlayerUuid != null) {
					applyOwnershipTransfer(entity, entityTag, localPlayerUuid);
				}

				if (tradeOverlay != null) {
					tradeOverlay.apply(entity, entityTag);
				}

				if (inventoryOverlay != null) {
					inventoryOverlay.apply(entity, entityTag);
				}

				entityList.add(entityTag);
			} catch (Throwable t) {
				LOGGER.warn("[wdl] Varlık serileştirilemedi: {} ({})", entity, entity.position(), t);
			}
		}

		if (entityList.isEmpty()) {
			return Optional.empty();
		}

		CompoundTag root = new CompoundTag();
		NbtUtils.addCurrentDataVersion(root);
		root.store("Position", ChunkPos.CODEC, chunkPos);
		root.put("Entities", entityList);
		return Optional.of(root);
	}

	public static void applyOwnershipTransfer(Entity entity, CompoundTag tag, java.util.UUID localPlayerUuid) {
		if (localPlayerUuid == null || tag == null) {
			return;
		}

		boolean isOwnable = tag.contains("Owner");

		if (entity instanceof net.minecraft.world.entity.OwnableEntity ownable && ownable.getOwnerReference() != null) {
			isOwnable = true;
		}
		if (entity instanceof net.minecraft.world.entity.TamableAnimal tamable && tamable.isTame()) {
			isOwnable = true;
		}
		if (entity instanceof net.minecraft.world.entity.animal.equine.AbstractHorse horse && horse.isTamed()) {
			isOwnable = true;
			tag.putBoolean("Tame", true);
		}

		if (isOwnable) {
			tag.store("Owner", net.minecraft.core.UUIDUtil.CODEC, localPlayerUuid);
		}
	}

	public static boolean shouldSave(Entity entity, ChunkPos chunkPos) {
		if (entity == null || entity.isRemoved()) {
			return false;
		}
		// Players (self and remote) must NEVER be saved
		if (entity instanceof Player) {
			return false;
		}
		// Passengers are saved recursively inside their vehicle
		if (entity.isPassenger()) {
			return false;
		}
		return entity.chunkPosition().equals(chunkPos);
	}
}
