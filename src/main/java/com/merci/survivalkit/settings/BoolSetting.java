package com.merci.survivalkit.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An on/off option. */
public final class BoolSetting extends Setting<Boolean> {
	public BoolSetting(String id, String key, boolean defaultValue) {
		super(id, key, defaultValue);
	}

	@Override
	public JsonElement write() {
		return new JsonPrimitive(get());
	}

	@Override
	public void read(JsonElement json) {
		if (Json.isBoolean(json)) {
			set(json.getAsBoolean());
		} else {
			reset();
		}
	}
}
