package com.merci.survivalkit.module;

import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.Objects;

/**
 * A single toggleable feature. Subclasses put their behaviour in the lifecycle
 * hooks; {@link ModuleManager} decides when those run.
 */
public abstract class Module {
	private final String id;
	private final Category category;
	private final SettingGroup settings;
	private final BoolSetting enabled;
	private final KeybindSetting keybind;
	/** What the lifecycle hooks have been told, which the setting can drift from. */
	private boolean running;

	/**
	 * A module's name and its one-line description are not passed in: they are looked up
	 * under its id, so every module reads in the player's language and a new one cannot be
	 * added without translating it.
	 *
	 * @param defaultKey the key that toggles this module, named the way Minecraft names keys
	 *                   ("key.keyboard.k"), or {@link KeybindSetting#UNBOUND} to ship with
	 *                   no key at all so the user picks one.
	 */
	protected Module(String id, Category category, String defaultKey) {
		this(id, category, defaultKey, false);
	}

	/**
	 * @param enabledByDefault on from the first start, for a module that only shows something —
	 *                         the HUD — rather than doing anything in the world
	 */
	protected Module(String id, Category category, String defaultKey, boolean enabledByDefault) {
		this.id = Objects.requireNonNull(id, "id");
		this.category = Objects.requireNonNull(category, "category");
		this.settings = new SettingGroup(this.id);
		// Every module has these two, so they share one pair of translations.
		this.enabled = settings.add(new BoolSetting("enabled", "common.enabled", enabledByDefault));
		this.keybind = settings.add(new KeybindSetting("key", "common.key", defaultKey));
	}

	public final String id() {
		return id;
	}

	public final String displayName() {
		return Lang.of(Keys.module(id));
	}

	/** One line on what this module does, shown above its settings. Never blank. */
	public final String description() {
		return Lang.of(Keys.moduleDescription(id));
	}

	public final Category category() {
		return category;
	}

	/** This module's own options, saved under its id. */
	public final SettingGroup settings() {
		return settings;
	}

	/** The toggle key, saved with the rest of this module's options. */
	public final KeybindSetting keybind() {
		return keybind;
	}

	public final boolean isEnabled() {
		return running;
	}

	/** Setting the state it already has is a no-op, so hooks never fire twice. */
	public final void setEnabled(boolean enabled) {
		this.enabled.set(enabled);

		if (running == enabled) {
			return;
		}

		running = enabled;

		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}
	}

	public final void toggle() {
		setEnabled(!running);
	}

	/**
	 * Catches up with a setting something else changed — loading a profile, or a hand-edited
	 * file. A module switched off this way gets its onDisable hook like any other, so
	 * it can put down whatever it was holding.
	 */
	final void applyEnabledSetting() {
		if (enabled.get() != running) {
			setEnabled(enabled.get());
		}
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Runs once per client tick while this module is enabled. */
	protected void onTick() {
	}
}
