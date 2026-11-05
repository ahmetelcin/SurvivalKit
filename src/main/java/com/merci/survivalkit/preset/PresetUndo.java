package com.merci.survivalkit.preset;

import com.google.gson.JsonElement;
import com.merci.survivalkit.settings.Setting;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * What each setting was before a preset changed it, so the menu's ↺ can put that one setting
 * back on its own. It only does so while the setting still holds what the preset gave it: once
 * the player has changed it again, ↺ is the ordinary "back to the default" it always was.
 *
 * <p>Two presets in a row are one change as far as ↺ goes: a setting the second one moves
 * again goes back to where it was before the first.
 */
public final class PresetUndo {
	private record Entry(JsonElement before, JsonElement after) {
	}

	private final Map<Setting<?>, Entry> entries = new IdentityHashMap<>();

	public void record(List<PresetValues.Change> changes) {
		for (PresetValues.Change change : changes) {
			Entry earlier = entries.get(change.setting());
			JsonElement before = earlier != null && earlier.after().equals(change.before()) ? earlier.before() : change.before();

			if (before.equals(change.after())) {
				entries.remove(change.setting());
			} else {
				entries.put(change.setting(), new Entry(before, change.after()));
			}
		}
	}

	/** True while the setting still holds what a preset gave it. */
	public boolean canUndo(Setting<?> setting) {
		Entry entry = entries.get(setting);
		return entry != null && setting.write().equals(entry.after());
	}

	/** Puts the setting back to what it was before the preset; false, and nothing done, if it cannot. */
	public boolean undo(Setting<?> setting) {
		if (!canUndo(setting)) {
			return false;
		}

		setting.read(entries.remove(setting).before());
		return true;
	}

	/** The settings under the menu are another profile's now: nothing here is theirs. */
	public void clear() {
		entries.clear();
	}
}
