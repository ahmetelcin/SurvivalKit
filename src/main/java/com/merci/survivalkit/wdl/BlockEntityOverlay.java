package com.merci.survivalkit.wdl;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Container contents scraped from open screens, keyed by dimension and the block they belong to.
 *
 * <p>The client's own block entities are deliberately left alone: keeping the captures
 * on the side means nothing the game renders or ticks changes because we are recording.
 */
public final class BlockEntityOverlay {
	private static final net.minecraft.resources.Identifier DEFAULT_DIMENSION =
		net.minecraft.resources.Identifier.parse("minecraft:overworld");

	private final Map<net.minecraft.resources.Identifier, Map<BlockPos, CompoundTag>> dimensionCaptures = new HashMap<>();

	public void capture(net.minecraft.resources.Identifier dimension, BlockPos pos, CompoundTag contents) {
		dimensionCaptures.computeIfAbsent(dimension, k -> new HashMap<>()).put(pos.immutable(), contents);
	}

	public void capture(ResourceKey<Level> dimension, BlockPos pos, CompoundTag contents) {
		capture(dimension.identifier(), pos, contents);
	}

	public void capture(BlockPos pos, CompoundTag contents) {
		capture(DEFAULT_DIMENSION, pos, contents);
	}

	/** The server tag with anything we captured for that block layered on top. */
	public CompoundTag merge(net.minecraft.resources.Identifier dimension, BlockPos pos, CompoundTag fromServer) {
		Map<BlockPos, CompoundTag> captures = dimensionCaptures.get(dimension);
		if (captures == null) {
			return fromServer;
		}

		CompoundTag captured = captures.get(pos);
		if (captured == null) {
			return fromServer;
		}

		CompoundTag merged = fromServer.copy().merge(captured);
		// Tells a later write that this download saw inside it; see ChunkContinuation.
		merged.putBoolean(ChunkContinuation.CAPTURED, true);
		return merged;
	}

	public CompoundTag merge(ResourceKey<Level> dimension, BlockPos pos, CompoundTag fromServer) {
		return merge(dimension.identifier(), pos, fromServer);
	}

	public CompoundTag merge(BlockPos pos, CompoundTag fromServer) {
		return merge(DEFAULT_DIMENSION, pos, fromServer);
	}

	/** Called once a chunk has been written, so captures do not pile up for a long session. */
	public void forget(net.minecraft.resources.Identifier dimension, ChunkPos chunk) {
		Map<BlockPos, CompoundTag> captures = dimensionCaptures.get(dimension);
		if (captures != null) {
			captures.keySet().removeIf(pos -> ChunkPos.containing(pos).equals(chunk));
		}
	}

	public void forget(ResourceKey<Level> dimension, ChunkPos chunk) {
		forget(dimension.identifier(), chunk);
	}

	public void forget(ChunkPos chunk) {
		forget(DEFAULT_DIMENSION, chunk);
	}

	public Optional<CompoundTag> get(net.minecraft.resources.Identifier dimension, BlockPos pos) {
		Map<BlockPos, CompoundTag> captures = dimensionCaptures.get(dimension);
		return captures != null ? Optional.ofNullable(captures.get(pos)) : Optional.empty();
	}

	public Optional<CompoundTag> get(ResourceKey<Level> dimension, BlockPos pos) {
		return get(dimension.identifier(), pos);
	}

	public Optional<CompoundTag> get(BlockPos pos) {
		return get(DEFAULT_DIMENSION, pos);
	}

	public Set<BlockPos> positionsIn(net.minecraft.resources.Identifier dimension, ChunkPos chunk) {
		Map<BlockPos, CompoundTag> captures = dimensionCaptures.get(dimension);
		if (captures == null || captures.isEmpty()) {
			return Collections.emptySet();
		}

		Set<BlockPos> inChunk = new HashSet<>();
		for (BlockPos pos : captures.keySet()) {
			if (ChunkPos.containing(pos).equals(chunk)) {
				inChunk.add(pos);
			}
		}
		return Collections.unmodifiableSet(inChunk);
	}

	public Set<BlockPos> positionsIn(ResourceKey<Level> dimension, ChunkPos chunk) {
		return positionsIn(dimension.identifier(), chunk);
	}

	public Set<BlockPos> positionsIn(ChunkPos chunk) {
		return positionsIn(DEFAULT_DIMENSION, chunk);
	}

	public int size() {
		return dimensionCaptures.values().stream().mapToInt(Map::size).sum();
	}
}
