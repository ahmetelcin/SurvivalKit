package com.merci.survivalkit.settings;

import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** A single named value with a default, a visibility rule and JSON persistence. */
public abstract class Setting<T> implements SettingNode {
	private final String id;
	private final String key;
	private final T defaultValue;
	private T value;
	private BooleanSupplier visibleWhen = () -> true;
	private Supplier<Optional<String>> note = Optional::empty;

	/**
	 * @param key what this setting is called in the language files, without the mod's prefix:
	 *            "autoeat.threshold". The tooltip, and an enum's values, hang off the same
	 *            key, so one line here is all a setting declares about its wording.
	 */
	protected Setting(String id, String key, T defaultValue) {
		Objects.requireNonNull(id, "id");

		if (id.isBlank()) {
			throw new IllegalArgumentException("Ayar id'si boş olamaz");
		}

		this.id = id;
		this.key = Objects.requireNonNull(key, "key");

		if (key.isBlank()) {
			throw new IllegalArgumentException("Ayar çeviri anahtarı boş olamaz: " + id);
		}

		this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
		this.value = this.defaultValue;
	}

	@Override
	public final String id() {
		return id;
	}

	/** The short translation key, as declared. */
	public final String key() {
		return key;
	}

	@Override
	public final String displayName() {
		return Lang.of(Keys.setting(key));
	}

	@Override
	public final String tooltip() {
		return Lang.of(Keys.tooltip(key));
	}

	public final T defaultValue() {
		return defaultValue;
	}

	public final T get() {
		return value;
	}

	public final void set(T value) {
		this.value = sanitize(Objects.requireNonNull(value, "value"));
	}

	/** True while the value is untouched, which is when the menu leaves the reset control off. */
	public final boolean isDefault() {
		return value.equals(defaultValue);
	}

	@Override
	public final void reset() {
		this.value = defaultValue;
	}

	@Override
	public final boolean isVisible() {
		return visibleWhen.getAsBoolean();
	}

	public final void setVisibleWhen(BooleanSupplier condition) {
		this.visibleWhen = Objects.requireNonNull(condition, "condition");
	}

	/** A short line the menu shows under the setting while there is one, already in the player's language. */
	public final Optional<String> note() {
		return note.get();
	}

	public final void setNote(Supplier<Optional<String>> note) {
		this.note = Objects.requireNonNull(note, "note");
	}

	/** Subclasses clamp or trim here; called for every incoming value. */
	protected T sanitize(T value) {
		return value;
	}
}
