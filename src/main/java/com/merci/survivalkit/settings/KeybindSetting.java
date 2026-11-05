package com.merci.survivalkit.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * A key written the way Minecraft writes it: "key.keyboard.k", "key.mouse.4". The config
 * stays readable by hand and a mouse button is a binding like any other. Turning the name
 * into something pollable is the client's job, so the settings core stays free of
 * Minecraft types.
 */
public final class KeybindSetting extends Setting<String> {
	/** The name Minecraft itself uses for "no key". */
	public static final String UNBOUND = "key.keyboard.unknown";

	private static final String[] PREFIXES = {"key.keyboard.", "key.mouse.", "scancode."};

	/**
	 * Translates the raw GLFW key codes written by earlier versions of this mod. The client
	 * installs the real one at startup; without it an old code reads as unbound rather than
	 * as some arbitrary key.
	 */
	private static IntFunction<String> legacyNames = code -> UNBOUND;

	public KeybindSetting(String id, String key, String defaultKey) {
		super(id, key, defaultKey);
	}

	/** Installed once, by whoever knows how to name a key code. */
	public static void useLegacyKeyNames(IntFunction<String> names) {
		legacyNames = Objects.requireNonNull(names, "names");
	}

	public boolean isBound() {
		return !UNBOUND.equals(get());
	}

	public void clear() {
		set(UNBOUND);
	}

	private static boolean isWellFormed(String name) {
		for (String prefix : PREFIXES) {
			if (name.length() > prefix.length() && name.startsWith(prefix)) {
				return true;
			}
		}

		return false;
	}

	/** A name we do not recognise is no key at all, rather than a key nobody can press. */
	@Override
	protected String sanitize(String value) {
		return isWellFormed(value) ? value : UNBOUND;
	}

	@Override
	public JsonElement write() {
		return new JsonPrimitive(get());
	}

	@Override
	public void read(JsonElement json) {
		if (Json.isString(json)) {
			set(json.getAsString());
		} else if (Json.isNumber(json)) {
			// Written by a version that stored raw GLFW codes; converted the once, on read.
			set(legacyNames.apply(json.getAsInt()));
		} else {
			reset();
		}
	}
}
