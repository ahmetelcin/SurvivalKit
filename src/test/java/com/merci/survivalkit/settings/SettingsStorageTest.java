package com.merci.survivalkit.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** File IO for the settings tree. Uses a temp dir, so it stays a plain unit test. */
class SettingsStorageTest {
	@TempDir
	Path dir;

	private static SettingGroup root() {
		SettingGroup root = new SettingGroup("survivalkit", "test.survivalkit");
		root.add(new BoolSetting("entities", "test.entities", true));
		root.add(new TextSetting("seed", "test.seed", "", 64));
		return root;
	}

	@Test
	void savingThenLoadingRoundTripsTheValues() throws IOException {
		Path file = dir.resolve("settings.json");
		SettingGroup saved = root();
		saved.<BoolSetting>byId("entities").orElseThrow().set(false);
		saved.<TextSetting>byId("seed").orElseThrow().set("12345");
		new SettingsStorage(file).save(saved);

		SettingGroup loaded = root();
		new SettingsStorage(file).load(loaded);

		assertFalse(loaded.<BoolSetting>byId("entities").orElseThrow().get());
		assertEquals("12345", loaded.<TextSetting>byId("seed").orElseThrow().get());
	}

	@Test
	void loadingAMissingFileLeavesTheDefaults() {
		SettingGroup group = root();

		new SettingsStorage(dir.resolve("yok.json")).load(group);

		assertTrue(group.<BoolSetting>byId("entities").orElseThrow().get());
	}

	@Test
	void loadingCorruptJsonLeavesTheDefaultsInsteadOfThrowing() throws IOException {
		Path file = dir.resolve("settings.json");
		Files.writeString(file, "{ bu json değil");
		SettingGroup group = root();

		new SettingsStorage(file).load(group);

		assertTrue(group.<BoolSetting>byId("entities").orElseThrow().get());
	}

	@Test
	void savingCreatesMissingParentDirectories() throws IOException {
		Path file = dir.resolve("nested/deeper/settings.json");

		new SettingsStorage(file).save(root());

		assertTrue(Files.exists(file));
	}

	@Test
	void theSavedFileIsReadableJson() throws IOException {
		Path file = dir.resolve("settings.json");

		new SettingsStorage(file).save(root());

		assertTrue(Files.readString(file).contains("\"entities\""));
	}

	@Test
	void reloadTakesWhatIsOnDiskNow() throws IOException {
		Path file = dir.resolve("settings.json");
		Files.writeString(file, "{\"seed\": \"diskten\"}");
		SettingGroup group = root();
		group.<TextSetting>byId("seed").orElseThrow().set("hafızadan");

		new SettingsStorage(file).reload(group);

		assertEquals("diskten", group.<TextSetting>byId("seed").orElseThrow().get());
	}

	@Test
	void reloadPutsBackWhatTheFileDoesNotMention() throws IOException {
		Path file = dir.resolve("settings.json");
		Files.writeString(file, "{\"seed\": \"diskten\"}");
		SettingGroup group = root();
		group.<BoolSetting>byId("entities").orElseThrow().set(false);

		new SettingsStorage(file).reload(group);

		assertTrue(group.<BoolSetting>byId("entities").orElseThrow().get(), "dosyada yoksa varsayılana dönmeli");
	}

	@Test
	void loadLeavesAloneWhatTheFileDoesNotMention() throws IOException {
		Path file = dir.resolve("settings.json");
		Files.writeString(file, "{\"seed\": \"diskten\"}");
		SettingGroup group = root();
		group.<BoolSetting>byId("entities").orElseThrow().set(false);

		new SettingsStorage(file).load(group);

		assertFalse(group.<BoolSetting>byId("entities").orElseThrow().get(), "load sıfırlamamalı");
	}

	@Test
	void theOldLocationIsReadWhenTheNewFileIsNotThereYet() throws IOException {
		Path oldFile = dir.resolve("survivalkit.json");
		Path newFile = dir.resolve("survivalkit/profiles/default.json");
		Files.writeString(oldFile, "{\"seed\": \"eski\"}");
		SettingGroup group = root();

		new SettingsStorage(newFile, oldFile).load(group);

		assertEquals("eski", group.<TextSetting>byId("seed").orElseThrow().get());
	}

