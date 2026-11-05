package com.merci.survivalkit.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes a settings tree as pretty JSON. A missing or corrupt file is not an
 * error: the tree simply keeps the values it already has.
 */
public final class SettingsStorage {
	private static final Logger LOGGER = LoggerFactory.getLogger(SettingsStorage.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private final Path legacyFile;
	private boolean inheritedFromLegacy;

	public SettingsStorage(Path file) {
		this(file, null);
	}

	/**
	 * @param legacyFile where an earlier version of the mod kept its settings, read once if
	 *                   {@code file} is not there yet. Null when there is nothing to inherit.
	 */
	public SettingsStorage(Path file, Path legacyFile) {
		this.file = Objects.requireNonNull(file, "file");
		this.legacyFile = legacyFile;
	}

	/** Where settings are written. Reads may come from the old location, writes never do. */
	public Path file() {
		return file;
	}

	public void save(SettingGroup root) {
		try {
			Path parent = file.getParent();

			if (parent != null) {
				Files.createDirectories(parent);
			}

			Files.writeString(file, GSON.toJson(root.write()));
			retireLegacyFile();
		} catch (IOException e) {
			LOGGER.error("Ayarlar kaydedilemedi: {}", file, e);
		}
	}

	/** Applies the file on top of whatever the tree holds now. */
	public void load(SettingGroup root) {
		Path source = source();

		if (source == null) {
			return;
		}

		if (source.equals(legacyFile)) {
			inheritedFromLegacy = true;
			LOGGER.info("Ayarlar eski konumdan okundu, bundan sonra buraya yazılacak: {}", file);
		}

		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(source));
			root.read(parsed);
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("Ayar dosyası okunamadı, varsayılanlar kullanılıyor: {}", source, e);
		}
	}

	/**
	 * Re-reads the file over a clean slate, so anything the file does not mention goes back
	 * to its default. That is what switching to another profile has to mean, and it is the
	 * difference between this and {@link #load}.
	 */
	public void reload(SettingGroup root) {
		root.reset();
		load(root);
	}

	/**
	 * Once the new file is safely written, the old one is renamed rather than left behind:
	 * otherwise deleting the profile to start fresh would quietly resurrect the old values.
	 */
	private void retireLegacyFile() {
		if (!inheritedFromLegacy) {
			return;
		}

		inheritedFromLegacy = false;
		Path retired = legacyFile.resolveSibling(legacyFile.getFileName() + ".migrated");

		try {
			Files.move(legacyFile, retired, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.info("Eski ayar dosyası artık kullanılmıyor, adı değişti: {}", retired);
		} catch (IOException e) {
			LOGGER.warn("Eski ayar dosyasının adı değiştirilemedi: {}", legacyFile, e);
		}
	}

	private Path source() {
		if (Files.isRegularFile(file)) {
			return file;
		}

		return legacyFile != null && Files.isRegularFile(legacyFile) ? legacyFile : null;
	}
}
