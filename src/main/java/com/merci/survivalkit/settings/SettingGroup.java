package com.merci.survivalkit.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * An ordered, named collection of settings. Groups nest.
 *
 * <p>A nested group is normally a level of its own in the file. A {@linkplain #section section}
 * is not: it is a heading in the menu only, and its settings are written and read as if they
 * sat directly in the group above. That is what lets settings be gathered under headings
 * without moving them in the file, where a moved key would read as missing and reset.
 */
public final class SettingGroup implements SettingNode {
	private final String id;
	/** Null for a module's root group, which is a container and never drawn. */
	private final String key;
	private final boolean section;
	/** Set once a section is added somewhere; the level whose ids it shares. */
	private SettingGroup parent;
	private final List<SettingNode> children = new ArrayList<>();
	private final List<SettingNode> childrenView = Collections.unmodifiableList(children);
	private final Map<String, SettingNode> index = new HashMap<>();
	private BooleanSupplier visibleWhen = () -> true;

	/** A container with no heading of its own: what a module holds its settings in. */
	public SettingGroup(String id) {
		this(id, null);
	}

	/**
	 * @param key what this heading is called in the language files, without the mod's prefix,
	 *            or null for a group that is never drawn.
	 */
	public SettingGroup(String id, String key) {
		this(id, key, false);
	}

	/**
	 * A heading that is not a level in the file: its settings are stored beside the ones in
	 * the group it is added to, under their own ids, exactly as if the heading were not there.
	 */
	public static SettingGroup section(String id, String key) {
		return new SettingGroup(id, Objects.requireNonNull(key, "key"), true);
	}

	private SettingGroup(String id, String key, boolean section) {
		Objects.requireNonNull(id, "id");

		if (id.isBlank()) {
			throw new IllegalArgumentException("Grup id'si boş olamaz");
		}

		this.id = id;
		this.key = key;
		this.section = section;
	}

	/** Adds a child and hands it back, so callers can keep a typed reference. */
	public <T extends SettingNode> T add(T node) {
		Objects.requireNonNull(node, "node");

		// Everything that ends up side by side in the file needs an id of its own, sections
		// included, so the check is made at the level that is actually written.
		SettingGroup level = storedLevel();
		rejectTaken(level, node.id());

		if (node instanceof SettingGroup group && group.section) {
			for (SettingNode inside : group.children) {
				rejectTaken(level, inside.id());
			}

			group.parent = this;
		}

		index.put(node.id(), node);
		children.add(node);
		return node;
	}

	private static void rejectTaken(SettingGroup level, String id) {
		if (level.find(id) != null) {
			throw new IllegalArgumentException("Bu ayar id'si zaten kayıtlı: " + id);
		}
	}

	/** True for a heading whose settings are stored in the group above it. */
	public boolean isSection() {
		return section;
	}

	/** The group whose level in the file this one writes into: itself, unless it is a section. */
	private SettingGroup storedLevel() {
		return section && parent != null ? parent.storedLevel() : this;
	}

	/** Looks inside sections too, since their settings share this level. */
	private SettingNode find(String id) {
		SettingNode found = index.get(id);

		for (int i = 0; found == null && i < children.size(); i++) {
			if (children.get(i) instanceof SettingGroup group && group.section) {
				found = group.find(id);
			}
		}

		return found;
	}

	/** Addition order, read-only. */
	public List<SettingNode> children() {
		return childrenView;
	}

	/** Children whose visibility rule currently passes. */
	public List<SettingNode> visibleChildren() {
		return children.stream().filter(SettingNode::isVisible).toList();
	}

	@SuppressWarnings("unchecked")
	public <T extends SettingNode> Optional<T> byId(String id) {
		return Optional.ofNullable((T) find(id));
	}

	public void setVisibleWhen(BooleanSupplier condition) {
		this.visibleWhen = Objects.requireNonNull(condition, "condition");
	}

	@Override
	public String id() {
		return id;
	}

	/** The short translation key, or null for a group that is never drawn. */
	public String key() {
		return key;
	}

	@Override
	public String displayName() {
		return key == null ? "" : Lang.of(Keys.setting(key));
	}

	/** Groups are headings and explain themselves by name; the audit exempts them. */
	@Override
	public String tooltip() {
		return "";
	}

	@Override
	public boolean isVisible() {
		return visibleWhen.getAsBoolean();
	}

	@Override
	public void reset() {
		children.forEach(SettingNode::reset);
	}

	@Override
	public JsonElement write() {
		JsonObject object = new JsonObject();
		writeInto(object);
		return object;
	}

	private void writeInto(JsonObject object) {
		for (SettingNode child : children) {
			if (child instanceof SettingGroup group && group.section) {
				group.writeInto(object);
			} else {
				object.add(child.id(), child.write());
			}
		}
	}

	/** A child missing from the file keeps its current value; anything unreadable resets. */
	@Override
	public void read(JsonElement json) {
		if (json == null || !json.isJsonObject()) {
			reset();
			return;
		}

		JsonObject object = json.getAsJsonObject();

		for (SettingNode child : children) {
			if (child instanceof SettingGroup group && group.section) {
				// Its settings are right here, beside everything else at this level.
				group.read(object);
			} else if (object.has(child.id())) {
				child.read(object.get(child.id()));
			}
		}
	}
}
