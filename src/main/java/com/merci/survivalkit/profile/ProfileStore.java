package com.merci.survivalkit.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The profiles on disk: one settings file each in the profiles folder, named after the profile,
 * and beside the folder a small file saying which one is in use. "default" is always there.
 */
public final class ProfileStore {
	public static final String DEFAULT = "default";
	/** Built in beside "default": every module off, whenever it is switched to. */
	public static final String SAFE = "safe";
	private static final String EXTENSION = ".json";
	private static final Logger LOGGER = LoggerFactory.getLogger(ProfileStore.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path folder;
	private final Path stateFile;

	/**
	 * @param folder    where the profiles' settings files are
	 * @param stateFile which profile is in use, and the rules that choose one
	 */
	public ProfileStore(Path folder, Path stateFile) {
		this.folder = folder;
		this.stateFile = stateFile;
	}

	public Path file(String name) {
		return folder.resolve(name + EXTENSION);
	}

	public boolean exists(String name) {
		return name.equals(DEFAULT) || name.equals(SAFE) || Files.isRegularFile(file(name));
	}

	/** Every profile, "default" and "safe" first and the rest by name. */
	public List<String> list() {
		List<String> names = new ArrayList<>();

		if (Files.isDirectory(folder)) {
			try (Stream<Path> files = Files.list(folder)) {
				files.map(path -> path.getFileName().toString())
					.filter(file -> file.endsWith(EXTENSION))
					.map(file -> file.substring(0, file.length() - EXTENSION.length()))
					.filter(name -> !name.equals(DEFAULT) && !name.equals(SAFE))
					.forEach(names::add);
			} catch (IOException e) {
				LOGGER.warn("Profil klasörü okunamadı: {}", folder, e);
			}
		}

		names.sort(Collator.getInstance(Locale.ROOT));
		names.addFirst(SAFE);
		names.addFirst(DEFAULT);
		return names;
	}

	/** A new profile with nothing in it: every setting at its default. */
	public void create(String name) {
		write(file(name), "{}");
	}

	/** Writes a profile's file as given: a built-in one. */
	public void write(String name, String json) {
		write(file(name), json);
	}

	public void copy(String from, String to) {
		try {
			Files.createDirectories(folder);

			if (Files.isRegularFile(file(from))) {
				Files.copy(file(from), file(to));
			} else {
				create(to);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void rename(String from, String to) {
		// Asked before the move: once the file has gone, the old name no longer counts as in use.
		boolean inUse = active().equals(from);

		try {
			Files.move(file(from), file(to));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		if (inUse) {
			setActive(to);
		}
	}

	public void delete(String name) {
		try {
			Files.deleteIfExists(file(name));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		if (state().has("active") && state().get("active").getAsString().equals(name)) {
			setActive(DEFAULT);
		}
	}

	/** The profile in use; "default" if none is named or the one named is gone. */
	public String active() {
		JsonObject state = state();
		String named = state.has("active") && state.get("active").isJsonPrimitive() ? state.get("active").getAsString() : DEFAULT;
		return exists(named) ? named : DEFAULT;
	}

	public void setActive(String name) {
		JsonObject state = state();
		state.addProperty("active", name);
		writeState(state);
	}

	/** The state file as it is, for whatever else is kept in it. */
	public JsonObject state() {
		if (!Files.isRegularFile(stateFile)) {
			return new JsonObject();
		}

		try {
			return JsonParser.parseString(Files.readString(stateFile)).getAsJsonObject();
		} catch (IOException | JsonParseException | IllegalStateException e) {
			LOGGER.warn("Profil durumu okunamadı, varsayılan profil kullanılıyor: {}", stateFile, e);
			return new JsonObject();
		}
	}

	public void writeState(JsonObject state) {
		write(stateFile, GSON.toJson(state));
	}

	private static void write(Path path, String text) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, text);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
