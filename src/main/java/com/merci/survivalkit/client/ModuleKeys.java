package com.merci.survivalkit.client;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.module.TogglePoller;
import com.merci.survivalkit.settings.KeybindSetting;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.sdl.SDLMouse;

/**
 * Reads module toggle keys straight off the keyboard and mouse, so a module's own
 * {@link KeybindSetting} is the only place its key lives.
 *
 * <p>Nothing here is registered with the game's Controls screen: until there is a menu,
 * a key is changed by editing the config. The one entry Controls will get is the key that
 * opens that menu, and it belongs to the menu, not to a module.
 *
 * <p>Because these keys are read rather than claimed, the game does not know about them
 * and cannot warn about a clash. A key vanilla already uses will do both things at once,
 * which is why a module with no obviously free key ships unbound.
 */
public final class ModuleKeys implements TogglePoller {
	private final Map<Module, Binding> bindings = new HashMap<>();

	public void bind(Module module) {
		bindings.put(module, new Binding(module.keybind()));
	}

	/**
	 * Samples every bound key once per tick. This runs with a screen open too, on purpose:
	 * a key held down as a screen closes is then already "down" and cannot be mistaken for
	 * a fresh press.
	 */
	public void poll(boolean screenOpen) {
		bindings.values().forEach(binding -> binding.poll(screenOpen));
	}

	@Override
	public boolean consumeToggle(Module module) {
		Binding binding = bindings.get(module);
		return binding != null && binding.consumePress();
	}

	/**
	 * Whether a key is held right now, read straight off the keyboard or mouse. Public for the
	 * odd module with a second key of its own (Damar Kırıcı's trigger), so every key in the mod
	 * is read the same way.
	 */
	public static boolean isDown(InputConstants.Key key) {
		return switch (key.getType()) {
			case KEYBOARD -> !key.equals(InputConstants.UNKNOWN) && InputConstants.isKeyDown(key.getValue());
			// SDL keeps the mouse as a bitmask: button n is bit n - 1, set while it is held.
			case MOUSE -> key.getValue() >= InputConstants.MOUSE_BUTTON_LEFT
				&& (SDLMouse.SDL_GetMouseState((FloatBuffer) null, null) & 1 << (key.getValue() - 1)) != 0;
		};
	}

	/** A saved key name as a key; a name this build does not know reads as no key. */
	public static InputConstants.Key parse(String name) {
		try {
			return InputConstants.getKey(name);
		} catch (IllegalArgumentException e) {
			SurvivalKit.LOGGER.warn("Tanınmayan tuş adı, atanmamış sayılıyor: {}", name);
			return InputConstants.UNKNOWN;
		}
	}

	/** One module's key: the name it is saved under, and the edge detection around it. */
	private static final class Binding {
		private final KeybindSetting setting;
		private String resolvedFrom;
		private InputConstants.Key resolved = InputConstants.UNKNOWN;
		private boolean wasDown;
		private boolean pressed;

		private Binding(KeybindSetting setting) {
			this.setting = setting;
		}

		private void poll(boolean screenOpen) {
			boolean down = ModuleKeys.isDown(key());

			if (down && !wasDown && !screenOpen) {
				pressed = true;
			}

			wasDown = down;
		}

		/** Parses the name only when it changes, so a tick costs a string comparison. */
		private InputConstants.Key key() {
			String name = setting.get();

			if (!name.equals(resolvedFrom)) {
				resolvedFrom = name;
				resolved = parse(name);
			}

			return resolved;
		}

		private boolean consumePress() {
			boolean was = pressed;
			pressed = false;
			return was;
		}
	}
}
