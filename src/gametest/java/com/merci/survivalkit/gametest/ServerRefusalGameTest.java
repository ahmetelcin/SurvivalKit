package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.wdl.WdlServerChannel;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

/**
 * A server that forbids downloading, over the old World Downloader mod's channel, is listened to:
 * the download does not start, one already running stops, and the player is told each time.
 *
 * <p>The server here is the singleplayer one, speaking the protocol the way the companion plugin
 * does: it listens for the client's hello on {@code wdl:init} and answers on {@code wdl:control}.
 */
public class ServerRefusalGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		AtomicReference<String> hello = new AtomicReference<>();
		ServerPlayNetworking.registerGlobalReceiver(WdlServerChannel.Init.TYPE,
				(payload, sender) -> hello.set(new String(payload.bytes(), StandardCharsets.UTF_8)));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(20);

			if (hello.get() == null || !hello.get().contains("\"State\":\"Init?\"")) {
				throw new AssertionError("istemci wdl:init üzerinden selam vermeliydi, gelen: " + hello.get());
			}

			// Nothing said yet: downloading goes ahead.
			WdlTestSupport.setEnabled(context, true);
			requireEnabled(context, true, "sunucu bir şey demeden indirme başlamalıydı");
			WdlTestSupport.setEnabled(context, false);

			// Forbidden before it starts.
			answer(context, world, permissions(false));
			long before = logSize();
			WdlTestSupport.setEnabled(context, true);
			requireEnabled(context, false, "sunucu yasakladıktan sonra indirme başlamamalıydı");

			if (context.computeOnClient(client -> WdlTestSupport.module().session().isPresent())) {
				throw new AssertionError("yasakken indirme oturumu açılmamalıydı");
			}

			requireTold(context, before, 1);

			// Allowed again, then forbidden while it runs.
			answer(context, world, permissions(true));
			WdlTestSupport.setEnabled(context, true);
			requireEnabled(context, true, "izin geri gelince indirme başlamalıydı");

			before = logSize();
			answer(context, world, permissions(false));
			requireEnabled(context, false, "indirme sürerken gelen yasak onu durdurmalıydı");
			requireTold(context, before, 1);
		} finally {
			ServerPlayNetworking.unregisterGlobalReceiver(WdlServerChannel.Init.TYPE.id());
		}

		SurvivalKit.LOGGER.info("[gametest] sunucu reddi: selam gitti, yasakta indirme başlamadı, sürerken gelen yasak durdurdu, "
				+ "oyuncuya iki kez söylendi");
	}

	/** Section 1 of the control channel, laid out the way the companion plugin writes it. */
	private static byte[] permissions(boolean canDownload) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bytes);
			out.writeInt(1);
			out.writeBoolean(canDownload);
			out.writeInt(-1);
			out.writeBoolean(true);
			out.writeBoolean(true);
			out.writeBoolean(true);
			out.writeBoolean(true);
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static void answer(ClientGameTestContext context, TestSingleplayerContext world, byte[] packet) {
		world.getServer().runOnServer(server -> ServerPlayNetworking.send(server.getPlayerList().getPlayers().getFirst(),
				new WdlServerChannel.Control(packet)));
		context.waitTicks(5);
	}

	private static void requireEnabled(ClientGameTestContext context, boolean wanted, String message) {
		context.waitTicks(3);

		if (context.computeOnClient(client -> WdlTestSupport.module().isEnabled()) != wanted) {
			throw new AssertionError(message);
		}
	}

	/** The refusal has to reach the chat, which the client writes to its log. */
	private static void requireTold(ClientGameTestContext context, long from, int times) {
		String text = context.computeOnClient(client -> Component.translatable("survivalkit.wdl.forbidden").getString());
		long lines = chatLines(from, text);

		if (lines != times) {
			throw new AssertionError("oyuncuya " + times + " kez söylenmeliydi (\"" + text + "\"), log'da " + lines);
		}
	}

	private static Path latestLog() {
		return FabricLoader.getInstance().getGameDir().resolve("logs/latest.log");
	}

	private static long logSize() {
		try {
			return Files.size(latestLog());
		} catch (IOException e) {
			throw new AssertionError("latest.log okunamadı", e);
		}
	}

	private static long chatLines(long from, String text) {
		try (InputStream in = Files.newInputStream(latestLog())) {
			in.skipNBytes(from);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
					.filter(line -> line.contains("[CHAT]") && line.contains(text))
					.count();
		} catch (IOException e) {
			throw new AssertionError("latest.log okunamadı", e);
		}
	}
}
