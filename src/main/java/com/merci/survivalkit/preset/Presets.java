package com.merci.survivalkit.preset;

import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.profile.ProfileNames;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A module's presets — the built-in ones first, then the player's own — and what can be done
 * with them. Applying one changes that module's settings and no other module's.
 */
public final class Presets {
	private final PresetStore store;

	public Presets(PresetStore store) {
		this.store = Objects.requireNonNull(store, "store");
	}

	public List<Preset> of(Module module) {
		List<Preset> presets = new ArrayList<>(BuiltInPresets.of(module.id()));
		presets.addAll(store.list(module.id()));
		return presets;
	}

	public List<PresetValues.Change> apply(Module module, Preset preset) {
		return PresetValues.apply(module.settings(), preset.values());
	}

	/** What applying would change now, nothing changed. */
	public List<PresetValues.Change> preview(Module module, Preset preset) {
		return PresetValues.preview(module.settings(), preset.values());
	}

	/**
	 * The module's settings as they are now, under a name of the player's. A name already in the
	 * list, built-in or not, whatever its case, is refused rather than overwritten.
	 */
	public Optional<ProfileNames.Problem> save(Module module, String name) {
		List<String> taken = of(module).stream().map(Preset::name).toList();
		Optional<ProfileNames.Problem> problem = ProfileNames.check(name, taken, null);

		if (problem.isEmpty()) {
			store.save(module.id(), name.strip(), PresetValues.capture(module.settings()));
		}

		return problem;
	}

	/** One of the player's own, gone; a built-in one stays. */
	public boolean delete(Module module, Preset preset) {
		if (preset.builtIn()) {
			return false;
		}

		store.delete(module.id(), preset.id());
		return true;
	}

	public PresetStore store() {
		return store;
	}
}
