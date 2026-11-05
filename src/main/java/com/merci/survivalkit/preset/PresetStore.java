package com.merci.survivalkit.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The player's own presets, in one file beside the profiles: by module, then by name. They are
 * the player's, not a profile's, so every profile sees the same ones. Read once and kept; every
 * change is written straight away.
 */
public final class PresetStore {
	private static final Logger LOGGER = LoggerFactory.getLogger(PresetStore.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private JsonObject all;

	public PresetStore(Path file) {
		this.file = file;
	}

	public Path file() {
		return file;
	}

	/** A module's own presets, by name. */
	public List<Preset> list(String moduleId) {
		List<Preset> presets = new ArrayList<>();

		for (Map.Entry<String, JsonElement> entry : module(moduleId).entrySet()) {
			if (entry.getValue().isJsonObject()) {
				presets.add(new Preset(entry.getKey(), entry.getKey(), false, entry.getValue().getAsJsonObject().deepCopy()));
			}
		}

		Collator collator = Collator.getInstance(Locale.ROOT);
		presets.sort((a, b) -> collator.compare(a.name(), b.name()));
		return presets;
	}

	public void save(String moduleId, String name, JsonObject values) {
		JsonObject presets = module(moduleId);
		presets.add(name, values.deepCopy());
		all().add(moduleId, presets);
		write();
	}

	public void delete(String moduleId, String name) {
		JsonObject presets = module(moduleId);

		if (presets.remove(name) != null) {
			all().add(moduleId, presets);
			write();
		}
	}

	private JsonObject module(String moduleId) {
		JsonElement presets = all().get(moduleId);
		return presets != null && presets.isJsonObject() ? presets.getAsJsonObject() : new JsonObject();
	}

	private JsonObject all() {
		if (all == null) {
			all = read();
		}

		return all;
	}

	private JsonObject read() {
		if (!Files.isRegularFile(file)) {
			return new JsonObject();
		}

		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(file));
			return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("Hazır ayar dosyası okunamadı, kendi hazır ayarın yok sayıldı: {}", file, e);
			return new JsonObject();
		}
	}

	private void write() {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(all));
		} catch (IOException e) {
			LOGGER.error("Hazır ayarlar kaydedilemedi: {}", file, e);
		}
	}
}
