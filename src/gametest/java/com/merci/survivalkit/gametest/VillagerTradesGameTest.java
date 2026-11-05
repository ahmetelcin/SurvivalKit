package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.AABB;

/**
 * Validates that villager trades, level, and XP opened by the player
 * are captured and written to the downloaded world's entities/*.mca files,
 * and survive reopening in singleplayer.
 */
public class VillagerTradesGameTest implements FabricClientGameTest {
	private static final BlockPos SPAWN_POS = new BlockPos(8, 80, 8);

	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		Path downloaded;

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			save = world.getWorldSave();
			downloaded = downloadWorldWithTrades(context, world);
		}

		// 1. Verify on-disk entity MCA file has Offers and XP
		assertOnDiskVillagerTrades(downloaded);

		// 2. Reopen downloaded world in singleplayer
		replaceContents(save.getSaveDirectory(), downloaded);

		try (TestSingleplayerContext reopened = save.open()) {
			assertReopenedVillagerTrades(reopened);
		}

		SurvivalKit.LOGGER.info("[gametest] köylü ticaretleri indirilen dünyada başarıyla korundu -> {}",
				downloaded.getFileName());
	}

	private static Path downloadWorldWithTrades(ClientGameTestContext context, TestSingleplayerContext world) {
		context.waitTicks(5);

		// Platform and villager on server
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.getLevel(Level.OVERWORLD);

			for (int x = 6; x <= 11; x++) {
				for (int z = 6; z <= 11; z++) {
					level.setBlock(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState(), 3);
				}
			}

			Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
			if (villager == null) {
				throw new AssertionError("köylü varlığı oluşturulamadı");
			}
			villager.setPos(8.5, 80.0, 8.5);
			villager.setNoGravity(true);
			villager.setVillagerData(villager.getVillagerData()
					.withProfession(BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.FARMER))
					.withLevel(2));

			MerchantOffers offers = new MerchantOffers();
			offers.add(new MerchantOffer(
					new ItemCost(Items.EMERALD, 1),
					new ItemStack(Items.BREAD, 64),
					16, 10, 0.05f));
			villager.setOffers(offers);
			villager.setVillagerXp(10);
			level.addFreshEntity(villager);
		});

		world.getServer().runCommand("tp @a 8.5 81.0 10.5 180 0");
		context.waitTicks(10);

		// Wait until client receives and renders the villager
		context.waitFor(client -> {
			if (client.level == null) return false;
			for (Entity e : client.level.entitiesForRendering()) {
				if (e instanceof Villager) return true;
			}
			return false;
		});

		// Start WDL
		setEnabled(context, true);
		context.waitTicks(5);

		// Right-click villager to open trading screen
		context.runOnClient(client -> {
			for (Entity e : client.level.entitiesForRendering()) {
				if (e instanceof Villager v) {
					client.gameMode.interact(client.player, v, new net.minecraft.world.phys.EntityHitResult(v), InteractionHand.MAIN_HAND);
					break;
				}
			}
		});

		// Wait until MerchantScreen opens and offers arrive
		context.waitFor(client -> client.gui.screen() instanceof MerchantScreen ms && !ms.getMenu().getOffers().isEmpty());
		context.waitTicks(5);

		// Close screen
		context.runOnClient(client -> client.gui.setScreen(null));
		context.waitTicks(10);

		// Stop WDL
		setEnabled(context, false);
		context.waitTicks(5);

		return context.computeOnClient(client -> module().lastSavePath()
				.orElseThrow(() -> new AssertionError("wdl indirme klasörü oluşturmadı")));
	}

	private static void assertOnDiskVillagerTrades(Path worldDir) {
		ChunkPos chunkPos = ChunkPos.containing(SPAWN_POS);
		Path region = DimensionType.getStorageFolder(Level.OVERWORLD, worldDir).resolve("entities");
		RegionStorageInfo info = new RegionStorageInfo(worldDir.getFileName().toString(), Level.OVERWORLD, "entities");
		CompoundTag chunkTag;

		try (RegionFileStorage storage = new RegionFileStorage(info, region, false)) {
			chunkTag = storage.read(chunkPos);
		} catch (IOException e) {
			throw new AssertionError("varlık bölge dosyası okunamadı: " + region, e);
		}

		if (chunkTag == null) {
			throw new AssertionError("Chunk için varlık verisi diskte bulunamadı: " + chunkPos);
		}

		ListTag entities = chunkTag.getListOrEmpty("Entities");
		boolean foundVillager = false;

		for (Tag tag : entities) {
			if (tag instanceof CompoundTag comp && "minecraft:villager".equals(comp.getStringOr("id", ""))) {
				foundVillager = true;

				Optional<MerchantOffers> offersOpt = comp.read("Offers", MerchantOffers.CODEC);
				if (offersOpt.isEmpty() || offersOpt.get().isEmpty()) {
					throw new AssertionError("HATA: Diskteki köylünün ticaretleri boş veya yok!");
				}

				MerchantOffer offer = offersOpt.get().get(0);
				if (!offer.getResult().is(Items.BREAD) || offer.getResult().getCount() != 64) {
					throw new AssertionError("HATA: Ticaret sonucu beklenen 64 Ekmek değil: " + offer.getResult());
				}

				int xp = comp.getIntOr("Xp", 0);
				if (xp < 10) {
					throw new AssertionError("HATA: Köylü XP beklenen >= 10 değil: " + xp);
				}

				CompoundTag vd = comp.getCompoundOrEmpty("VillagerData");
				if (vd.getIntOr("level", 0) != 2) {
					throw new AssertionError("HATA: Köylü seviyesi beklenen 2 değil: " + vd.getIntOr("level", 0));
				}
			}
		}

		if (!foundVillager) {
			throw new AssertionError("Diskteki varlıklar arasında köylü bulunamadı!");
		}
	}

	private static void assertReopenedVillagerTrades(TestSingleplayerContext reopened) {
		reopened.getServer().runOnServer(server -> {
			ServerLevel level = server.getLevel(Level.OVERWORLD);
			AABB searchBox = new AABB(SPAWN_POS).inflate(5.0);
			List<Entity> entities = level.getEntities((Entity) null, searchBox, e -> true);

			Villager foundVillager = null;
			for (Entity entity : entities) {
				if (entity instanceof Villager v) {
					foundVillager = v;
					break;
				}
			}

			if (foundVillager == null) {
				throw new AssertionError("Yeniden açılan dünyada köylü bulunamadı!");
			}

			MerchantOffers offers = foundVillager.getOffers();
			if (offers == null || offers.isEmpty()) {
				throw new AssertionError("Yeniden açılan dünyadaki köylünün ticaretleri boş!");
			}

			MerchantOffer offer = offers.get(0);
			if (!offer.getResult().is(Items.BREAD) || offer.getResult().getCount() != 64) {
				throw new AssertionError("Yeniden açılan köylünün ticareti beklenen 64 ekmek değil: " + offer.getResult());
			}

			if (foundVillager.getVillagerXp() < 10) {
				throw new AssertionError("Yeniden açılan köylünün XP'si beklenen >= 10 değil: " + foundVillager.getVillagerXp());
			}

			if (foundVillager.getVillagerData().level() != 2) {
				throw new AssertionError("Yeniden açılan köylünün seviyesi beklenen 2 değil: " + foundVillager.getVillagerData().level());
			}
		});
	}

	private static void setEnabled(ClientGameTestContext context, boolean enabled) {
		context.runOnClient(client -> module().setEnabled(enabled));
	}

	private static WorldDownloaderModule module() {
		return (WorldDownloaderModule) SurvivalKit.modules()
				.byId("wdl")
				.orElseThrow(() -> new AssertionError("wdl modülü kayıtlı değil"));
	}

	private static void replaceContents(Path targetDir, Path sourceDir) {
		try {
			if (Files.exists(targetDir)) {
				try (Stream<Path> stream = Files.walk(targetDir)) {
					stream.sorted(Comparator.reverseOrder())
							.filter(p -> !p.equals(targetDir))
							.forEach(p -> {
								try {
									Files.deleteIfExists(p);
								} catch (IOException e) {
									throw new UncheckedIOException(e);
								}
							});
				}
			}
			try (Stream<Path> stream = Files.walk(sourceDir)) {
				stream.forEach(source -> {
					Path relative = sourceDir.relativize(source);
					Path destination = targetDir.resolve(relative);
					try {
						if (Files.isDirectory(source)) {
							Files.createDirectories(destination);
						} else {
							Files.createDirectories(destination.getParent());
							Files.copy(source, destination);
						}
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
