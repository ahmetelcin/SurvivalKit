package com.merci.survivalkit.wdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

/**
 * The server never sends chest contents, so whatever we scraped out of an open
 * container has to win over the near-empty block entity the server did send.
 */
class BlockEntityOverlayTest {
	private static final BlockPos CHEST = new BlockPos(10, 64, 20);

	private static CompoundTag tagWith(String key, String value) {
		CompoundTag tag = new CompoundTag();
		tag.putString(key, value);
		return tag;
	}

	@Test
	void anUntouchedPositionKeepsTheServerTagAsItIs() {
		BlockEntityOverlay overlay = new BlockEntityOverlay();
		CompoundTag fromServer = tagWith("id", "minecraft:chest");

		assertSame(fromServer, overlay.merge(CHEST, fromServer));
	}

	@Test
	void aCapturedKeyOverridesTheServerValue() {
		BlockEntityOverlay overlay = new BlockEntityOverlay();
		overlay.capture(CHEST, tagWith("Items", "yakalandı"));

		CompoundTag merged = overlay.merge(CHEST, tagWith("Items", "boş"));

		assertEquals("yakalandı", merged.getStringOr("Items", ""));
	}

	@Test
	void serverKeysWeDidNotCaptureSurvive() {
		BlockEntityOverlay overlay = new BlockEntityOverlay();
		overlay.capture(CHEST, tagWith("Items", "yakalandı"));

		CompoundTag merged = overlay.merge(CHEST, tagWith("id", "minecraft:chest"));

		assertEquals("minecraft:chest", merged.getStringOr("id", ""));
	}

	@Test
	void mergingDoesNotMutateTheServerTag() {
		BlockEntityOverlay overlay = new BlockEntityOverlay();
		overlay.capture(CHEST, tagWith("Items", "yakalandı"));
		CompoundTag fromServer = tagWith("id", "minecraft:chest");

		overlay.merge(CHEST, fromServer);

		assertTrue(fromServer.keySet().contains("id") && fromServer.keySet().size() == 1);
	}

	@Test
	void capturingTwiceKeepsTheNewerContents() {
		BlockEntityOverlay overlay = new BlockEntityOverlay();
		overlay.capture(CHEST, tagWith("Items", "eski"));
		overlay.capture(CHEST, tagWith("Items", "yeni"));

		assertEquals("yeni", overlay.merge(CHEST, new CompoundTag()).getStringOr("Items", ""));
	}

	@Test
	void aWrittenChunkReleasesItsCaptures() {
		BlockEntityOverlay overlay = new BlockEntityOverlay();
		overlay.capture(CHEST, tagWith("Items", "yakalandı"));

		overlay.forget(ChunkPos.containing(CHEST));

		assertEquals(0, overlay.size());
	}

	@Test
	void forgettingOneChunkLeavesTheOthersAlone() {
		BlockEntityOverlay overlay = new BlockEntityOverlay();
		overlay.capture(CHEST, tagWith("Items", "yakalandı"));
		BlockPos elsewhere = new BlockPos(500, 64, 500);
		overlay.capture(elsewhere, tagWith("Items", "uzakta"));

		overlay.forget(ChunkPos.containing(CHEST));

		assertEquals(1, overlay.size());
		assertEquals("uzakta", overlay.merge(elsewhere, new CompoundTag()).getStringOr("Items", ""));
	}

	@Test
	void capturesInDifferentDimensionsDoNotCollideAtTheSamePosition() {
		BlockEntityOverlay overlay = new BlockEntityOverlay();
		net.minecraft.resources.Identifier overworld = net.minecraft.resources.Identifier.parse("minecraft:overworld");
		net.minecraft.resources.Identifier nether = net.minecraft.resources.Identifier.parse("minecraft:the_nether");

		overlay.capture(overworld, CHEST, tagWith("Items", "overworld_elmas"));
		overlay.capture(nether, CHEST, tagWith("Items", "nether_altin"));

		assertEquals("overworld_elmas", overlay.merge(overworld, CHEST, new CompoundTag()).getStringOr("Items", ""));
		assertEquals("nether_altin", overlay.merge(nether, CHEST, new CompoundTag()).getStringOr("Items", ""));
	}

	@Test
	void forgettingInOneDimensionLeavesOtherDimensionsUntouched() {
		BlockEntityOverlay overlay = new BlockEntityOverlay();
		net.minecraft.resources.Identifier overworld = net.minecraft.resources.Identifier.parse("minecraft:overworld");
		net.minecraft.resources.Identifier nether = net.minecraft.resources.Identifier.parse("minecraft:the_nether");

		overlay.capture(overworld, CHEST, tagWith("Items", "overworld_elmas"));
		overlay.capture(nether, CHEST, tagWith("Items", "nether_altin"));

		overlay.forget(overworld, ChunkPos.containing(CHEST));

		assertTrue(overlay.get(overworld, CHEST).isEmpty());
		assertEquals("nether_altin", overlay.get(nether, CHEST).orElseThrow().getStringOr("Items", ""));
	}

	/** A later download over the same world needs to know this chest was seen inside. */
	@Test
	void aCapturedMergeIsMarkedForTheNextDownload() {
		BlockEntityOverlay overlay = new BlockEntityOverlay();
		overlay.capture(CHEST, tagWith("Items", "yakalandı"));

		assertTrue(overlay.merge(CHEST, new CompoundTag()).contains(ChunkContinuation.CAPTURED));
	}
}
