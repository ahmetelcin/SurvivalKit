package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import com.merci.survivalkit.settings.TextSetting;
import com.merci.survivalkit.wdl.DownloadSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Proves that visiting multiple dimensions (Overworld and Nether) correctly routes
 * chunk writes to their respective dimension folders, closes the previous dimension's
 * writer upon dimension transition, and preserves both dimensions when reopened.
 */
public class DimensionChangeGameTest implements FabricClientGameTest {
	private static final long SEED = 1357924680L;
	private static final BlockPos OVERWORLD_MARKER = new BlockPos(8, 80, 8);
	private static final BlockPos NETHER_MARKER = new BlockPos(8, 80, 8);

	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		Path downloaded;

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			save = world.getWorldSave();
			downloaded = downloadAcrossDimensions(context, world);
		}

		replaceContents(save.getSaveDirectory(), downloaded);

		try (TestSingleplayerContext reopened = save.open()) {
			assertBothDimensionsSurvivedTheReopen(reopened);
		}

		SurvivalKit.LOGGER.info("[gametest] iki boyutta gezilen dünya tek oyunculuda başarıyla açıldı -> {}",
				downloaded.getFileName());
	}

	private static Path downloadAcrossDimensions(ClientGameTestContext context, TestSingleplayerContext world) {
		context.waitTicks(5);

		// Place Overworld marker and move player nearby
		world.getServer().runCommand("setblock " + OVERWORLD_MARKER.getX() + " " + OVERWORLD_MARKER.getY() + " " + OVERWORLD_MARKER.getZ() + " minecraft:gold_block");
		world.getServer().runCommand("tp @a 8.5 81.0 8.5 0 0");
		context.waitTicks(20);

		boolean overworldSeen = context.computeOnClient(client ->
				client.level != null && client.level.getBlockState(OVERWORLD_MARKER).is(Blocks.GOLD_BLOCK));
		if (!overworldSeen) {
			throw new AssertionError("hazırlık başarısız: Overworld işaret bloğu client'a ulaşmadı");
		}

		// Configure and start WDL
		context.runOnClient(client -> module().settings().<TextSetting>byId("seed").orElseThrow().set(String.valueOf(SEED)));
		setEnabled(context, true);
		context.waitTicks(5);

		// Teleport player into Nether
		world.getServer().runCommand("execute in minecraft:the_nether run tp @a 8.5 81.0 8.5 0 0");

		// Wait for client to switch to Nether
		context.waitFor(client -> client.level != null && Level.NETHER.equals(client.level.dimension()));
		context.waitTicks(10);

		// Place Nether marker now that the chunk is loaded
		world.getServer().runCommand("execute in minecraft:the_nether run setblock "
				+ NETHER_MARKER.getX() + " " + NETHER_MARKER.getY() + " " + NETHER_MARKER.getZ() + " minecraft:diamond_block");
		context.waitTicks(15);

		boolean netherSeen = context.computeOnClient(client ->
				client.level != null && client.level.getBlockState(NETHER_MARKER).is(Blocks.DIAMOND_BLOCK));
		if (!netherSeen) {
			throw new AssertionError("hazırlık başarısız: Nether işaret bloğu client'a ulaşmadı");
		}

		// Verify that active session switched to Nether and old Overworld writer was closed
		context.runOnClient(client -> {
			Optional<DownloadSession> optSession = module().session();
			if (optSession.isEmpty()) {
				throw new AssertionError("aktif download oturumu bulunamadı");
			}
			DownloadSession session = optSession.get();
			if (!Level.NETHER.equals(session.currentDimension())) {
				throw new AssertionError("oturum boyutu Nether olarak güncellenmedi: " + session.currentDimension());
			}
			Set<ResourceKey<Level>> openDims = session.openDimensions();
			if (openDims.contains(Level.OVERWORLD)) {
				throw new AssertionError("eski Overworld yazıcısı kapatılmadı, hâlâ açık: " + openDims);
			}
		});

		// Trigger explicit flush to verify periodic/manual flush works
		context.runOnClient(client -> module().flush());
		context.waitTicks(5);

		setEnabled(context, false);
		context.waitTicks(5);

		return context.computeOnClient(client -> module().lastSavePath()
				.orElseThrow(() -> new AssertionError("indirme klasörü oluşmadı")));
	}

	private static void assertBothDimensionsSurvivedTheReopen(TestSingleplayerContext reopened) {
		BlockState overworldState = reopened.getServer().computeOnServer(server ->
				server.getLevel(Level.OVERWORLD).getBlockState(OVERWORLD_MARKER));

		if (!overworldState.is(Blocks.GOLD_BLOCK)) {
			throw new AssertionError("yeniden açılan dünyada Overworld işaret bloğu yok, bulunan: " + overworldState
					+ " — Overworld chunk'ı kaydedilmemiş veya yeniden üretilmiş");
		}

		BlockState netherState = reopened.getServer().computeOnServer(server ->
				server.getLevel(Level.NETHER).getBlockState(NETHER_MARKER));

		if (!netherState.is(Blocks.DIAMOND_BLOCK)) {
			throw new AssertionError("yeniden açılan dünyada Nether işaret bloğu yok, bulunan: " + netherState
					+ " — Nether chunk'ı kaydedilmemiş veya yeniden üretilmiş");
		}
	}

	private static void replaceContents(Path target, Path source) {
		deleteRecursively(target);

		try (Stream<Path> tree = Files.walk(source)) {
			tree.forEach(from -> {
				Path to = target.resolve(source.relativize(from).toString());

				try {
					if (Files.isDirectory(from)) {
						Files.createDirectories(to);
					} else {
						Files.createDirectories(to.getParent());
						Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					}
				} catch (IOException e) {
					throw new UncheckedIOException("kopyalanamadı: " + from, e);
				}
			});
		} catch (IOException e) {
			throw new AssertionError("indirilen dünya kopyalanamadı: " + source, e);
		}
	}

	private static void deleteRecursively(Path root) {
		if (!Files.exists(root)) {
			return;
		}

		for (int attempt = 0; attempt < 20; attempt++) {
			try (Stream<Path> tree = Files.walk(root)) {
				tree.sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (IOException e) {
						throw new UncheckedIOException("silinemedi: " + path, e);
					}
				});
				if (!Files.exists(root)) {
					return;
				}
			} catch (Exception ignored) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
				}
			}
		}

		if (Files.exists(root)) {
			throw new AssertionError("hedef kayıt temizlenemedi: " + root);
		}
	}

	private static WorldDownloaderModule module() {
		return (WorldDownloaderModule) SurvivalKit.modules()
				.byId("wdl")
				.orElseThrow(() -> new AssertionError("wdl modülü kayıtlı değil"));
	}

	private static void setEnabled(ClientGameTestContext context, boolean enabled) {
		context.runOnClient(client -> module().setEnabled(enabled));
	}
}
