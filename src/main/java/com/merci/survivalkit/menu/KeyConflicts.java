package com.merci.survivalkit.menu;

import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Who else is already listening for a key.
 *
 * <p>Worth warning about because this mod reads its module keys straight off the keyboard
 * rather than registering them: the game does not know they exist and will happily fire its
 * own action at the same time. Binding a module to L would open the advancements screen on
 * every press, which looks like the module is broken.
 *
 * <p>The list of who uses what is handed in, so the rule can be tried without a game.
 */
public final class KeyConflicts {
	private KeyConflicts() {
	}

	/**
	 * The first other thing using this key, if any.
	 *
	 * @param ownerId the binding being edited, so it is not reported as clashing with itself
	 */
	public static Optional<String> firstFor(String keyName, String ownerId, List<KeyUse> uses) {
		Objects.requireNonNull(ownerId, "ownerId");
		Objects.requireNonNull(uses, "uses");

		// An unbound setting clashes with nothing, however many other things are unbound.
		if (keyName == null || keyName.isBlank() || KeybindSetting.UNBOUND.equals(keyName)) {
			return Optional.empty();
		}

		return uses.stream()
			.filter(use -> !use.ownerId().equals(ownerId))
			.filter(use -> use.keyName().equals(keyName))
			.map(KeyUse::actionName)
			.findFirst();
	}

	/** The warning as it appears under the control, or empty when the key is free. */
	public static Optional<String> warningFor(String keyName, String ownerId, List<KeyUse> uses) {
		return firstFor(keyName, ownerId, uses).map(action -> Lang.of(Keys.MENU_CONFLICT, action));
	}
}
