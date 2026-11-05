package com.merci.survivalkit.preset;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.settings.Setting;
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.settings.SettingNode;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A preset against one module's settings tree: applied, looked at before applying, and taken
 * from the tree as it is now.
 *
 * <p>Two things a preset never touches, whatever its values say: whether the module is on, and
 * its keys. A preset is a way of working, not a switch, and a key is the player's hand, not a
 * setting of the module's behaviour.
 */
public final class PresetValues {
	/** The setting every module has for being on; a preset leaves it alone. */
	public static final String ENABLED = "enabled";

	/** One setting a preset changed, as the settings file writes it before and after. */
	public record Change(Setting<?> setting, JsonElement before, JsonElement after) {
	}

	private PresetValues() {
	}

	/** Sets what the preset sets and nothing else; what actually changed comes back, in menu order. */
	public static List<Change> apply(SettingGroup root, JsonObject values) {
		List<Change> changes = new ArrayList<>();
		walk(root, values, true, changes);
		return changes;
	}

	/** What applying would change, with the tree left exactly as it was. */
	public static List<Change> preview(SettingGroup root, JsonObject values) {
		List<Change> changes = apply(root, values);

		for (Change change : changes) {
			change.setting().read(change.before());
		}

		return changes;
	}

	/** Every setting the module has now, but for being on and its keys: what the player saves as their own. */
	public static JsonObject capture(SettingGroup root) {
		JsonObject values = root.write().getAsJsonObject();
		strip(root, values, true);
		return values;
	}

	/**
	 * One change as the menu says it: the setting's name, then what it was and what it will be in
	 * the menu's own words. A list or a text says only its name: its value is no short thing.
	 */
	public static String describe(Change change) {
		String before = label(change.setting(), change.before());
		String after = label(change.setting(), change.after());
		return before == null || after == null
			? change.setting().displayName()
			: change.setting().displayName() + ": " + before + " → " + after;
	}

	private static String label(Setting<?> setting, JsonElement value) {
		if (setting instanceof BoolSetting) {
			return Lang.of(value.getAsBoolean() ? Keys.MENU_ON : Keys.MENU_OFF);
		}

		if (setting instanceof IntSetting) {
			return value.toString();
		}

		if (setting instanceof EnumSetting<?> choice) {
			String stored = value.getAsString();
			return choice.options().stream()
				.filter(option -> option.name().toLowerCase(Locale.ROOT).equals(stored))
				.findFirst()
				.map(choice::label)
				.orElse(stored);
		}

		return null;
	}

	private static void walk(SettingGroup group, JsonObject values, boolean top, List<Change> changes) {
		for (SettingNode child : group.children()) {
			if (child instanceof SettingGroup nested) {
				if (nested.isSection()) {
					// A heading only: its settings sit at this level of the file.
					walk(nested, values, top, changes);
				} else if (values.has(nested.id()) && values.get(nested.id()).isJsonObject()) {
					walk(nested, values.getAsJsonObject(nested.id()), false, changes);
				}

				continue;
			}

			Setting<?> setting = (Setting<?>) child;

			if (!values.has(setting.id()) || untouchable(setting, top)) {
				continue;
			}

			JsonElement before = setting.write();
			setting.read(values.get(setting.id()));
			JsonElement after = setting.write();

			if (!after.equals(before)) {
				changes.add(new Change(setting, before, after));
			}
		}
	}

	private static void strip(SettingGroup group, JsonObject values, boolean top) {
		for (SettingNode child : group.children()) {
			if (child instanceof SettingGroup nested) {
				if (nested.isSection()) {
					strip(nested, values, top);
				} else if (values.has(nested.id()) && values.get(nested.id()).isJsonObject()) {
					strip(nested, values.getAsJsonObject(nested.id()), false);
				}
			} else if (untouchable((Setting<?>) child, top)) {
				values.remove(child.id());
			}
		}
	}

	private static boolean untouchable(Setting<?> setting, boolean top) {
		return setting instanceof KeybindSetting || top && setting.id().equals(ENABLED);
	}
}
