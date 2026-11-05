package com.merci.survivalkit.wdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

/**
 * A chunk seen again in a later download is written fresh — but the server never sends what
 * is inside a chest, so a chest opened in an earlier download and only walked past this time
 * would come out empty. What the earlier download knew is carried over, unless this download
 * opened that chest itself.
 */
class ChunkContinuationTest {
	private static CompoundTag chunk(CompoundTag... blockEntities) {
		ListTag list = new ListTag();

		for (CompoundTag blockEntity : blockEntities) {
			list.add(blockEntity);
		}

		CompoundTag chunk = new CompoundTag();
		chunk.put("block_entities", list);
		return chunk;
	}

	private static CompoundTag chest(int x, String id, String items) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		tag.putInt("x", x);
		tag.putInt("y", 64);
		tag.putInt("z", 5);

		if (items != null) {
			ListTag list = new ListTag();
			list.add(StringTag.valueOf(items));
			tag.put("Items", list);
		}

		return tag;
	}

	private static CompoundTag captured(CompoundTag tag) {
		tag.putBoolean(ChunkContinuation.CAPTURED, true);
		return tag;
	}

	private static String items(CompoundTag chunk, int index) {
		ListTag items = chunk.getListOrEmpty("block_entities").getCompoundOrEmpty(index).getListOrEmpty("Items");
		return items.isEmpty() ? "" : items.get(0).asString().orElse("");
	}

	@Test
	void aChestOnlyWalkedPastKeepsWhatTheEarlierDownloadSawInIt() {
		CompoundTag fresh = chunk(chest(1, "minecraft:chest", null));
		CompoundTag earlier = chunk(chest(1, "minecraft:chest", "64 elmas"));

		assertEquals("64 elmas", items(ChunkContinuation.carryOver(fresh, earlier), 0));
	}

	@Test
	void aChestOpenedThisTimeWinsEvenWhenItIsNowEmpty() {
		CompoundTag emptied = chest(1, "minecraft:chest", null);
		emptied.put("Items", new ListTag());
		CompoundTag fresh = chunk(captured(emptied));
		CompoundTag earlier = chunk(chest(1, "minecraft:chest", "64 elmas"));

		assertEquals("", items(ChunkContinuation.carryOver(fresh, earlier), 0));
	}

	@Test
	void aDifferentBlockAtThatSpotTakesNothingOver() {
		CompoundTag fresh = chunk(chest(1, "minecraft:barrel", null));
		CompoundTag earlier = chunk(chest(1, "minecraft:chest", "64 elmas"));

		assertEquals("", items(ChunkContinuation.carryOver(fresh, earlier), 0));
	}

	@Test
	void aChestSomewhereElseTakesNothingOver() {
		CompoundTag fresh = chunk(chest(2, "minecraft:chest", null));
		CompoundTag earlier = chunk(chest(1, "minecraft:chest", "64 elmas"));

		assertEquals("", items(ChunkContinuation.carryOver(fresh, earlier), 0));
	}

	@Test
	void theMarkIsNeverWrittenToDisk() {
		CompoundTag fresh = chunk(captured(chest(1, "minecraft:chest", "yeni")));

		CompoundTag written = ChunkContinuation.carryOver(fresh, null);

		assertFalse(written.getListOrEmpty("block_entities").getCompoundOrEmpty(0).contains(ChunkContinuation.CAPTURED));
		assertEquals("yeni", items(written, 0));
	}

	@Test
	void aChunkNeverSeenBeforeIsWrittenAsItIs() {
		CompoundTag fresh = chunk(chest(1, "minecraft:chest", null));

		CompoundTag written = ChunkContinuation.carryOver(fresh, null);

		assertEquals(1, written.getListOrEmpty("block_entities").size());
		assertEquals("", items(written, 0));
	}

	@Test
	void theFreshTagIsNotChangedInPlace() {
		CompoundTag fresh = chunk(captured(chest(1, "minecraft:chest", null)));

		ChunkContinuation.carryOver(fresh, chunk(chest(1, "minecraft:chest", "64 elmas")));

		assertTrue(fresh.getListOrEmpty("block_entities").getCompoundOrEmpty(0).contains(ChunkContinuation.CAPTURED));
	}
}
