package com.merci.survivalkit.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleManagerTest {
	private static final TogglePoller NO_KEYS = module -> false;

	private static TogglePoller clicking(Module... clicked) {
		Set<Module> set = Set.of(clicked);
		return set::contains;
	}

	@Test
	void byIdFindsARegisteredModule() {
		ModuleManager manager = new ModuleManager();
		RecordingModule module = new RecordingModule("autoeat");
		manager.register(module);

		assertSame(module, manager.byId("autoeat").orElseThrow());
	}

	@Test
	void byIdIsEmptyForAnUnknownId() {
		ModuleManager manager = new ModuleManager();

		assertTrue(manager.byId("nope").isEmpty());
	}

	@Test
	void registerRejectsADuplicateId() {
		ModuleManager manager = new ModuleManager();
		manager.register(new RecordingModule("autoeat"));

		assertThrows(IllegalArgumentException.class, () -> manager.register(new RecordingModule("autoeat")));
	}

	@Test
	void modulesAreListedInRegistrationOrder() {
		ModuleManager manager = new ModuleManager();
		RecordingModule first = new RecordingModule("first");
		RecordingModule second = new RecordingModule("second");
		manager.register(first);
		manager.register(second);

		assertEquals(List.of(first, second), manager.modules());
	}

	@Test
	void theModuleListCannotBeModifiedFromOutside() {
		ModuleManager manager = new ModuleManager();
		List<Module> modules = manager.modules();

		assertThrows(UnsupportedOperationException.class, () -> modules.add(new RecordingModule("sneaky")));
	}

	@Test
	void byCategoryReturnsOnlyMatchingModules() {
		ModuleManager manager = new ModuleManager();
		RecordingModule player = new RecordingModule("eat", Category.PLAYER);
		RecordingModule misc = new RecordingModule("other", Category.MISC);
		manager.register(player);
		manager.register(misc);

		assertEquals(List.of(player), manager.byCategory(Category.PLAYER));
	}

	@Test
	void tickDispatchesOnlyToEnabledModules() {
		ModuleManager manager = new ModuleManager();
		RecordingModule on = new RecordingModule("on");
		RecordingModule off = new RecordingModule("off");
		manager.register(on);
		manager.register(off);
		on.setEnabled(true);

		manager.tick(false, NO_KEYS);

		assertEquals(1, on.tickCalls);
		assertEquals(0, off.tickCalls);
	}

	@Test
	void tickTogglesTheModuleWhoseKeyWasClicked() {
		ModuleManager manager = new ModuleManager();
		RecordingModule clicked = new RecordingModule("clicked");
		RecordingModule untouched = new RecordingModule("untouched");
		manager.register(clicked);
		manager.register(untouched);

		manager.tick(false, clicking(clicked));

		assertTrue(clicked.isEnabled());
		assertEquals(1, clicked.enableCalls);
		assertFalse(untouched.isEnabled());
	}

	@Test
	void tickDoesNotPollKeysWhileAScreenIsOpen() {
		ModuleManager manager = new ModuleManager();
		RecordingModule module = new RecordingModule("module");
		manager.register(module);

		manager.tick(true, clicking(module));

		assertFalse(module.isEnabled());
		assertEquals(0, module.enableCalls);
	}

	@Test
	void tickStillDispatchesToEnabledModulesWhileAScreenIsOpen() {
		ModuleManager manager = new ModuleManager();
		RecordingModule module = new RecordingModule("module");
		manager.register(module);
		module.setEnabled(true);

		manager.tick(true, NO_KEYS);

		assertEquals(1, module.tickCalls);
	}

	@Test
	void aModuleToggledOnByItsKeyTicksInTheSameTick() {
		ModuleManager manager = new ModuleManager();
		RecordingModule module = new RecordingModule("module");
		manager.register(module);

		manager.tick(false, clicking(module));

		assertEquals(1, module.tickCalls);
	}

	@Test
	void registeringAModuleAddsItsSettingsToTheRoot() {
		ModuleManager manager = new ModuleManager();
		RecordingModule module = new RecordingModule("autoeat");
		manager.register(module);

		assertSame(module.settings(), manager.settings().byId("autoeat").orElseThrow());
	}
}
