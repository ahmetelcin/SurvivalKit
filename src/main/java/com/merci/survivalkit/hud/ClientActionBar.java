package com.merci.survivalkit.hud;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Puts a HUD line on the game's action bar, above the hotbar; a warning in red. */
public final class ClientActionBar {
	private ClientActionBar() {
	}

	public static void say(HudLine line) {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player != null) {
			minecraft.gui.hud.setOverlayMessage(
				Component.literal(line.text()).withStyle(line.warning() ? ChatFormatting.RED : ChatFormatting.WHITE), false);
		}
	}
}
