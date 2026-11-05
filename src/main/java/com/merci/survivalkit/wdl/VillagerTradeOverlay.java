package com.merci.survivalkit.wdl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * In-memory overlay of villager trades captured when the player opened trading screens.
 *
 * <p>During world download, entities are serialized by the client where Minecraft omits
 * trades because {@code level.isClientSide} is true. This overlay bridges the captured
 * trades, level, and XP back into the entity tags before writing them to disk.
 */
public final class VillagerTradeOverlay {
	private final Map<UUID, VillagerTradeData> captures = new HashMap<>();

	public void capture(UUID uuid, VillagerTradeData data) {
		if (uuid != null && data != null) {
			captures.put(uuid, data);
		}
	}

	public boolean has(UUID uuid) {
		return uuid != null && captures.containsKey(uuid);
	}

	public Optional<VillagerTradeData> get(UUID uuid) {
		return Optional.ofNullable(captures.get(uuid));
	}

	public int size() {
		return captures.size();
	}

	public void clear() {
		captures.clear();
	}

	public Map<UUID, VillagerTradeData> all() {
		return Collections.unmodifiableMap(captures);
	}

	/**
	 * Applies captured trade offers, XP, and profession level to an entity's CompoundTag.
	 */
	public void apply(UUID uuid, CompoundTag entityTag, RegistryAccess registryAccess) {
		if (uuid == null || entityTag == null) {
			return;
		}
		VillagerTradeData data = captures.get(uuid);
		if (data == null || data.offers() == null || data.offers().isEmpty()) {
			return;
		}

		if (registryAccess != null) {
			entityTag.store("Offers", MerchantOffers.CODEC, registryAccess.createSerializationContext(NbtOps.INSTANCE), data.offers());
		} else {
			entityTag.store("Offers", MerchantOffers.CODEC, data.offers());
		}

		if (data.xp() > 0) {
			entityTag.putInt("Xp", data.xp());
		}

		if (data.level() > 0 && entityTag.contains("VillagerData")) {
			CompoundTag vdTag = entityTag.getCompoundOrEmpty("VillagerData");
			vdTag.putInt("level", data.level());
			entityTag.put("VillagerData", vdTag);
		}
	}

	public void apply(UUID uuid, CompoundTag entityTag) {
		apply(uuid, entityTag, null);
	}

	public void apply(Entity entity, CompoundTag entityTag) {
		if (entity != null) {
			apply(entity.getUUID(), entityTag, entity.registryAccess());
		}
	}
}
