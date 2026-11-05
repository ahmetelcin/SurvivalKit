package com.merci.survivalkit.preset;

import com.google.gson.JsonObject;
import java.util.Objects;

/**
 * A named set of values for one module's settings, written the way the settings file writes
 * them — only the settings it sets. A built-in one comes with the mod and cannot be deleted;
 * the player's own are saved from what the module had at the time.
 *
 * @param id     the built-in's id ("bridge"), or the player's name for their own
 * @param name   what the list shows: the built-in's translated name, or the player's name
 */
public record Preset(String id, String name, boolean builtIn, JsonObject values) {
	public Preset {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(values, "values");
	}
}
