package com.merci.survivalkit.client;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.menu.KeyUse;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.settings.SettingNode;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Everything currently listening for a key, gathered from the two places they live.
 *
 * <p>Vanilla's own bindings come from the options, and the menu key is among them since it is
 * registered like any other. The module keys come from this mod, which polls them and so is
 * invisible to the game — those are the ones that can clash silently, which is why the menu
 * warns about the rest.
 *
 * <p>Read fresh each time the menu draws, because the player may have rebound something in
 * the game's own Controls screen since it opened.
 */
public final class KeyRegistry {
	private KeyRegistry() {
	}

	public static List<KeyUse> current(Minecraft client) {
		List<KeyUse> uses = new ArrayList<>();

		for (KeyMapping mapping : client.options.keyMappings) {
			uses.add(new KeyUse(
					"vanilla:" + mapping.getName(),
					// Translated here, so the warning speaks the player's language.
					Component.translatable(mapping.getName()).getString(),
					KeyMappingHelper.getBoundKeyOf(mapping).getName()));
		}

		for (Module module : SurvivalKit.modules().modules()) {
			uses.add(new KeyUse(module.id(), module.displayName(), module.keybind().get()));
			others(module, module.settings(), uses);
		}

		return List.copyOf(uses);
	}

	/** A module's keys besides its on/off key, at any depth, named "Module — setting". */
	private static void others(Module module, SettingGroup group, List<KeyUse> uses) {
		for (SettingNode child : group.children()) {
			if (child instanceof SettingGroup nested) {
				others(module, nested, uses);
			} else if (child instanceof KeybindSetting keybind && keybind != module.keybind()) {
				uses.add(new KeyUse(KeyUse.ownerOf(module, keybind), module.displayName() + " — " + keybind.displayName(), keybind.get()));
			}
		}
	}
}
