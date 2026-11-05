package com.merci.survivalkit.util;

import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.settings.SettingNode;
import com.merci.survivalkit.text.Lang;
import java.util.ArrayList;
import java.util.List;

/**
 * House rules a settings tree has to keep, checked by walking it.
 *
 * <p>The point of walking rather than listing is that a module added next year is checked
 * too, without anyone remembering to add a test for it.
 */
public final class SettingsAudit {
	private SettingsAudit() {
	}

	/**
	 * Dotted paths of every setting left without a tooltip, deepest last.
	 *
	 * <p>Groups are exempt on purpose: a group is a heading, and its name already says what
	 * it is. A single setting is a choice the user has to understand, so it must explain
	 * itself — the menu has nowhere else to put that.
	 *
	 * <p>Since the words come from the language files, "without a tooltip" now means the
	 * language in use has nothing to say for it: either blank, or the key showing through.
	 * The rule is the same one, checked against what the player would actually read.
	 */
	public static List<String> settingsWithoutTooltip(SettingGroup root) {
		List<String> found = new ArrayList<>();
		walk(root, root.id(), found);
		return found;
	}

	/**
	 * How many settings {@link #settingsWithoutTooltip} looks at: every one, however deep,
	 * and no headings. This is the number a passing check can honestly claim to have covered.
	 */
	public static int settingsChecked(SettingGroup root) {
		int count = 0;

		for (SettingNode child : root.children()) {
			count += child instanceof SettingGroup nested ? settingsChecked(nested) : 1;
		}

		return count;
	}

	private static void walk(SettingGroup group, String path, List<String> found) {
		for (SettingNode child : group.children()) {
			String childPath = path + "." + child.id();

			if (child instanceof SettingGroup nested) {
				walk(nested, childPath, found);
			} else if (child.tooltip().isBlank() || Lang.isRaw(child.tooltip())) {
				found.add(childPath);
			}
		}
	}
}
