package com.merci.survivalkit.wdl;

import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * Client commands for World Downloader:
 * /wdl start, /wdl stop, /wdl status, /wdl save, /wdl help.
 */
public final class WdlCommands {
	private WdlCommands() {
	}

	public static void register(WorldDownloaderModule module) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			register(dispatcher, module);
		});
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, WorldDownloaderModule module) {
		dispatcher.register(
			ClientCommands.literal("wdl")
				.executes(ctx -> status(ctx, module))
				.then(ClientCommands.literal("start").executes(ctx -> start(ctx, module)))
				.then(ClientCommands.literal("stop").executes(ctx -> stop(ctx, module)))
				.then(ClientCommands.literal("status").executes(ctx -> status(ctx, module)))
				.then(ClientCommands.literal("save").executes(ctx -> save(ctx, module)))
				.then(ClientCommands.literal("help").executes(WdlCommands::help))
		);
	}

	private static int start(CommandContext<FabricClientCommandSource> ctx, WorldDownloaderModule module) {
		FabricClientCommandSource source = ctx.getSource();
		if (module.isEnabled()) {
			source.sendError(Component.translatable("survivalkit.wdl.command.already_running"));
			return 0;
		}

		if (source.getClient() == null || source.getClient().level == null) {
			source.sendError(Component.translatable("survivalkit.wdl.command.not_in_world"));
			return 0;
		}

		module.setEnabled(true);
		if (!module.isEnabled()) {
			source.sendError(Component.translatable("survivalkit.wdl.command.start_failed"));
			return 0;
		}

		source.sendFeedback(Component.translatable("survivalkit.wdl.command.started"));
		return 1;
	}

	private static int stop(CommandContext<FabricClientCommandSource> ctx, WorldDownloaderModule module) {
		FabricClientCommandSource source = ctx.getSource();
		if (!module.isEnabled()) {
			source.sendError(Component.translatable("survivalkit.wdl.command.not_running"));
			return 0;
		}

		String folder = module.session()
			.map(s -> s.levelPath().getFileName().toString())
			.orElse("?");

		module.setEnabled(false);
		int chunks = module.lastSavedChunks();
		source.sendFeedback(Component.translatable("survivalkit.wdl.command.stopped", chunks, folder));
		return 1;
	}

	private static int status(CommandContext<FabricClientCommandSource> ctx, WorldDownloaderModule module) {
		FabricClientCommandSource source = ctx.getSource();
		if (module.isEnabled() && module.session().isPresent()) {
			int chunks = module.session().get().savedChunks();
			String folder = module.session().get().levelPath().getFileName().toString();
			source.sendFeedback(Component.translatable("survivalkit.wdl.command.status.active", chunks, folder));
		} else {
			source.sendFeedback(Component.translatable("survivalkit.wdl.command.status.inactive"));
		}
		return 1;
	}

	private static int save(CommandContext<FabricClientCommandSource> ctx, WorldDownloaderModule module) {
		FabricClientCommandSource source = ctx.getSource();
		if (!module.isEnabled() || module.session().isEmpty()) {
			source.sendError(Component.translatable("survivalkit.wdl.command.not_running"));
			return 0;
		}

		module.flush();
		int chunks = module.session().get().savedChunks();
		source.sendFeedback(Component.translatable("survivalkit.wdl.command.saved", chunks));
		return 1;
	}

	private static int help(CommandContext<FabricClientCommandSource> ctx) {
		ctx.getSource().sendFeedback(Component.translatable("survivalkit.wdl.command.help"));
		return 1;
	}
}
