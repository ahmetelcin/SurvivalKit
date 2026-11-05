package com.merci.survivalkit.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Type checks for stored JSON. Settings files are hand-editable and survive mod
 * updates, so every read has to cope with the wrong type being there.
 */
final class Json {
	private Json() {
	}

	private static boolean isPrimitive(JsonElement json) {
		return json != null && json.isJsonPrimitive();
	}

	static boolean isBoolean(JsonElement json) {
		return isPrimitive(json) && ((JsonPrimitive) json).isBoolean();
	}

	static boolean isNumber(JsonElement json) {
		return isPrimitive(json) && ((JsonPrimitive) json).isNumber();
	}

	static boolean isString(JsonElement json) {
		return isPrimitive(json) && ((JsonPrimitive) json).isString();
	}
}
