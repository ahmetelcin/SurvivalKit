package com.merci.survivalkit.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Free text, trimmed and capped at a maximum length. */
public final class TextSetting extends Setting<String> {
	private final int maxLength;

	public TextSetting(String id, String key, String defaultValue, int maxLength) {
		super(id, key, defaultValue);
		this.maxLength = maxLength;
	}

	public boolean isBlank() {
		return get().isBlank();
	}

	/** The cap the menu's text box is told about, so it stops taking input at the same point. */
	public int maxLength() {
		return maxLength;
	}

	@Override
	protected String sanitize(String value) {
		String trimmed = value.trim();
		return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
	}

	@Override
	public JsonElement write() {
		return new JsonPrimitive(get());
	}

	@Override
	public void read(JsonElement json) {
		if (Json.isString(json)) {
			set(json.getAsString());
		} else {
			reset();
		}
	}
}
