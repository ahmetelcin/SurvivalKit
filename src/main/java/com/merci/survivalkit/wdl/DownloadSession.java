package com.merci.survivalkit.wdl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One download: the save folder, a writer per dimension, and the captures so far. */
public final class DownloadSession implements AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(DownloadSession.class);

	private final LevelStorageSource.LevelStorageAccess access;
	private final String levelId;
	private final String worldName;
	private final BlockEntityOverlay overlay = new BlockEntityOverlay();
	private final VillagerTradeOverlay tradeOverlay = new VillagerTradeOverlay();
	private final ClientChunkSerializer serializer = new ClientChunkSerializer(overlay);
	private final ClientEntitySerializer entitySerializer = new ClientEntitySerializer();
	private final Map<ResourceKey<Level>, DimensionWriter> writers = new HashMap<>();
	private final MapCapture maps = new MapCapture();
	/** The last look at the overworld's time, weather, spawn and difficulty. */
	private WorldInfo worldInfo;
	private final EntityInventoryOverlay inventoryOverlay = new EntityInventoryOverlay();
	/** The registries the maps are written with; learnt from the level, since a session has none of its own. */
	private net.minecraft.core.HolderLookup.Provider registries;

	private ResourceKey<Level> currentDimension;
	private long seed;
	private int savedChunks;
	/** Every chunk saved so far, by dimension and position. */
	private final Set<String> saved = new HashSet<>();

	private DownloadSession(LevelStorageSource.LevelStorageAccess access, String levelId, String worldName) {
		this.access = access;
		this.levelId = levelId;
		this.worldName = worldName;
	}

	/** Takes the session lock on a fresh save folder, so nothing else can open it meanwhile. */
	public static DownloadSession open(LevelStorageSource source, String levelId, String worldName) throws IOException {
		return new DownloadSession(source.createAccess(levelId), levelId, worldName);
	}

	public BlockEntityOverlay overlay() {
		return overlay;
	}

	public EntityInventoryOverlay inventoryOverlay() {
		return inventoryOverlay;
	}

	public MapCapture maps() {
		return maps;
	}

	/** Remembers the maps the player is holding or can see in a frame right now. */
	public synchronized void lookAtMaps(ClientLevel level, net.minecraft.world.entity.player.Player player) {
		registries = level.registryAccess();
		maps.look(level, player);
		noteWorldInfo(level);
	}

	/**
	 * Remembers the world's time, weather, spawn and difficulty. The overworld is the one that
	 * has them; from anywhere else it is only taken when nothing better has been seen yet.
	 */
	public synchronized void noteWorldInfo(ClientLevel level) {
		if (worldInfo == null || level.dimension().equals(Level.OVERWORLD)) {
			registries = level.registryAccess();
			worldInfo = WorldInfo.of(level);
		}
	}

	public synchronized WorldInfo worldInfo() {
		return worldInfo;
	}

	public VillagerTradeOverlay tradeOverlay() {
		return tradeOverlay;
	}

	/** Where the download is being written, for tooling and tests. */
	public Path levelPath() {
		return access.getLevelPath(LevelResource.ROOT).normalize();
	}

	public int savedChunks() {
		return savedChunks;
	}

	public long seed() {
		return seed;
	}

	private Optional<net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack>> enderChestItems = Optional.empty();

	public synchronized void setEnderChestItems(net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items) {
		this.enderChestItems = Optional.ofNullable(items);
	}

	public synchronized Optional<net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack>> enderChestItems() {
		return enderChestItems;
	}

	private boolean savePlayerState = true;
	private CompoundTag cachedPlayerTag;
	private String cachedPlayerUuid;

	public synchronized void setSavePlayerState(boolean savePlayerState) {
		this.savePlayerState = savePlayerState;
	}

	public synchronized boolean savePlayerState() {
		return savePlayerState;
	}

	public synchronized void savePlayer(net.minecraft.world.entity.player.Player player) {
		if (player == null || !savePlayerState) {
			return;
		}
		try {
			cachedPlayerTag = PlayerStateWriter.buildPlayerTag(player, enderChestItems);
			cachedPlayerUuid = player.getStringUUID();
			Path worldDir = access.getLevelPath(LevelResource.ROOT);
			PlayerStateWriter.writeToFiles(worldDir, cachedPlayerUuid, cachedPlayerTag);
		} catch (IOException e) {
			LOGGER.error("Oyuncu verileri kaydedilemedi: {}", levelId, e);
		}
	}

	private OwnershipMode ownershipMode = OwnershipMode.AS_IS;
	private java.util.UUID localPlayerUuid;

	/** Written into the world only when the session closes, so it can arrive late. */
	public void setSeed(long seed) {
		this.seed = seed;
	}

	public synchronized void setOwnership(OwnershipMode mode, java.util.UUID localPlayerUuid) {
		this.ownershipMode = mode != null ? mode : OwnershipMode.AS_IS;
		this.localPlayerUuid = localPlayerUuid;
	}

	public synchronized OwnershipMode ownershipMode() {
		return ownershipMode;
	}

	public synchronized java.util.UUID localPlayerUuid() {
		return localPlayerUuid;
	}

	/**
	 * Switches the active dimension.
	 *
	 * <p>The writer for the previous dimension is flushed and closed, ensuring region files
	 * are committed and file handles are released when moving between Overworld, Nether, or End.
	 */
	public synchronized void dimensionChanged(ResourceKey<Level> newDimension) {
		if (currentDimension != null && !currentDimension.equals(newDimension)) {
			DimensionWriter oldWriter = writers.remove(currentDimension);
			if (oldWriter != null) {
				oldWriter.flush();
				oldWriter.close();
				LOGGER.info("[wdl] Önceki boyut yazıcısı kapatıldı: {}", currentDimension.identifier());
			}
		}
		currentDimension = newDimension;
	}

	public synchronized ResourceKey<Level> currentDimension() {
		return currentDimension;
	}

	public synchronized java.util.Set<ResourceKey<Level>> openDimensions() {
		return java.util.Collections.unmodifiableSet(writers.keySet());
	}

	public synchronized void saveChunk(ClientLevel level, LevelChunk chunk) {
		if (currentDimension == null) {
			currentDimension = level.dimension();
		}
		ChunkPos pos = chunk.getPos();
		CompoundTag chunkTag = serializer.write(level, chunk);
		writerFor(level.dimension()).storeChunk(pos, chunkTag);

		CompoundTag entityTag = entitySerializer.serialize(level, pos, ownershipMode, localPlayerUuid, tradeOverlay, inventoryOverlay).orElse(null);
		writerFor(level.dimension()).storeEntities(pos, entityTag);

		// Distinct chunks, not saves: one seen again, or saved again when the download stops, is still one.
		if (saved.add(level.dimension().identifier() + "/" + pos.pack())) {
			savedChunks++;
		}
	}

	private DimensionWriter writerFor(ResourceKey<Level> dimension) {
		return writers.computeIfAbsent(dimension, key -> new DimensionWriter(levelId, key, access.getDimensionPath(key)));
	}

	/** Blocks until every chunk queued so far across open writers is on disk. */
	public synchronized void flush() {
		writers.values().forEach(DimensionWriter::flush);
	}

	@Override
	public synchronized void close() {
		writers.values().forEach(DimensionWriter::close);
		writers.clear();

		if (registries != null) {
			try {
				maps.write(access.getLevelPath(LevelResource.ROOT), registries);
			} catch (IOException e) {
				LOGGER.error("Haritalar yazılamadı: {}", levelId, e);
			}
		}

		try {
			LevelDataWriter.write(access, worldName, seed, worldInfo, registries);
		} catch (IOException e) {
			LOGGER.error("level.dat yazılamadı, dünya açılmayabilir: {}", levelId, e);
		}

		if (savePlayerState && cachedPlayerTag != null && cachedPlayerUuid != null) {
			try {
				PlayerStateWriter.writeToFiles(access.getLevelPath(LevelResource.ROOT), cachedPlayerUuid, cachedPlayerTag);
			} catch (IOException e) {
				LOGGER.error("Kapanışta oyuncu verileri level.dat'a yazılamadı: {}", levelId, e);
			}
		}

		try {
			access.close();
		} catch (IOException e) {
			LOGGER.error("Kayıt kilidi bırakılamadı: {}", levelId, e);
		}

		LOGGER.info("WDL kapandı: {} chunk kaydedildi -> {}", savedChunks, levelId);
	}
}
