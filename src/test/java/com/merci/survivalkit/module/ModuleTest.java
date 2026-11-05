package com.merci.survivalkit.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.text.TestNames;
import org.junit.jupiter.api.Test;

class ModuleTest {
	private static final String KEY_O = "key.keyboard.o";

	@Test
	void newModuleIsDisabled() {
		RecordingModule module = new RecordingModule("test");

		assertFalse(module.isEnabled());
		assertEquals(0, module.enableCalls);
		assertEquals(0, module.disableCalls);
	}

	@Test
	void moduleKeepsTheIdentityItWasBuiltWith() {
		Module module = new RecordingModule("autoeat", Category.PLAYER);

		assertEquals("autoeat", module.id());
		assertSame(Category.PLAYER, module.category());
	}

	@Test
	void enablingFiresOnEnableOnce() {
		RecordingModule module = new RecordingModule("test");

		module.setEnabled(true);

		assertTrue(module.isEnabled());
		assertEquals(1, module.enableCalls);
		assertEquals(0, module.disableCalls);
	}

	@Test
	void disablingFiresOnDisableOnce() {
		RecordingModule module = new RecordingModule("test");
		module.setEnabled(true);

		module.setEnabled(false);

		assertFalse(module.isEnabled());
		assertEquals(1, module.enableCalls);
		assertEquals(1, module.disableCalls);
	}

	@Test
	void settingTheSameStateAgainDoesNotFireHooks() {
		RecordingModule module = new RecordingModule("test");
		module.setEnabled(true);

		module.setEnabled(true);

		assertEquals(1, module.enableCalls);
		assertEquals(0, module.disableCalls);
	}

	@Test
	void toggleFlipsEnabledState() {
		RecordingModule module = new RecordingModule("test");

		module.toggle();
		assertTrue(module.isEnabled());

		module.toggle();
		assertFalse(module.isEnabled());
		assertEquals(1, module.enableCalls);
		assertEquals(1, module.disableCalls);
	}

	@Test
	void aModuleOwnsASettingsGroupNamedAfterItself() {
		RecordingModule module = new RecordingModule("autoeat");

		assertEquals("autoeat", module.settings().id());
	}

	@Test
	void aModuleCanRegisterItsOwnSettings() {
		RecordingModule module = new RecordingModule("autoeat");
		BoolSetting setting = module.settings().add(new BoolSetting("risky", "test.risky", false));

		assertEquals(setting, module.settings().byId("risky").orElseThrow());
	}

	@Test
	void aModulesToggleKeyLivesInItsOwnSettingsGroup() {
		RecordingModule module = new RecordingModule("autoeat");

		assertSame(module.keybind(), module.settings().byId("key").orElseThrow());
	}

	@Test
	void aModuleCanShipWithNoKeyAtAll() {
		RecordingModule module = new RecordingModule("wdl");

		assertFalse(module.keybind().isBound());
	}

	@Test
	void aReboundToggleKeyIsWrittenWithTheModulesOtherSettings() {
		RecordingModule module = new RecordingModule("autoeat");

		module.keybind().set(KEY_O);

		assertEquals(KEY_O, module.settings().write().getAsJsonObject().get("key").getAsString());
	}

	@Test
	void theOnOffStateIsSavedWithTheModule() {
		RecordingModule module = new RecordingModule("autoeat");

		module.setEnabled(true);

		assertTrue(module.settings().write().getAsJsonObject().get("enabled").getAsBoolean());
	}

	@Test
	void loadingAProfileThatTurnsTheModuleOffFiresOnDisable() {
		RecordingModule module = new RecordingModule("autoeat");
		module.setEnabled(true);

		module.settings().<BoolSetting>byId("enabled").orElseThrow().set(false);
		module.applyEnabledSetting();

		assertFalse(module.isEnabled());
		assertEquals(1, module.disableCalls);
	}

	@Test
	void loadingAProfileThatTurnsTheModuleOnFiresOnEnable() {
		RecordingModule module = new RecordingModule("autoeat");

		module.settings().<BoolSetting>byId("enabled").orElseThrow().set(true);
		module.applyEnabledSetting();

		assertTrue(module.isEnabled());
		assertEquals(1, module.enableCalls);
	}

	@Test
	void aProfileThatChangesNothingFiresNoHooks() {
		RecordingModule module = new RecordingModule("autoeat");
		module.setEnabled(true);

		module.applyEnabledSetting();
		module.applyEnabledSetting();

		assertEquals(1, module.enableCalls);
		assertEquals(0, module.disableCalls);
	}

	/** The menu has a place for this and nowhere else to explain the module, so it is required. */
	/** A module is named and described in the language files, under its own id. */
	@Test
	void aModuleIsNamedAndDescribedByItsId() {
		TestNames.install();
		TestNames.module("described", "Tarifli");
		Module module = new RecordingModule("described");

		assertEquals("Tarifli", module.displayName());
		assertEquals("Tarifli ne yapar", module.description());
	}
}
