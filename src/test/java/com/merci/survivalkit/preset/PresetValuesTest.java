package com.merci.survivalkit.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.merci.survivalkit.module.Category;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.settings.SettingGroup;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A preset sets what it names and nothing else; never the module's switch or its keys. */
class PresetValuesTest {
	/** A module with a heading, a real nested level, a key of its own, and a key setting. */
	static final class Probe extends Module {
		final SettingGroup heading = settings().add(SettingGroup.section("heading", "test.heading"));
		final IntSetting width = heading.add(new IntSetting("width", "test.width", 1, 1, 3));
		final BoolSetting torches = heading.add(new BoolSetting("torches", "test.torches", false));
		final SettingGroup inner = settings().add(new SettingGroup("inner", "test.inner"));
		final IntSetting depth = inner.add(new IntSetting("depth", "test.depth", 5, 0, 10));
		final KeybindSetting lockKey = settings().add(new KeybindSetting("lock_key", "test.lock_key", KeybindSetting.UNBOUND));

		Probe() {
			super("probe", Category.MISC, KeybindSetting.UNBOUND);
		}
	}

	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	@Test
	void whatThePresetNamesIsSetAndWhatChangedComesBack() {
		Probe probe = new Probe();
		probe.torches.set(true);

		List<PresetValues.Change> changes = PresetValues.apply(probe.settings(), json("{\"width\": 3, \"torches\": true}"));

		assertEquals(3, probe.width.get());
		assertEquals(List.of(probe.width), changes.stream().map(PresetValues.Change::setting).toList());
		assertEquals("1", changes.getFirst().before().toString());
		assertEquals("3", changes.getFirst().after().toString());
	}

	@Test
	void whatItDoesNotNameIsLeftAlone() {
		Probe probe = new Probe();
		probe.depth.set(8);
		probe.torches.set(true);

		PresetValues.apply(probe.settings(), json("{\"width\": 2}"));

		assertEquals(8, probe.depth.get());
		assertTrue(probe.torches.get());
	}

	@Test
	void aNestedLevelIsReachedTheWayTheFileHasIt() {
		Probe probe = new Probe();

		PresetValues.apply(probe.settings(), json("{\"inner\": {\"depth\": 9}}"));

		assertEquals(9, probe.depth.get());
	}

	/** Not even a saved one that happens to carry them. */
	@Test
	void theSwitchAndTheKeysAreNeverTouched() {
		Probe probe = new Probe();

		List<PresetValues.Change> changes = PresetValues.apply(probe.settings(),
			json("{\"enabled\": true, \"lock_key\": \"key.keyboard.g\", \"width\": 2}"));

		assertFalse(probe.isEnabled());
		assertEquals(KeybindSetting.UNBOUND, probe.lockKey.get());
		assertEquals(1, changes.size());
	}

	@Test
	void aPreviewChangesNothing() {
		Probe probe = new Probe();

		List<PresetValues.Change> changes = PresetValues.preview(probe.settings(), json("{\"width\": 3, \"inner\": {\"depth\": 0}}"));

		assertEquals(2, changes.size());
		assertEquals(1, probe.width.get());
		assertEquals(5, probe.depth.get());
	}

	@Test
	void capturedIsEverythingButTheSwitchAndTheKeys() {
		Probe probe = new Probe();
		probe.width.set(2);
		probe.setEnabled(true);

		JsonObject captured = PresetValues.capture(probe.settings());

		assertEquals(json("{\"width\": 2, \"torches\": false, \"inner\": {\"depth\": 5}}"), captured);
	}

	@Test
	void capturedAndAppliedComesBackAsItWas() {
		Probe probe = new Probe();
		probe.width.set(3);
		probe.depth.set(1);
		JsonObject saved = PresetValues.capture(probe.settings());
		probe.width.set(1);
		probe.depth.set(7);

		PresetValues.apply(probe.settings(), saved);

		assertEquals(3, probe.width.get());
		assertEquals(1, probe.depth.get());
	}
}
