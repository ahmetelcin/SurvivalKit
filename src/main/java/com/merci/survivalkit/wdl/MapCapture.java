package com.merci.survivalkit.wdl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapIndex;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.SavedDataStorage;

/**
 * The maps the player has had in view — held, or hanging in a frame nearby — kept so they can
 * be written into the downloaded world's {@code data/} folder.
 *
 * <p>The client only ever has the picture of a map, never where it was drawn: its centre is not
 * sent. Written as it is, the first time the map was held in the downloaded world the game would
 * draw over it as if it were centred on 0, 0. So every map is written locked, the way a
 * cartography table locks one: the picture stays exactly as it was downloaded.
 *
 * <p>The files are written through the game's own saved data storage, so they land where and
 * how the game reads them: {@code data/minecraft/maps/<id>.dat}, and the id counter in
 * {@code maps/last_id} moved past every downloaded id so a new map never overwrites one.
 */
public final class MapCapture {
	private final Map<MapId, MapItemSavedData> seen = new HashMap<>();

	/** Picks up every map the player is holding or can see in a frame. The newest picture wins. */
	public synchronized void look(ClientLevel level, Player player) {
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			remember(level, stack);
		}

		remember(level, player.getOffhandItem());

		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof ItemFrame frame) {
				remember(level, frame.getItem());
			}
		}
	}

	public synchronized int count() {
		return seen.size();
	}

	private void remember(ClientLevel level, ItemStack stack) {
		MapId id = stack.get(DataComponents.MAP_ID);

		if (id == null) {
			return;
		}

		MapItemSavedData data = level.getMapData(id);

		if (data != null) {
			seen.put(id, data.locked());
		}
	}

	/** Writes every map seen into the world folder. Nothing is written when none was seen. */
	public synchronized void write(Path worldFolder, HolderLookup.Provider registries) throws IOException {
		if (seen.isEmpty()) {
			return;
		}

		Path dataFolder = worldFolder.resolve("data");
		int last = Math.max(existingLastId(dataFolder), seen.keySet().stream().mapToInt(MapId::id).max().orElse(-1));

		try (SavedDataStorage storage = new SavedDataStorage(dataFolder, DataFixers.getDataFixer(), registries)) {
			seen.forEach((id, data) -> {
				data.setDirty();
				storage.set(MapItemSavedData.type(id), data);
			});

			MapIndex index = new MapIndex(last);
			index.setDirty();
			storage.set(MapIndex.TYPE, index);
			storage.saveAndJoin();
		}
	}

	/** The counter an earlier download into this folder left, so continuing never lowers it. */
	private static int existingLastId(Path dataFolder) {
		Path file = dataFolder.resolve("minecraft").resolve("maps").resolve("last_id.dat");

		if (!Files.exists(file)) {
			return -1;
		}

		try {
			CompoundTag data = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompoundOrEmpty("data");
			return data.getIntOr("map", -1);
		} catch (IOException unreadable) {
			return -1;
		}
	}
}
