package com.merci.survivalkit.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.module.impl.AutoEatModule;
import com.merci.survivalkit.module.impl.AutoWeaponModule;
import com.merci.survivalkit.module.impl.ScaffoldModule;
import com.merci.survivalkit.profile.ProfileNames;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.settings.Setting;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.merci.survivalkit.text.TestLang;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The built-in presets against the real modules, and the player's own saved, kept and deleted. */
class PresetsTest {
	@TempDir
	Path dir;

	private Presets presets;

	@BeforeEach
	void turkish() {
		TestLang.install("tr_tr");
		presets = new Presets(new PresetStore(dir.resolve("presets.json")));
	}

	private static Preset named(Presets presets, Module module, String name) {
		return presets.of(module).stream().filter(preset -> preset.name().equals(name)).findFirst()
			.orElseThrow(() -> new AssertionError("hazır ayar yok: " + name));
	}

	private static String value(Module module, String id) {
		return module.settings().<Setting<?>>byId(id).orElseThrow().write().toString();
	}

	@Test
	void theBuiltInOnesAreThereInOrderAndNamed() {
		assertEquals(List.of("Köprü", "Rampa", "Geniş yol"), presets.of(new ScaffoldModule()).stream().map(Preset::name).toList());
		assertEquals(List.of("Sadece nişan", "Çoğunluk"), presets.of(new AutoWeaponModule()).stream().map(Preset::name).toList());
		assertEquals(List.of("Tasarruflu", "Hep tok"), presets.of(new AutoEatModule()).stream().map(Preset::name).toList());
	}

	/** Every value every built-in names is a real setting and takes the value as written: no typo goes quietly to a default. */
	@Test
	void everyBuiltInSetsExactlyWhatItSays() {
		for (Module module : List.of(new ScaffoldModule(), new AutoWeaponModule(), new AutoEatModule())) {
			for (Preset preset : BuiltInPresets.of(module.id())) {
				module.settings().reset();
				presets.apply(module, preset);

				preset.values().entrySet().forEach(entry -> assertEquals(entry.getValue().toString(), value(module, entry.getKey()),
					module.id() + " / " + preset.id() + " / " + entry.getKey()));
			}
		}
	}

	@Test
	void theBridge() {
		ScaffoldModule scaffold = new ScaffoldModule();
		scaffold.settings().<IntSetting>byId("width").orElseThrow().set(3);

		presets.apply(scaffold, named(presets, scaffold, "Köprü"));

		assertEquals("1", value(scaffold, "width"));
		assertEquals("\"start\"", value(scaffold, "y_lock"));
		assertEquals("\"at_edge\"", value(scaffold, "safe_walk"));
	}

	/** Hovering says what would change, in the menu's words and order, and changes nothing. */
	@Test
	void thePreviewSaysWhatWouldChangeInTheMenusWords() {
		ScaffoldModule scaffold = new ScaffoldModule();
		scaffold.settings().<IntSetting>byId("width").orElseThrow().set(3);

		List<String> lines = presets.preview(scaffold, named(presets, scaffold, "Köprü")).stream().map(PresetValues::describe).toList();

		assertEquals(List.of(
			"Y kilidi: Kapalı → Başladığım yerin Y'si",
			Lang.of(Keys.setting("scaffold.width")) + ": 3 → 1",
			"Kenarda dur: Kapalı → Kenarda"), lines);
		assertEquals("3", value(scaffold, "width"));

		AutoEatModule eat = new AutoEatModule();
		List<String> eating = presets.preview(eat, named(presets, eat, "Hep tok")).stream().map(PresetValues::describe).toList();
		assertTrue(eating.contains(Lang.of(Keys.setting("autoeat.eat_early_when_hurt")) + ": Kapalı → Açık"), eating.toString());
		assertTrue(eating.contains(Lang.of(Keys.setting("autoeat.threshold")) + ": 17 → 19"), eating.toString());
	}

	@Test
	void theRampAndTheWideRoad() {
		ScaffoldModule scaffold = new ScaffoldModule();

		presets.apply(scaffold, named(presets, scaffold, "Rampa"));
		assertEquals("\"up\"", value(scaffold, "ramp"));
		assertEquals("1", value(scaffold, "width"));

		presets.apply(scaffold, named(presets, scaffold, "Geniş yol"));
		assertEquals("3", value(scaffold, "width"));
		assertEquals("\"start\"", value(scaffold, "y_lock"));
		assertEquals("true", value(scaffold, "torches"));
		assertEquals("\"off\"", value(scaffold, "ramp"));
	}

