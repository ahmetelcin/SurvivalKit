package com.merci.survivalkit.menu;

import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.KeybindSetting;
import java.util.Objects;

/**
 * One thing already listening for a key: a module of this mod, the menu key, or any of
 * vanilla's own bindings.
 *
 * @param ownerId    what holds it — a module id, or a made-up id for a vanilla binding. Used
 *                   only to tell a binding apart from itself, so rebinding a key to the key
 *                   it already has is not reported as a clash with itself.
 * @param actionName what to call it in a warning, already in the player's language
 * @param keyName    vanilla's spelling of the key, or unbound
 */
public record KeyUse(String ownerId, String actionName, String keyName) {
	public KeyUse {
		Objects.requireNonNull(ownerId, "ownerId");
		Objects.requireNonNull(actionName, "actionName");
		Objects.requireNonNull(keyName, "keyName");
	}

	/**
	 * The owner id of one of a module's keys: the module's own id for its on/off key, the
	 * module and the setting together for any other key it has. A module's second key is then
	 * a binding of its own, and clashes with the first like with anything else.
	 */
	public static String ownerOf(Module module, KeybindSetting keybind) {
		return keybind == module.keybind() ? module.id() : module.id() + ":" + keybind.id();
	}
}
