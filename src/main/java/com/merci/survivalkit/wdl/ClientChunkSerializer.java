package com.merci.survivalkit.wdl;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.Optionull;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.lighting.LevelLightEngine;

/**
 * Turns a chunk the client already has into the exact NBT the game writes for a
 * singleplayer world.
 *
 * <p>Vanilla's {@code SerializableChunkData.copyOf} wants a {@code ServerLevel}, which a
 * connected client never has. Every field it reads is available on {@link ClientLevel}
 * too, and the record's canonical constructor is public, so this rebuilds it by hand.
 * Keep it in step with {@code SerializableChunkData.copyOf} when Minecraft updates.
 *
 * <p>Two fields cannot come from a client: structure starts and references are never
 * sent, so they are written empty, and nothing is a proto-chunk here, so the carving
 * mask is always absent.
 */
public final class ClientChunkSerializer {
	private final BlockEntityOverlay overlay;

	public ClientChunkSerializer(BlockEntityOverlay overlay) {
		this.overlay = overlay;
	}

	public CompoundTag write(ClientLevel level, LevelChunk chunk) {
		return snapshot(level, chunk).write();
	}

	private SerializableChunkData snapshot(ClientLevel level, LevelChunk chunk) {
		ChunkPos pos = chunk.getPos();
		long gameTime = level.getGameTime();

		return new SerializableChunkData(
			level.palettedContainerFactory(),
			pos,
			chunk.getMinSectionY(),
			gameTime,
			chunk.getInhabitedTime(),
			chunk.getPersistedStatus(),
			Optionull.map(chunk.getBlendingData(), BlendingData::pack),
			chunk.getBelowZeroRetrogen(),
			chunk.getUpgradeData().copy(),
			heightmaps(chunk),
			chunk.getTicksForSerialization(gameTime),
			postProcessing(chunk),
			chunk.isLightCorrect(),
			sections(level, chunk),
			List.of(),
			blockEntities(level, chunk),
			emptyStructureData()
		);
	}

	private static List<SerializableChunkData.SectionData> sections(ClientLevel level, LevelChunk chunk) {
		LevelLightEngine light = level.getChunkSource().getLightEngine();
		LevelChunkSection[] chunkSections = chunk.getSections();
		List<SerializableChunkData.SectionData> sections = new ArrayList<>();
		ChunkPos pos = chunk.getPos();

		for (int sectionY = light.getMinLightSection(); sectionY < light.getMaxLightSection(); sectionY++) {
			int index = chunk.getSectionIndexFromSectionY(sectionY);
			boolean present = index >= 0 && index < chunkSections.length;
			DataLayer blockLight = copyIfUseful(light.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(pos, sectionY)));
			DataLayer skyLight = copyIfUseful(light.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(pos, sectionY)));

			if (present || blockLight != null || skyLight != null) {
				LevelChunkSection section = present ? chunkSections[index].copy() : null;
				sections.add(new SerializableChunkData.SectionData(sectionY, section, blockLight, skyLight));
			}
		}

		return sections;
	}

	private static DataLayer copyIfUseful(DataLayer layer) {
		return layer != null && !layer.isEmpty() ? layer.copy() : null;
	}

	/** Server-sent block entity data, with any container we scraped layered on top. */
	private List<CompoundTag> blockEntities(ClientLevel level, LevelChunk chunk) {
		List<CompoundTag> tags = new ArrayList<>();
		Set<BlockPos> positions = new LinkedHashSet<>(chunk.getBlockEntitiesPos());
		positions.addAll(overlay.positionsIn(level.dimension(), chunk.getPos()));

		for (BlockPos blockPos : positions) {
			CompoundTag fromServer = chunk.getBlockEntityNbtForSaving(blockPos, level.registryAccess());

			if (fromServer != null) {
				tags.add(overlay.merge(level.dimension(), blockPos, fromServer));
			} else {
				chunk.getBlockEntity(blockPos, LevelChunk.EntityCreationType.IMMEDIATE);
				fromServer = chunk.getBlockEntityNbtForSaving(blockPos, level.registryAccess());
				if (fromServer != null) {
					tags.add(overlay.merge(level.dimension(), blockPos, fromServer));
				}
			}
		}

		return tags;
	}

	private static Map<Heightmap.Types, long[]> heightmaps(LevelChunk chunk) {
		Map<Heightmap.Types, long[]> heightmaps = new EnumMap<>(Heightmap.Types.class);

		for (Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
			if (chunk.getPersistedStatus().heightmapsAfter().contains(entry.getKey())) {
				heightmaps.put(entry.getKey(), entry.getValue().getRawData().clone());
			}
		}

		return heightmaps;
	}

	private static ShortList[] postProcessing(LevelChunk chunk) {
		return Arrays.stream(chunk.getPostProcessing())
			.map(offsets -> offsets != null && !offsets.isEmpty() ? new ShortArrayList(offsets) : null)
			.toArray(ShortList[]::new);
	}

	/** The shape vanilla writes when a chunk touches no structure. */
	private static CompoundTag emptyStructureData() {
		CompoundTag structures = new CompoundTag();
		structures.put("starts", new CompoundTag());
		structures.put("References", new CompoundTag());
		return structures;
	}
}
