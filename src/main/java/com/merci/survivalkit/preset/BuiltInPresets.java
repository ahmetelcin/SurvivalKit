package com.merci.survivalkit.preset;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The presets that come with the mod, per module, in the order the list shows them. Each sets
 * only what it is about; a bridge and a wide road also put the ramp back to off, since a bridge
 * applied after the ramp would otherwise still climb.
 */
public final class BuiltInPresets {
	private record Spec(String id, String values) {
	}

	private static final Map<String, List<Spec>> SPECS = new LinkedHashMap<>();

	static {
		SPECS.put("scaffold", List.of(
			new Spec("bridge", "{\"width\": 1, \"y_lock\": \"start\", \"safe_walk\": \"at_edge\", \"ramp\": \"off\"}"),
			new Spec("ramp", "{\"ramp\": \"up\", \"width\": 1}"),
			new Spec("wide_road", "{\"width\": 3, \"y_lock\": \"start\", \"torches\": true, \"ramp\": \"off\"}")));
		SPECS.put("autoweapon", List.of(
			new Spec("aim_only", "{\"source\": \"aim_only\"}"),
			new Spec("crowd", "{\"source\": \"aim_then_crowd\"}")));
		SPECS.put("autoeat", List.of(
			new Spec("frugal", "{\"threshold_mode\": \"auto\", \"fallback\": \"lowest_value_first\"}"),
			new Spec("always_fed", "{\"threshold_mode\": \"fixed\", \"threshold\": 19, \"eat_early_when_hurt\": true}")));
	}

	private BuiltInPresets() {
	}

	/** A module's built-in presets, named in the language in use now. */
	public static List<Preset> of(String moduleId) {
		return SPECS.getOrDefault(moduleId, List.of()).stream()
			.map(spec -> new Preset(spec.id(), Lang.of(Keys.preset(moduleId, spec.id())), true, parse(spec.values())))
			.toList();
	}

	/** The language keys of a module's built-ins, for the check that every word is translated. */
	public static List<String> keys(String moduleId) {
		return SPECS.getOrDefault(moduleId, List.of()).stream().map(spec -> Keys.preset(moduleId, spec.id())).toList();
	}

	/** Every module that has any. */
	public static List<String> modules() {
		return List.copyOf(SPECS.keySet());
	}

	private static JsonObject parse(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}
}
