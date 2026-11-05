package com.merci.survivalkit.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.module.impl.AutoEatModule;
import com.merci.survivalkit.module.impl.AutoTotemModule;
import com.merci.survivalkit.module.impl.AutoToolModule;
import com.merci.survivalkit.module.impl.AutoWeaponModule;
import com.merci.survivalkit.module.impl.ScaffoldModule;
import com.merci.survivalkit.module.impl.HudModule;
import com.merci.survivalkit.module.impl.BuildModeModule;
import com.merci.survivalkit.module.impl.ToolPickerModule;
import com.merci.survivalkit.module.impl.VeinMinerModule;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.TestLang;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * No enum value is shown as the id it is stored under.
 *
 * <p>"highest_nutrition" is how the value is written to the config file; it is not a thing to
 * put in front of a player. Every value of every enum in every module is walked here, so a
 * mode added to any module later is caught the same way.
 */
class EnumLabelsTest {
	private static List<Module> modules() {
		return List.of(new AutoEatModule(), new AutoTotemModule(), new AutoToolModule(),
			new ToolPickerModule(new AutoToolModule()), new AutoWeaponModule(), new BuildModeModule(), new VeinMinerModule(), new ScaffoldModule(), new WorldDownloaderModule(), new HudModule());
	}

	private static List<EnumSetting<?>> enumsOf(Module module) {
		List<EnumSetting<?>> found = new ArrayList<>();
		collect(module.settings(), found);
		return found;
	}

	private static void collect(SettingGroup group, List<EnumSetting<?>> found) {
		for (SettingNode child : group.children()) {
			if (child instanceof SettingGroup nested) {
				collect(nested, found);
			} else if (child instanceof EnumSetting<?> choice) {
				found.add(choice);
			}
		}
	}

	@Test
	void everyEnumValueOfEveryModuleReadsAsWordsInEveryLanguage() {
		for (String language : List.of("en_us", "tr_tr")) {
			Map<String, String> entries = TestLang.load(language);
			com.merci.survivalkit.text.Lang.use(key -> entries.getOrDefault(key, key));

			for (Module module : modules()) {
				for (EnumSetting<?> setting : enumsOf(module)) {
					for (Enum<?> option : setting.options()) {
						String label = setting.label(option);
						String id = option.name().toLowerCase(Locale.ROOT);

						assertTrue(!label.isBlank() && !label.equals(id)
								&& !label.equals(Keys.option(setting.key(), option)),
							language + " dilinde ham id görünüyor: " + module.id() + "." + setting.id() + " -> " + label);
					}
				}
			}
		}
	}

	/** The label is the translated one, not something assembled from the constant's name. */
	@Test
	void aValueIsLabelledByItsOwnTranslation() {
		Map<String, String> turkish = TestLang.load("tr_tr");
		com.merci.survivalkit.text.Lang.use(key -> turkish.getOrDefault(key, key));

		EnumSetting<?> scope = enumsOf(new AutoEatModule()).stream()
			.filter(setting -> "scope".equals(setting.id()))
			.findFirst()
			.orElseThrow();

		assertEquals("Tüm envanter", scope.label(scope.options().get(1)));
	}
}
