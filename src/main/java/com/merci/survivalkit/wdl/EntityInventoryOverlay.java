package com.merci.survivalkit.wdl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.Entity;

/**
 * What was inside an animal's saddle bags or a cart, captured while its screen was open, kept
 * until the entity is written.
 *
 * <p>The client's own copy of the entity cannot hold it: an animal's bags are sized before the
 * client learns it carries a chest. So the contents are laid over the saved entity instead,
 * under {@code Items}, in the same shape the game writes for both: a list of stacks, each with
 * its slot number in the entity's container.
 */
public final class EntityInventoryOverlay {
	private final Map<UUID, List<ItemStackWithSlot>> captures = new HashMap<>();

	public synchronized void capture(UUID entity, List<ItemStackWithSlot> items) {
		captures.put(entity, List.copyOf(items));
	}

	public synchronized int size() {
		return captures.size();
	}

	/** Puts the captured contents on the entity's saved tag; leaves it alone when there are none. */
	public synchronized void apply(Entity entity, CompoundTag entityTag) {
		List<ItemStackWithSlot> items = captures.get(entity.getUUID());

		if (items == null) {
			return;
		}

		entityTag.store("Items", ItemStackWithSlot.CODEC.listOf(),
				entity.registryAccess().createSerializationContext(NbtOps.INSTANCE), items);
	}
}
