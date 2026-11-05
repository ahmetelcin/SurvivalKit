package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import com.merci.survivalkit.settings.TextSetting;
import com.merci.survivalkit.wdl.AutoSeedQuerier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * GameTest verifying that upon connecting to a world with /seed permission,
 * the client automatically asks for /seed and writes the answer to the wdl.seed setting.
 */
public class AutoSeedGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		AutoSeedQuerier.allowSingleplayerForTesting(true);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(10);

			WorldDownloaderModule module = module();
			TextSetting seedSetting = module.settings().<TextSetting>byId("seed").orElseThrow();

			long expectedSeed = world.getServer().computeOnServer(server -> server.overworld().getSeed());

			// Wait up to 60 ticks for the command tree and /seed response
			for (int i = 0; i < 60; i++) {
				String currentSeed = context.computeOnClient(client -> seedSetting.get());
				if (String.valueOf(expectedSeed).equals(currentSeed)) {
					break;
				}
				context.waitTicks(1);
			}

			boolean queried = context.computeOnClient(client -> module.seedQuerier().hasQueried());
			if (!queried) {
				throw new AssertionError("Seed querier komut ağacını sorgulamadı");
			}

			String finalSeed = context.computeOnClient(client -> seedSetting.get());
			if (!String.valueOf(expectedSeed).equals(finalSeed)) {
				throw new AssertionError("Otomatik seed sorgulanıp ayara yazılamadı: beklenen="
					+ expectedSeed + ", ayardaki=" + finalSeed);
			}

			// Verify that client command tree indeed has seed permission
			boolean hasPerm = context.computeOnClient(client ->
				AutoSeedQuerier.hasSeedPermission(client.getConnection().getCommands()));
			if (!hasPerm) {
				throw new AssertionError("Beklenen: sunucuda seed yetkisi var, fakat hasSeedPermission false döndü");
			}

			// Clean up setting after test
			context.runOnClient(client -> seedSetting.set(""));
			SurvivalKit.LOGGER.info("[gametest] Otomatik seed başarıyla doğrulandı: {}", finalSeed);
		} finally {
			AutoSeedQuerier.allowSingleplayerForTesting(false);
		}
	}

	private static WorldDownloaderModule module() {
		return (WorldDownloaderModule) SurvivalKit.modules()
			.byId("wdl")
			.orElseThrow(() -> new AssertionError("wdl modülü kayıtlı değil"));
	}
}