	@Test
	void theNewFileWinsWhenBothExist() throws IOException {
		Path oldFile = dir.resolve("survivalkit.json");
		Path newFile = dir.resolve("survivalkit/profiles/default.json");
		Files.createDirectories(newFile.getParent());
		Files.writeString(oldFile, "{\"seed\": \"eski\"}");
		Files.writeString(newFile, "{\"seed\": \"yeni\"}");
		SettingGroup group = root();

		new SettingsStorage(newFile, oldFile).load(group);

		assertEquals("yeni", group.<TextSetting>byId("seed").orElseThrow().get());
	}

	@Test
	void savingAlwaysGoesToTheNewLocation() throws IOException {
		Path oldFile = dir.resolve("survivalkit.json");
		Path newFile = dir.resolve("survivalkit/profiles/default.json");
		Files.writeString(oldFile, "{\"seed\": \"eski\"}");
		SettingsStorage storage = new SettingsStorage(newFile, oldFile);
		SettingGroup group = root();

		storage.load(group);
		storage.save(group);

		assertTrue(Files.exists(newFile));
		assertEquals("eski", group.<TextSetting>byId("seed").orElseThrow().get());
	}

	@Test
	void theOldFileIsRenamedOnceTheNewOneIsWritten() throws IOException {
		Path oldFile = dir.resolve("survivalkit.json");
		Path newFile = dir.resolve("survivalkit/profiles/default.json");
		Files.writeString(oldFile, "{\"seed\": \"eski\"}");
		SettingsStorage storage = new SettingsStorage(newFile, oldFile);
		SettingGroup group = root();

		storage.load(group);
		storage.save(group);

		assertFalse(Files.exists(oldFile), "eski dosya eski adıyla kalmamalı");
		assertEquals("{\"seed\": \"eski\"}", Files.readString(dir.resolve("survivalkit.json.migrated")));
	}

	@Test
	void theOldFileStaysPutUntilTheNewOneExists() throws IOException {
		Path oldFile = dir.resolve("survivalkit.json");
		Path newFile = dir.resolve("survivalkit/profiles/default.json");
		Files.writeString(oldFile, "{\"seed\": \"eski\"}");

		new SettingsStorage(newFile, oldFile).load(root());

		assertTrue(Files.exists(oldFile), "kaydetmeden önce adı değişmemeli");
	}

	@Test
	void deletingTheProfileAfterAMigrationDoesNotBringTheOldValuesBack() throws IOException {
		Path oldFile = dir.resolve("survivalkit.json");
		Path newFile = dir.resolve("survivalkit/profiles/default.json");
		Files.writeString(oldFile, "{\"seed\": \"eski\"}");
		SettingsStorage storage = new SettingsStorage(newFile, oldFile);
		SettingGroup group = root();
		storage.load(group);
		storage.save(group);

		// The user deletes the profile to start over.
		Files.delete(newFile);
		SettingGroup fresh = root();
		new SettingsStorage(newFile, oldFile).load(fresh);

		assertEquals("", fresh.<TextSetting>byId("seed").orElseThrow().get(), "eski değerler dirilmemeli");
	}

	@Test
	void anUntouchedOldFileIsLeftAloneWhenTheNewOneAlreadyExists() throws IOException {
		Path oldFile = dir.resolve("survivalkit.json");
		Path newFile = dir.resolve("survivalkit/profiles/default.json");
		Files.createDirectories(newFile.getParent());
		Files.writeString(oldFile, "{\"seed\": \"eski\"}");
		Files.writeString(newFile, "{\"seed\": \"yeni\"}");
		SettingsStorage storage = new SettingsStorage(newFile, oldFile);
		SettingGroup group = root();

		storage.load(group);
		storage.save(group);

		assertTrue(Files.exists(oldFile), "okunmayan eski dosyaya dokunulmamalı");
	}
}
