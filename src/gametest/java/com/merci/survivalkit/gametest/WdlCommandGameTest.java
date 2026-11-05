package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * End-to-end test verifying that /wdl start, status, save, and stop work from the client chat/command line.
 */
public class WdlCommandGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(10);

			WorldDownloaderModule module = module();
			if (module.isEnabled()) {
				context.runOnClient(client -> module.setEnabled(false));
				context.waitTicks(5);
			}

			// 1. Initially disabled
			if (module.isEnabled()) {
				throw new AssertionError("WDL başlangıçta kapalı olmalı");
			}

			// 2. Run /wdl start command on client
			context.runOnClient(client -> {
				client.player.connection.sendCommand("wdl start");
			});
			context.waitTicks(10);

			// Verify module started
			if (!module.isEnabled()) {
				throw new AssertionError("/wdl start komutu indirmeyi başlatmadı");
			}
			if (module.session().isEmpty()) {
				throw new AssertionError("/wdl start oturumu açmadı");
			}

			// 3. Run /wdl status and /wdl save
			context.runOnClient(client -> {
				client.player.connection.sendCommand("wdl status");
				client.player.connection.sendCommand("wdl save");
			});
			context.waitTicks(10);

			// 4. Run /wdl stop command on client
			context.runOnClient(client -> {
				client.player.connection.sendCommand("wdl stop");
			});
			context.waitTicks(10);

			// Verify module stopped
			if (module.isEnabled()) {
				throw new AssertionError("/wdl stop komutu indirmeyi durdurmadı");
			}

			Path lastPath = module.lastSavePath()
				.orElseThrow(() -> new AssertionError("/wdl stop sonrası kayıt klasörü bulunamadı"));
			if (!Files.exists(lastPath)) {
				throw new AssertionError("Kayıt klasörü diskte oluşmadı: " + lastPath);
			}

			SurvivalKit.LOGGER.info("[gametest] /wdl komutları başarıyla doğrulandı -> {}", lastPath);
		}
	}

	private static WorldDownloaderModule module() {
		return (WorldDownloaderModule) SurvivalKit.modules()
			.byId("wdl")
			.orElseThrow(() -> new AssertionError("wdl modülü kayıtlı değil"));
	}
}
