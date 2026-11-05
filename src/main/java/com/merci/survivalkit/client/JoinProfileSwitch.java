package com.merci.survivalkit.client;

import com.merci.survivalkit.profile.Profiles;
import com.merci.survivalkit.profile.ServerRules;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

/**
 * On joining a world: what the world is — singleplayer, or the address the player connected to —
 * goes to the profiles, whose rules decide; a switch is told in a toast. Nothing is decided here.
 */
public final class JoinProfileSwitch {
	/** Ours, so a second switch replaces the first toast rather than queueing behind it. */
	public static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId();

	private JoinProfileSwitch() {
	}

	/** @param switched called after a switch, for whatever else holds on to the settings (the menu's ↺) */
	public static void register(Supplier<Profiles> profiles, Runnable switched) {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> joined(client, profiles.get(), switched));
	}

	private static void joined(Minecraft client, Profiles profiles, Runnable switched) {
		String address;

		if (client.hasSingleplayerServer()) {
			address = null;
		} else {
			ServerData server = client.getCurrentServer();

			if (server == null) {
				// Neither singleplayer nor an address: nothing a rule could be about.
				return;
			}

			address = server.ip;
		}

		Optional<ServerRules.Choice> choice = profiles.joined(address);

		choice.ifPresent(chosen -> {
			switched.run();
			List<String> notice = ServerRules.notice(chosen);
			SystemToast.addOrUpdate(client.gui.toastManager(), TOAST, Component.literal(notice.get(0)), Component.literal(notice.get(1)));
		});
	}
}
