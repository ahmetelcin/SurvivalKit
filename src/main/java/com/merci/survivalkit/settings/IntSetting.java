package com.merci.survivalkit.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A whole number clamped to an inclusive range. */
public final class IntSetting extends Setting<Integer> {
	private final int min;
	private final int max;

	public IntSetting(String id, String key, int defaultValue, int min, int max) {
		super(id, key, defaultValue);

		if (min > max) {
			throw new IllegalArgumentException("Alt sınır üst sınırdan büyük: " + min + " > " + max);
		}

		if (defaultValue < min || defaultValue > max) {
			throw new IllegalArgumentException("Varsayılan değer aralık dışında: " + defaultValue);
		}

		this.min = min;
		this.max = max;
	}

	public int min() {
		return min;
	}

	public int max() {
		return max;
	}

	@Override
	protected Integer sanitize(Integer value) {
		return Math.clamp(value, min, max);
	}

	@Override
	public JsonElement write() {
		return new JsonPrimitive(get());
	}

	@Override
	public void read(JsonElement json) {
		if (Json.isNumber(json)) {
			set(json.getAsInt());
		} else {
			reset();
		}
	}
}
