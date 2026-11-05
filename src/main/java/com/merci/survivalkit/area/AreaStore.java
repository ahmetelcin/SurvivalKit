package com.merci.survivalkit.area;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.merci.survivalkit.vein.Pos;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Every place's corners, in one file of their own beside the profiles: an area belongs to a
 * server and a dimension, not to a profile, so switching profiles does not lose it.
 *
 * <pre>{"mp:play.example.net": {"minecraft:overworld": {"first": [1, 64, 2], "second": [9, 70, 8]}}}</pre>
 *
 * <p>Read once, on first use; written whenever a corner changes, which is a click, not a tick.
 * A file that is missing, unreadable or half wrong costs only the parts that are wrong.
 */
public final class AreaStore {
	private final Path file;
	private final Consumer<String> log;
	private Map<Place, Corners> places;

	public AreaStore(Path file, Consumer<String> log) {
		this.file = Objects.requireNonNull(file, "file");
		this.log = Objects.requireNonNull(log, "log");
	}

	public Corners get(Place place) {
		return loaded().getOrDefault(place, Corners.NONE);
	}

	public void put(Place place, Corners corners) {
		if (corners.isEmpty()) {
			loaded().remove(place);
		} else {
			loaded().put(place, corners);
		}

		save();
	}

	private Map<Place, Corners> loaded() {
		if (places == null) {
			places = read();
		}

		return places;
	}

	private Map<Place, Corners> read() {
		Map<Place, Corners> read = new HashMap<>();

		if (!Files.isRegularFile(file)) {
			return read;
		}

		try {
			JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));

			if (!root.isJsonObject()) {
				return read;
			}

			for (Map.Entry<String, JsonElement> server : root.getAsJsonObject().entrySet()) {
				if (!server.getValue().isJsonObject()) {
					continue;
				}

				for (Map.Entry<String, JsonElement> dimension : server.getValue().getAsJsonObject().entrySet()) {
					if (!dimension.getValue().isJsonObject()) {
						continue;
					}

					JsonObject corners = dimension.getValue().getAsJsonObject();
					Corners found = new Corners(pos(corners.get("first")), pos(corners.get("second")));

					if (!found.isEmpty()) {
						read.put(new Place(server.getKey(), dimension.getKey()), found);
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			log.accept("Alan dosyası okunamadı (" + file + "): " + e);
		}

		return read;
	}

	private void save() {
		JsonObject root = new JsonObject();

		for (Map.Entry<Place, Corners> entry : places.entrySet()) {
			JsonObject server = root.has(entry.getKey().server())
				? root.getAsJsonObject(entry.getKey().server())
				: new JsonObject();
			root.add(entry.getKey().server(), server);

			JsonObject corners = new JsonObject();
			Corners value = entry.getValue();

			if (value.first() != null) {
				corners.add("first", json(value.first()));
			}

			if (value.second() != null) {
				corners.add("second", json(value.second()));
			}

			server.add(entry.getKey().dimension(), corners);
		}

		try {
			Files.createDirectories(file.getParent());
			Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			log.accept("Alan dosyası yazılamadı (" + file + "): " + e);
		}
	}

	private static JsonArray json(Pos pos) {
		JsonArray array = new JsonArray();
		array.add(pos.x());
		array.add(pos.y());
		array.add(pos.z());
		return array;
	}

	/** Three whole numbers, or nothing. */
	private static Pos pos(JsonElement json) {
		if (json == null || !json.isJsonArray() || json.getAsJsonArray().size() != 3) {
			return null;
		}

		JsonArray array = json.getAsJsonArray();

		for (JsonElement element : array) {
			if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
				return null;
			}
		}

		return new Pos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
	}
}
