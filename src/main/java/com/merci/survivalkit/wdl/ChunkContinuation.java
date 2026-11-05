package com.merci.survivalkit.wdl;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Folds what an earlier download knew about a chunk into the fresh copy being written over it.
 *
 * <p>Continuing the same world means a chunk seen again is written again, from what the
 * server sends now — and the server never sends what is inside a chest. A chest opened in an
 * earlier download and only walked past this time would come out empty. So a container this
 * download did not open itself keeps the contents the earlier one saw, as long as the same
 * kind of block is still standing on the same spot.
 *
 * <p>A container this download did open is marked {@link #CAPTURED} by the overlay, and wins
 * even when it is empty now: someone emptied it, and the fresh look is the true one. The mark
 * is only a note between the two steps and never reaches the disk.
 */
public final class ChunkContinuation {
	/** Put on a block entity whose contents were captured in this download. */
	public static final String CAPTURED = "survivalkit:captured";

	private static final String BLOCK_ENTITIES = "block_entities";
	private static final String ITEMS = "Items";

	private ChunkContinuation() {
	}

	/** @param earlier the chunk as the earlier download wrote it, or null when there was none */
	public static CompoundTag carryOver(CompoundTag fresh, CompoundTag earlier) {
		CompoundTag written = fresh.copy();
		Map<String, CompoundTag> before = earlier == null ? Map.of() : byPosition(earlier.getListOrEmpty(BLOCK_ENTITIES));
		ListTag blockEntities = written.getListOrEmpty(BLOCK_ENTITIES);

		for (int index = 0; index < blockEntities.size(); index++) {
			CompoundTag blockEntity = blockEntities.getCompoundOrEmpty(index);

			if (blockEntity.contains(CAPTURED)) {
				blockEntity.remove(CAPTURED);
				continue;
			}

			CompoundTag old = before.get(key(blockEntity));

			if (old != null && sameKind(old, blockEntity) && blockEntity.getListOrEmpty(ITEMS).isEmpty()
					&& !old.getListOrEmpty(ITEMS).isEmpty()) {
				blockEntity.put(ITEMS, old.getListOrEmpty(ITEMS).copy());
			}
		}

		return written;
	}

	private static Map<String, CompoundTag> byPosition(ListTag blockEntities) {
		Map<String, CompoundTag> found = new HashMap<>();

		for (int index = 0; index < blockEntities.size(); index++) {
			CompoundTag blockEntity = blockEntities.getCompoundOrEmpty(index);
			found.put(key(blockEntity), blockEntity);
		}

		return found;
	}

	private static String key(CompoundTag blockEntity) {
		return blockEntity.getIntOr("x", 0) + "," + blockEntity.getIntOr("y", 0) + "," + blockEntity.getIntOr("z", 0);
	}

	private static boolean sameKind(CompoundTag a, CompoundTag b) {
		return a.getStringOr("id", "").equals(b.getStringOr("id", ""));
	}
}
