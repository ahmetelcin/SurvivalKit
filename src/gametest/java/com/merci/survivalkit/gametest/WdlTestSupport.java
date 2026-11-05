package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import com.merci.survivalkit.settings.BoolSetting;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

/**
 * What the World Downloader game tests share: switching the module, and the round trip that
 * every promise of it ends in — download, then open the result in singleplayer and look.
 *
 * <p>The reopen goes through a save the test framework created and knows how to open: its
 * contents are swapped for the downloaded ones, rather than opening the download folder by a
 * hand-rolled path.
 */
final class WdlTestSupport {
	private WdlTestSupport() {
	}

	static WorldDownloaderModule module() {
		return (WorldDownloaderModule) SurvivalKit.modules()
				.byId("wdl")
				.orElseThrow(() -> new AssertionError("wdl modülü kayıtlı değil"));
	}

	static void setEnabled(ClientGameTestContext context, boolean enabled) {
		context.runOnClient(client -> module().setEnabled(enabled));
		context.waitTicks(5);
	}

	static void continueWorld(ClientGameTestContext context, boolean on) {
		context.runOnClient(client -> module().settings().<BoolSetting>byId("continue_world").orElseThrow().set(on));
	}

	/** Where the last download went, once it has been closed. */
	static Path lastSave(ClientGameTestContext context) {
		return context.computeOnClient(client -> module().lastSavePath()
				.orElseThrow(() -> new AssertionError("indirme klasörü oluşmadı")));
	}

	/**
	 * Builds a world, lets {@code download} run in it, then opens what was downloaded in
	 * singleplayer and hands it to {@code check}.
	 */
	static void downloadThenReopen(ClientGameTestContext context, Consumer<TestSingleplayerContext> download,
			Consumer<TestSingleplayerContext> check) {
		TestWorldSave save;

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			save = world.getWorldSave();
			download.accept(world);
		}

		reopen(context, save, lastSave(context), check);
	}

	/** Swaps the save's contents for the download's and opens it. */
	static void reopen(ClientGameTestContext context, TestWorldSave save, Path downloaded,
			Consumer<TestSingleplayerContext> check) {
		replaceContents(save.getSaveDirectory(), downloaded);

		try (TestSingleplayerContext reopened = save.open()) {
			check.accept(reopened);
		}
	}

	static void replaceContents(Path target, Path source) {
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

	/** Retried: the game may still be letting go of a region file for a moment. */
	static void deleteRecursively(Path root) {
		for (int attempt = 0; attempt < 20 && Files.exists(root); attempt++) {
			try (Stream<Path> tree = Files.walk(root)) {
				tree.sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (IOException e) {
						throw new UncheckedIOException("silinemedi: " + path, e);
					}
				});
			} catch (Exception busy) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				}
			}
		}

		if (Files.exists(root)) {
			throw new AssertionError("klasör temizlenemedi: " + root);
		}
	}
}
