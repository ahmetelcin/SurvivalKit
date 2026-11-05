package com.merci.survivalkit.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An ordered set of namespaced ids ("minecraft:overworld").
 *
 * <p>Ids are kept as text and are never looked up in a registry, here or on the way to and
 * from disk. That is deliberate: a list written while a mod was installed keeps working
 * after the mod is gone, and an id this build has never heard of survives a save and load
 * untouched. Turning an id into an item is done where it is used, at the moment it is used.
 */
public final class RegistryListSetting extends Setting<List<String>> {
	/**
	 * What the menu's picker should offer for this list. Named here rather than resolved
	 * here: this class still never touches a registry, it only says which one is meant.
	 */
	public enum Kind {
		/** Anything in the item registry. */
		ITEM,
		/** Items that can be eaten. */
		FOOD,
		/** Dimensions. */
		DIMENSION,
		/** Blocks that exist as an item too, so each has a name and an icon to show. */
		BLOCK
	}

	private final Kind kind;

	public RegistryListSetting(String id, String key, List<String> defaultValue) {
		this(id, key, defaultValue, Kind.ITEM);
	}

	public RegistryListSetting(String id, String key, List<String> defaultValue, Kind kind) {
		super(id, key, List.copyOf(defaultValue));
		this.kind = Objects.requireNonNull(kind, "kind");
	}

	public Kind kind() {
		return kind;
	}

	/**
	 * Moves one entry to another position, shifting the rest along. Out-of-range positions
	 * are clamped rather than refused, because this is driven by a dragging mouse and a
	 * finger that overshoots the end of the list means the end of the list.
	 */
	public void move(int from, int to) {
		List<String> current = get();

		if (current.isEmpty()) {
			return;
		}

		int source = Math.clamp(from, 0, current.size() - 1);
		int target = Math.clamp(to, 0, current.size() - 1);

		if (source == target) {
			return;
		}

		List<String> next = new ArrayList<>(current);
		next.add(target, next.remove(source));
		set(next);
	}

	private static boolean isWellFormed(String namespacedId) {
		int colon = namespacedId.indexOf(':');
		return colon > 0 && colon < namespacedId.length() - 1;
	}

	public void add(String namespacedId) {
		if (!isWellFormed(namespacedId)) {
			throw new IllegalArgumentException("Geçersiz id, 'ad_alanı:yol' bekleniyor: " + namespacedId);
		}

		if (contains(namespacedId)) {
			return;
		}

		List<String> next = new ArrayList<>(get());
		next.add(namespacedId);
		set(next);
	}

	public void remove(String namespacedId) {
		List<String> next = new ArrayList<>(get());

		if (next.remove(namespacedId)) {
			set(next);
		}
	}

	public boolean contains(String namespacedId) {
		return get().contains(namespacedId);
	}

	@Override
	protected List<String> sanitize(List<String> value) {
		return List.copyOf(value);
	}

	@Override
	public JsonElement write() {
		JsonArray array = new JsonArray();
		get().forEach(array::add);
		return array;
	}

	@Override
	public void read(JsonElement json) {
		if (json == null || !json.isJsonArray()) {
			reset();
			return;
		}

		List<String> parsed = new ArrayList<>();

		for (JsonElement element : json.getAsJsonArray()) {
			if (Json.isString(element) && isWellFormed(element.getAsString()) && !parsed.contains(element.getAsString())) {
				parsed.add(element.getAsString());
			}
		}

		set(parsed);
	}
}
