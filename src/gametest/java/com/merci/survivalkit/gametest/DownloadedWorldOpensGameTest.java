package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import com.merci.survivalkit.settings.TextSetting;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The end of the WDL promise: a downloaded world opens in singleplayer, and what was
 * downloaded is what you get.
 *
 * <p>{@link WorldDownloaderGameTest} reads the written files with vanilla's own parser, which
 * proves they are well formed. This goes one further and hands them to the game. The
 * interesting failure is not a crash but a quiet one: the game opening the folder, deciding
 * the chunks are not there, and generating fresh terrain over them. So the test marks a block
 * the generator would never place and looks for it after the reopen — gold at that spot means
 * the save was read, anything else means it was regenerated.
 *
 * <p>Furthermore, opened containers (single chest, double chest 27+27) must have their
 * contents preserved and populated when reopened.
 */
public class DownloadedWorldOpensGameTest implements FabricClientGameTest {
	private static final long SEED = 987654321098L;
	private static final BlockPos MARKER = new BlockPos(8, 80, 8);
	private static final BlockPos CHEST_POS = new BlockPos(8, 80, 10);
	private static final BlockPos DOUBLE_CHEST_LEFT = new BlockPos(8, 80, 12);
	private static final BlockPos DOUBLE_CHEST_RIGHT = new BlockPos(9, 80, 12);

	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		Path downloaded;

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			save = world.getWorldSave();
			downloaded = downloadAWorld(context, world);
		}

		// The save the framework knows how to open is made to hold the downloaded world, so
		// the reopen goes through the supported path rather than a hand-rolled one.
		replaceContents(save.getSaveDirectory(), downloaded);

		try (TestSingleplayerContext reopened = save.open()) {
			assertTheDownloadSurvivedTheReopen(reopened);
		}

		SurvivalKit.LOGGER.info("[gametest] indirilen dünya singleplayer'da açıldı, sandıklar ve chunk'lar korundu -> {}",
				downloaded.getFileName());
	}

	private static Path downloadAWorld(ClientGameTestContext context, TestSingleplayerContext world) {
		context.waitTicks(5);

		// Place marker and chests
		world.getServer().runCommand("setblock " + MARKER.getX() + " " + MARKER.getY() + " " + MARKER.getZ() + " minecraft:gold_block");
		world.getServer().runCommand("setblock " + CHEST_POS.getX() + " " + CHEST_POS.getY() + " " + CHEST_POS.getZ() + " minecraft:chest[facing=north]");
		world.getServer().runCommand("item replace block " + CHEST_POS.getX() + " " + CHEST_POS.getY() + " " + CHEST_POS.getZ() + " container.0 with minecraft:diamond 64");

		world.getServer().runCommand("setblock " + DOUBLE_CHEST_RIGHT.getX() + " " + DOUBLE_CHEST_RIGHT.getY() + " " + DOUBLE_CHEST_RIGHT.getZ() + " minecraft:chest[facing=north,type=right]");
		world.getServer().runCommand("setblock " + DOUBLE_CHEST_LEFT.getX() + " " + DOUBLE_CHEST_LEFT.getY() + " " + DOUBLE_CHEST_LEFT.getZ() + " minecraft:chest[facing=north,type=left]");
		world.getServer().runCommand("item replace block " + DOUBLE_CHEST_RIGHT.getX() + " " + DOUBLE_CHEST_RIGHT.getY() + " " + DOUBLE_CHEST_RIGHT.getZ() + " container.0 with minecraft:emerald 32");
		world.getServer().runCommand("item replace block " + DOUBLE_CHEST_LEFT.getX() + " " + DOUBLE_CHEST_LEFT.getY() + " " + DOUBLE_CHEST_LEFT.getZ() + " container.0 with minecraft:netherite_ingot 16");

		world.getServer().runCommand("tp @a 8.5 81.0 11.0 0 0");
		context.waitTicks(20);

		boolean seen = context.computeOnClient(client -> client.level.getBlockState(MARKER).is(Blocks.GOLD_BLOCK));
		if (!seen) {
			throw new AssertionError("hazırlık başarısız: işaret bloğu client'a ulaşmadı");
		}

		context.runOnClient(client -> module().settings().<TextSetting>byId("seed").orElseThrow().set(String.valueOf(SEED)));
		setEnabled(context, true);
		context.waitTicks(5);

		// Open and close single chest to capture its contents
		openAndCloseContainer(context, CHEST_POS);

		// Open and close double chest to capture both halves
		openAndCloseContainer(context, DOUBLE_CHEST_RIGHT);

		setEnabled(context, false);
		context.waitTicks(5);

		return context.computeOnClient(client -> module().lastSavePath()
				.orElseThrow(() -> new AssertionError("indirme klasörü oluşmadı")));
	}

	private static void openAndCloseContainer(ClientGameTestContext context, BlockPos pos) {
		context.runOnClient(client -> {
			BlockHitResult hit = new BlockHitResult(
					new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
					Direction.UP,
					pos,
					false
			);
			client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
		});
		context.waitTicks(10);

		boolean opened = context.computeOnClient(client -> client.gui.screen() instanceof AbstractContainerScreen<?>);
		if (!opened) {
			throw new AssertionError("hazırlık başarısız: konteyner ekranı açılmadı: " + pos);
		}

		context.runOnClient(client -> client.gui.setScreen(null));
		context.waitTicks(5);
	}

	private static void assertTheDownloadSurvivedTheReopen(TestSingleplayerContext reopened) {
		BlockState state = reopened.getServer().computeOnServer(server ->
				server.getLevel(Level.OVERWORLD).getBlockState(MARKER));

		if (!state.is(Blocks.GOLD_BLOCK)) {
			throw new AssertionError("yeniden açılan dünyada işaret bloğu yok, bulunan: " + state
					+ " — chunk diskten okunmak yerine yeniden üretilmiş");
		}

		BlockState above = reopened.getServer().computeOnServer(server ->
				server.getLevel(Level.OVERWORLD).getBlockState(MARKER.above()));

		if (!above.isAir()) {
			throw new AssertionError("işaretin üstü boş olmalıydı, bulunan: " + above
					+ " — bölüm yeniden üretilmiş olabilir");
		}

		// Verify single chest
		reopened.getServer().runOnServer(server -> {
			BlockEntity be = server.getLevel(Level.OVERWORLD).getBlockEntity(CHEST_POS);
			if (!(be instanceof ChestBlockEntity chest)) {
				throw new AssertionError("yeniden açılan dünyada tekli sandık bulunamadı: " + CHEST_POS);
			}

			ItemStack diamond = chest.getItem(0);
			if (!diamond.is(Items.DIAMOND) || diamond.getCount() != 64) {
				throw new AssertionError("tekli sandıkta 64 elmas bekleniyordu, bulunan: " + diamond);
			}

			// Verify double chest right half
			BlockEntity rightBe = server.getLevel(Level.OVERWORLD).getBlockEntity(DOUBLE_CHEST_RIGHT);
			if (!(rightBe instanceof ChestBlockEntity rightChest)) {
				throw new AssertionError("yeniden açılan dünyada sağ sandık bulunamadı: " + DOUBLE_CHEST_RIGHT);
			}

			ItemStack emerald = rightChest.getItem(0);
			if (!emerald.is(Items.EMERALD) || emerald.getCount() != 32) {
				throw new AssertionError("sağ sandıkta 32 zümrüt bekleniyordu, bulunan: " + emerald);
			}

			// Verify double chest left half
			BlockEntity leftBe = server.getLevel(Level.OVERWORLD).getBlockEntity(DOUBLE_CHEST_LEFT);
			if (!(leftBe instanceof ChestBlockEntity leftChest)) {
				throw new AssertionError("yeniden açılan dünyada sol sandık bulunamadı: " + DOUBLE_CHEST_LEFT);
			}

			ItemStack netherite = leftChest.getItem(0);
			if (!netherite.is(Items.NETHERITE_INGOT) || netherite.getCount() != 16) {
				throw new AssertionError("sol sandıkta 16 netherite külçesi bekleniyordu, bulunan: " + netherite);
			}
		});
	}

	/** Empties the target save and copies the downloaded world into it, file for file. */
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