	@Test
	void theWeaponOnes() {
		AutoWeaponModule weapon = new AutoWeaponModule();

		presets.apply(weapon, named(presets, weapon, "Sadece nişan"));
		assertEquals("\"aim_only\"", value(weapon, "source"));

		presets.apply(weapon, named(presets, weapon, "Çoğunluk"));
		assertEquals("\"aim_then_crowd\"", value(weapon, "source"));
	}

	@Test
	void theEatingOnes() {
		AutoEatModule eat = new AutoEatModule();

		presets.apply(eat, named(presets, eat, "Tasarruflu"));
		assertEquals("\"auto\"", value(eat, "threshold_mode"));
		assertEquals("\"lowest_value_first\"", value(eat, "fallback"));

		presets.apply(eat, named(presets, eat, "Hep tok"));
		assertEquals("\"fixed\"", value(eat, "threshold_mode"));
		assertEquals("19", value(eat, "threshold"));
		assertEquals("true", value(eat, "eat_early_when_hurt"));
	}

	@Test
	void savedItIsInTheListAfterTheBuiltInsAndInTheFile() throws IOException {
		ScaffoldModule scaffold = new ScaffoldModule();
		scaffold.settings().<IntSetting>byId("width").orElseThrow().set(2);

		assertEquals(Optional.empty(), presets.save(scaffold, "  benim  "));

		assertEquals(List.of("Köprü", "Rampa", "Geniş yol", "benim"), presets.of(scaffold).stream().map(Preset::name).toList());
		assertTrue(Files.readString(dir.resolve("presets.json")).contains("\"benim\""));

		Presets again = new Presets(new PresetStore(dir.resolve("presets.json")));
		scaffold.settings().reset();
		again.apply(scaffold, named(again, scaffold, "benim"));
		assertEquals("2", value(scaffold, "width"));
	}

	@Test
	void aNameInTheListIsRefusedWhateverItsCase() {
		ScaffoldModule scaffold = new ScaffoldModule();
		presets.save(scaffold, "benim");

		assertEquals(Optional.of(ProfileNames.Problem.TAKEN), presets.save(scaffold, "köprü"));
		assertEquals(Optional.of(ProfileNames.Problem.TAKEN), presets.save(scaffold, "BENIM"));
		assertEquals(Optional.of(ProfileNames.Problem.BLANK), presets.save(scaffold, " "));
	}

	@Test
	void theyAreTheModulesOwn() {
		presets.save(new ScaffoldModule(), "benim");

		assertEquals(List.of("Tasarruflu", "Hep tok"), presets.of(new AutoEatModule()).stream().map(Preset::name).toList());
	}

	@Test
	void theOwnOnesCanBeDeletedTheBuiltInOnesCannot() {
		ScaffoldModule scaffold = new ScaffoldModule();
		presets.save(scaffold, "benim");

		assertFalse(presets.delete(scaffold, named(presets, scaffold, "Köprü")));
		assertTrue(presets.delete(scaffold, named(presets, scaffold, "benim")));

		assertEquals(List.of("Köprü", "Rampa", "Geniş yol"), presets.of(scaffold).stream().map(Preset::name).toList());
		assertEquals(List.of(), new PresetStore(dir.resolve("presets.json")).list("scaffold"));
	}

	@Test
	void anUnreadableFileIsNoPresetsAtAll() throws IOException {
		Files.writeString(dir.resolve("presets.json"), "{bozuk");

		assertEquals(3, new Presets(new PresetStore(dir.resolve("presets.json"))).of(new ScaffoldModule()).size());
	}

	/** Applying touches no setting the preset does not name — the enum left over from before stays. */
	@Test
	void applyingLeavesTheRestOfTheModule() {
		ScaffoldModule scaffold = new ScaffoldModule();
		EnumSetting<?> safeWalk = scaffold.settings().<EnumSetting<?>>byId("safe_walk").orElseThrow();
		safeWalk.read(com.google.gson.JsonParser.parseString("\"always\""));

		presets.apply(scaffold, named(presets, scaffold, "Rampa"));

		assertEquals("\"always\"", safeWalk.write().toString());
	}
}
