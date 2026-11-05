package com.merci.survivalkit.settings;

import com.google.gson.JsonElement;

/** Anything that can appear in the settings tree: a single setting or a group of them. */
public interface SettingNode {
	String id();

	String displayName();

	String tooltip();

	/** False hides the node from the menu; its value is kept either way. */
	boolean isVisible();

	void reset();

	JsonElement write();

	/** Applies stored JSON. Anything unreadable falls back to the default rather than throwing. */
	void read(JsonElement json);
}
