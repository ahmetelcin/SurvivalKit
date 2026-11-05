package com.merci.survivalkit.wdl;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import com.mojang.brigadier.CommandDispatcher;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Automatically asks the server for /seed upon connection if and only if the command tree
 * indicates the player has permission to execute /seed.
 */
public final class AutoSeedQuerier {
	private static final Pattern SEED_PATTERN = Pattern.compile("(?i)(?:seed|tohum|oluşturucusu\\s+kodu)[^0-9\\-]*(-?\\d+)");
	private static final int RESPONSE_TIMEOUT_TICKS = 100;

	private static boolean allowSingleplayerForTesting = false;

	private final WorldDownloaderModule module;
	private boolean queried;
	private boolean awaitingResponse;
	private int responseTicks;

	public AutoSeedQuerier(WorldDownloaderModule module) {
		this.module = module;

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		ClientReceiveMessageEvents.GAME.register(this::onGameMessage);
	}

	public static void allowSingleplayerForTesting(boolean allow) {
		allowSingleplayerForTesting = allow;
	}

	public void reset() {
		queried = false;
		awaitingResponse = false;
		responseTicks = 0;
	}

	public boolean hasQueried() {
		return queried;
	}

	public boolean isAwaitingResponse() {
		return awaitingResponse;
	}

	public void onTick(Minecraft client) {
		if (awaitingResponse) {
			responseTicks++;
			if (responseTicks > RESPONSE_TIMEOUT_TICKS) {
				awaitingResponse = false;
				responseTicks = 0;
			}
		}

		if (queried || client == null || client.player == null) {
			return;
		}

		if (!module.autoQuerySeed().get()) {
			return;
		}

		// Sadece bir sunucuya bağlanıldığında sorgula (tek oyunculu dünyalarda test dışında sorgulanmaz)
		if (client.isLocalServer() && !allowSingleplayerForTesting) {
			return;
		}

		ClientPacketListener connection = client.getConnection();
		if (connection == null) {
			return;
		}

		CommandDispatcher<ClientSuggestionProvider> commands = connection.getCommands();
		if (commands == null || commands.getRoot().getChildren().isEmpty()) {
			return;
		}

		// Ağaç geldi; bağlantı başına yalnızca bir kez değerlendir
		queried = true;

		if (hasSeedPermission(commands)) {
			awaitingResponse = true;
			responseTicks = 0;
			connection.sendCommand("seed");
			SurvivalKit.LOGGER.info("[wdl] Sunucuda /seed yetkisi tespit edildi, otomatik sorgulanıyor...");
		} else {
			SurvivalKit.LOGGER.info("[wdl] Sunucu komut ağacında /seed yok, yetki bulunmadığı için sorulmadı.");
		}
	}

	public void onGameMessage(Component message, boolean overlay) {
		if (!awaitingResponse || message == null) {
			return;
		}

		parseSeed(message).ifPresent(seedStr -> {
			awaitingResponse = false;
			responseTicks = 0;
			module.setSeed(seedStr);
			SurvivalKit.LOGGER.info("[wdl] Sunucudan seed alındı ve ayara yazıldı: {}", seedStr);
		});
	}

	public static boolean hasSeedPermission(CommandDispatcher<?> commands) {
		return commands != null && commands.getRoot().getChild("seed") != null;
	}

	public static Optional<String> parseSeed(Component message) {
		if (message == null) {
			return Optional.empty();
		}

		// 1. Önce vanilla commands.seed.success çevirisine bak
		Optional<String> vanilla = extractVanillaSeed(message);
		if (vanilla.isPresent()) {
			return vanilla;
		}

		// 2. Pencere içindeyken yedek olarak regex kullan
		String text = message.getString();
		Matcher matcher = SEED_PATTERN.matcher(text);
		if (matcher.find()) {
			return Optional.of(matcher.group(1));
		}

		return Optional.empty();
	}

	private static Optional<String> extractVanillaSeed(Component message) {
		if (message.getContents() instanceof TranslatableContents tc
				&& "commands.seed.success".equals(tc.getKey())
				&& tc.getArgs().length > 0) {
			Object arg = tc.getArgs()[0];
			String seedStr = (arg instanceof Component c) ? c.getString() : String.valueOf(arg);
			seedStr = seedStr.replaceAll("[\\[\\]]", "").trim();
			return Optional.of(seedStr);
		}

		for (Component sibling : message.getSiblings()) {
			Optional<String> fromSibling = extractVanillaSeed(sibling);
			if (fromSibling.isPresent()) {
				return fromSibling;
			}
		}

		return Optional.empty();
	}
}
