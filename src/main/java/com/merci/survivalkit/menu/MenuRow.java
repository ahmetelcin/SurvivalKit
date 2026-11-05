package com.merci.survivalkit.menu;

import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.settings.SettingNode;
import java.util.Objects;

/**
 * One line of the right-hand panel: a setting to edit, or a group heading to fold.
 *
 * @param node  what the line stands for
 * @param depth how far it is indented, 0 for a module's own settings
 */
public record MenuRow(SettingNode node, int depth) {
	public MenuRow {
		Objects.requireNonNull(node, "node");

		if (depth < 0) {
			throw new IllegalArgumentException("Derinlik negatif olamaz: " + depth);
		}
	}

	public boolean isGroup() {
		return node instanceof SettingGroup;
	}

	public String id() {
		return node.id();
	}

	public String displayName() {
		return node.displayName();
	}
}
