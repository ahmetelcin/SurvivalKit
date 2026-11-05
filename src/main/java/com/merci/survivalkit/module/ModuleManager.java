package com.merci.survivalkit.module;

import com.merci.survivalkit.settings.SettingGroup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Owns every module and drives them from the client tick. */
public final class ModuleManager {
	private final List<Module> modules = new ArrayList<>();
	private final List<Module> modulesView = Collections.unmodifiableList(modules);
	private final Map<String, Module> index = new HashMap<>();
	private final SettingGroup settings = new SettingGroup("modules", "Modüller");

	public void register(Module module) {
		Objects.requireNonNull(module, "module");

		if (index.putIfAbsent(module.id(), module) != null) {
			throw new IllegalArgumentException("Bu modül id'si zaten kayıtlı: " + module.id());
		}

		modules.add(module);
		settings.add(module.settings());
	}

	/** Registration order, read-only. */
	public List<Module> modules() {
		return modulesView;
	}

	/** Every module's options under one root, ready for {@link com.merci.survivalkit.settings.SettingsStorage}. */
	public SettingGroup settings() {
		return settings;
	}

	public Optional<Module> byId(String id) {
		return Optional.ofNullable(index.get(id));
	}

	public List<Module> byCategory(Category category) {
		return modules.stream().filter(module -> module.category() == category).toList();
	}

	/**
	 * One client tick: take on any state a profile load left in the settings, consume
	 * module keys, then run the enabled modules. Keys are skipped while a screen is open
	 * so typing never toggles anything.
	 */
	public void tick(boolean screenOpen, TogglePoller poller) {
		// A profile load changes the setting, not the module; this is where that lands.
		for (Module module : modules) {
			module.applyEnabledSetting();
		}

		if (!screenOpen) {
			for (Module module : modules) {
				if (poller.consumeToggle(module)) {
					module.toggle();
				}
			}
		}

		for (Module module : modules) {
			if (module.isEnabled()) {
				module.onTick();
			}
		}
	}
}
