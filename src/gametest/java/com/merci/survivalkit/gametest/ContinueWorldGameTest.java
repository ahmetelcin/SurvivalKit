package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.settings.TextSetting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Two downloads from the same server, of two places far apart, end up as one world with both
 * places in it.
 *
 * <p>The second download also walks back past the first place without opening its chest. The
 * server never sends what is inside a chest, so the chunk it writes again would lose the
 * diamonds the first download saw — unless what that download knew is carried over. And the
 * seed is left blank the second time: not knowing it then must not wipe the one written before.
 */
public class ContinueWorldGameTest implements FabricClientGameTest {
	private static final long SEED = 246813579L;
	private static final BlockPos FIRST = new BlockPos(8, 80, 8);
	private static final BlockPos FIRST_CHEST = new BlockPos(8, 80, 10);
	/** Far enough that nothing of the first place is loaded while the second is downloaded. */
	private static final BlockPos SECOND = new BlockPos(1208, 80, 1208);

	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		Path first;
		Path second;

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			save = world.getWorldSave();
			context.waitTicks(5);
			WdlTestSupport.continueWorld(context, true);
			// No fall on arrival can end the test with a death screen.
			world.getServer().runCommand("gamemode creative @a");

			first = downloadTheFirstPlace(context, world);
			second = downloadTheSecondPlaceAndWalkBackPastTheFirst(context, world);
		}

		if (!first.equals(second)) {
			throw new AssertionError("ikinci indirme aynı klasöre eklenmeliydi: " + first.getFileName() + " / " + second.getFileName());
		}

		List<String> folders = downloadFolders(first.getParent(), first.getFileName().toString());

		if (folders.size() != 1) {
			throw new AssertionError("sunucu için tek klasör olmalıydı, bulunan: " + folders);
		}

		WdlTestSupport.reopen(context, save, first, ContinueWorldGameTest::bothPlacesAreThere);
		context.runOnClient(client -> WdlTestSupport.module().settings().<TextSetting>byId("seed").orElseThrow().set(""));

		SurvivalKit.LOGGER.info("[gametest] aynı dünyaya devam: iki oturum tek klasörde ({}), iki bölge de açılan dünyada, "
				+ "yeniden görülen sandığın içi ve seed korundu", first.getFileName());
	}

	private static Path downloadTheFirstPlace(ClientGameTestContext context, TestSingleplayerContext world) {
		floor(world, FIRST);
		world.getServer().runCommand(setblock(FIRST, "minecraft:gold_block"));
		world.getServer().runCommand(setblock(FIRST_CHEST, "minecraft:chest[facing=north]"));
		world.getServer().runCommand("item replace block " + FIRST_CHEST.getX() + " " + FIRST_CHEST.getY() + " "
				+ FIRST_CHEST.getZ() + " container.0 with minecraft:diamond 64");
		goTo(context, world, FIRST, Blocks.GOLD_BLOCK);

		context.runOnClient(client -> WdlTestSupport.module().settings().<TextSetting>byId("seed").orElseThrow().set(String.valueOf(SEED)));
		WdlTestSupport.setEnabled(context, true);
		openAndClose(context, FIRST_CHEST);
		WdlTestSupport.setEnabled(context, false);

		return WdlTestSupport.lastSave(context);
	}

	private static Path downloadTheSecondPlaceAndWalkBackPastTheFirst(ClientGameTestContext context, TestSingleplayerContext world) {
		// Loaded before anyone goes there: a setblock into a chunk nobody has loaded does
		// nothing, and arriving before the floor is down means a fall.
		world.getServer().runCommand("forceload add " + SECOND.getX() + " " + SECOND.getZ());
		context.waitTicks(40);
		floor(world, SECOND);
		world.getServer().runCommand(setblock(SECOND, "minecraft:emerald_block"));
		goTo(context, world, SECOND, Blocks.EMERALD_BLOCK);

		boolean firstStillLoaded = context.computeOnClient(client -> client.level.getChunkSource()
				.getChunk(FIRST.getX() >> 4, FIRST.getZ() >> 4, false) != null);

		if (firstStillLoaded) {
			throw new AssertionError("hazırlık başarısız: ikinci yerdeyken ilk yer hâlâ yüklü");
		}

		// Blank this time: the seed written by the first download has to survive it.
		context.runOnClient(client -> WdlTestSupport.module().settings().<TextSetting>byId("seed").orElseThrow().set(""));
		WdlTestSupport.setEnabled(context, true);

		// Back past the first place, chest left shut: its chunk is written again from what
		// the server sends now, which has nothing inside the chest.
		goTo(context, world, FIRST, Blocks.GOLD_BLOCK);
		WdlTestSupport.setEnabled(context, false);

		return WdlTestSupport.lastSave(context);
	}

	private static void bothPlacesAreThere(TestSingleplayerContext reopened) {
		reopened.getServer().runOnServer(server -> {
			var level = server.getLevel(Level.OVERWORLD);

			if (!level.getBlockState(FIRST).is(Blocks.GOLD_BLOCK)) {
				throw new AssertionError("ilk indirmenin yeri yok (altın blok), bulunan: " + level.getBlockState(FIRST));
			}

			if (!level.getBlockState(SECOND).is(Blocks.EMERALD_BLOCK)) {
				throw new AssertionError("ikinci indirmenin yeri yok (zümrüt blok), bulunan: " + level.getBlockState(SECOND));
			}

			if (!(level.getBlockEntity(FIRST_CHEST) instanceof ChestBlockEntity chest)) {
				throw new AssertionError("ilk yerdeki sandık yok");
			}

			ItemStack inside = chest.getItem(0);

			if (!inside.is(Items.DIAMOND) || inside.getCount() != 64) {
				throw new AssertionError("yeniden görülen sandığın içi korunmalıydı (64 elmas), bulunan: " + inside);
			}

			if (level.getSeed() != SEED) {
				throw new AssertionError("ikinci indirmede seed boştu, ilkinin seed'i korunmalıydı: beklenen "
						+ SEED + ", bulunan " + level.getSeed());
			}
		});
	}

	// --- plumbing ---

	private static void goTo(ClientGameTestContext context, TestSingleplayerContext world, BlockPos at,
			net.minecraft.world.level.block.Block marker) {
		world.getServer().runCommand("tp @a " + (at.getX() + 0.5) + " " + at.getY() + " " + (at.getZ() + 1.5) + " 0 0");

		for (int tick = 0; tick < 400; tick++) {
			if (context.computeOnClient(client -> client.level.getBlockState(at).is(marker))) {
				context.waitTicks(20);
				return;
			}

			context.waitTick();
		}

		throw new AssertionError("hazırlık başarısız: " + at + " istemciye ulaşmadı; istemcide " + context.computeOnClient(client ->
				client.level.getBlockState(at) + ", chunk yüklü " + (client.level.getChunkSource().getChunk(at.getX() >> 4, at.getZ() >> 4, false) != null)
				+ ", oyuncu " + client.player.position())
				+ "; sunucuda " + world.getServer().computeOnServer(server -> server.overworld().getBlockState(at) + ", sunucuda oyuncu " + server.getPlayerList().getPlayers().getFirst().position()));
	}

	private static void openAndClose(ClientGameTestContext context, BlockPos pos) {
		context.runOnClient(client -> client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
		context.waitTicks(10);

		if (!context.computeOnClient(client -> client.gui.screen() instanceof AbstractContainerScreen<?>)) {
			throw new AssertionError("hazırlık başarısız: sandık açılmadı; istemcide " + context.computeOnClient(client ->
					client.level.getBlockState(pos) + ", oyuncu " + client.player.position() + ", ekran " + client.gui.screen()));
		}

		context.runOnClient(client -> client.gui.setScreen(null));
		context.waitTicks(5);
	}

	/** Every folder this world's downloads could have gone to: the continued one, or stamped ones after it. */
	private static List<String> downloadFolders(Path saves, String name) {
		try (Stream<Path> children = Files.list(saves)) {
			return children.map(path -> path.getFileName().toString())
					.filter(folder -> folder.startsWith(name))
					.toList();
		} catch (IOException e) {
			throw new AssertionError("saves okunamadı: " + saves, e);
		}
	}

	/** Solid ground under the spot and open air over it, whatever the terrain there was. */
	private static void floor(TestSingleplayerContext world, BlockPos at) {
		world.getServer().runCommand("fill " + (at.getX() - 3) + " " + (at.getY() - 1) + " " + (at.getZ() - 3) + " "
				+ (at.getX() + 3) + " " + (at.getY() - 1) + " " + (at.getZ() + 5) + " minecraft:stone");
		world.getServer().runCommand("fill " + (at.getX() - 3) + " " + at.getY() + " " + (at.getZ() - 3) + " "
				+ (at.getX() + 3) + " " + (at.getY() + 3) + " " + (at.getZ() + 5) + " minecraft:air");
	}

	private static String setblock(BlockPos pos, String block) {
		return "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block;
	}
}
