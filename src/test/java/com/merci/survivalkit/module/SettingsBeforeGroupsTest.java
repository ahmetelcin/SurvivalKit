package com.merci.survivalkit.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.merci.survivalkit.module.impl.AutoEatModule;
import com.merci.survivalkit.module.impl.AutoTotemModule;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import com.merci.survivalkit.settings.Setting;
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.settings.SettingNode;
import com.merci.survivalkit.settings.SettingsStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A profile written before the settings were put under headings has to load unchanged.
 *
 * <p>The file is a copy of the layout 0.2.1 wrote, with every value moved off its default. A
 * key that the headings had moved would not be found, its setting would stay at the default,
 * and both checks below would say which one.
 */
class SettingsBeforeGroupsTest {
	private static final String FIXTURE = "/profiles/before-groups.json";

	@TempDir
	Path dir;

	private static ModuleManager realModules() {
		ModuleManager modules = new ModuleManager();
		modules.register(new AutoEatModule());
		modules.register(new AutoTotemModule());
		modules.register(new WorldDownloaderModule());
		return modules;
	}

	@Test
	void everySettingTakesItsValueFromTheOldFile() throws IOException {
		ModuleManager modules = realModules();

		new SettingsStorage(copyFixture()).load(modules.settings());

		List<String> stillDefault = new ArrayList<>();
		collectDefaults(modules.settings(), modules.settings().id(), stillDefault);
		assertEquals(List.of(), stillDefault, "eski dosyadan okunamayıp varsayılanda kalan ayarlar");
	}

	/** Written back, the file keeps the old keys and nothing else: no heading becomes a level. */
	@Test
	void savingWritesTheSameKeysTheOldFileHad() throws IOException {
		ModuleManager modules = realModules();
		Path file = copyFixture();
		SettingsStorage storage = new SettingsStorage(file);
		JsonElement before = JsonParser.parseString(Files.readString(file));

		storage.load(modules.settings());
		storage.save(modules.settings());

		assertEquals(before, JsonParser.parseString(Files.readString(file)));
	}

	@Test
	void theRealModulesDoHaveHeadings() {
		boolean anySection = realModules().modules().stream()
			.flatMap(module -> module.settings().children().stream())
			.anyMatch(node -> node instanceof SettingGroup group && group.isSection());

		assertTrue(anySection, "bu test başlıklı modüller için yazıldı; hiç başlık yoksa bir şey kanıtlamıyor");
	}

	private Path copyFixture() throws IOException {
		Path file = dir.resolve("default.json");

		try (InputStream stream = SettingsBeforeGroupsTest.class.getResourceAsStream(FIXTURE)) {
			if (stream == null) {
				throw new AssertionError("fixture yok: " + FIXTURE);
			}

			Files.copy(stream, file);
		}

		return file;
	}

	private static void collectDefaults(SettingGroup group, String path, List<String> found) {
		for (SettingNode child : group.children()) {
			String childPath = path + "." + child.id();

			if (child instanceof SettingGroup nested) {
				collectDefaults(nested, childPath, found);
			} else if (((Setting<?>) child).isDefault()) {
				found.add(childPath);
			}
		}
	}
}
