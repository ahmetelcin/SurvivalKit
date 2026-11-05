package com.merci.survivalkit.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.List;
import java.util.Locale;

/** One constant out of an enum, stored by its lowercase name. */
public final class EnumSetting<E extends Enum<E>> extends Setting<E> {
	private final List<E> options;

	public EnumSetting(String id, String key, E defaultValue) {
		super(id, key, defaultValue);
		this.options = List.of(defaultValue.getDeclaringClass().getEnumConstants());
	}

	/** Every constant, in declaration order. */
	public List<E> options() {
		return options;
	}

	/**
	 * What one value is called on screen. The stored spelling — "highest_nutrition" — belongs
	 * in the config file and nowhere else, so each value carries its own translation, hung off
	 * the setting's key rather than the enum's: two settings on the same enum may well want
	 * to word it differently.
	 */
	public String label(Enum<?> option) {
		return Lang.of(Keys.option(key(), option));
	}

	/** Advances to the next constant, wrapping at the end. */
	public void cycle() {
		set(options.get((get().ordinal() + 1) % options.size()));
	}

	private static String nameOf(Enum<?> constant) {
		return constant.name().toLowerCase(Locale.ROOT);
	}

	@Override
	public JsonElement write() {
		return new JsonPrimitive(nameOf(get()));
	}

	@Override
	public void read(JsonElement json) {
		if (!Json.isString(json)) {
			reset();
			return;
		}

		String stored = json.getAsString().toLowerCase(Locale.ROOT);
		options.stream().filter(option -> nameOf(option).equals(stored)).findFirst().ifPresentOrElse(this::set, this::reset);
	}
}
