package com.merci.survivalkit.menu;

import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.SettingNode;
import java.util.Objects;

/**
 * One setting the search found, and the module it lives in.
 *
 * <p>The module comes along because a result is somewhere to go: opening it means selecting
 * that module first, and the list shows which module a setting belongs to.
 *
 * @param module  the module holding the setting
 * @param setting what matched
 */
public record SearchHit(Module module, SettingNode setting) {
	public SearchHit {
		Objects.requireNonNull(module, "module");
		Objects.requireNonNull(setting, "setting");
	}

	public String id() {
		return setting.id();
	}

	public String displayName() {
		return setting.displayName();
	}
}
