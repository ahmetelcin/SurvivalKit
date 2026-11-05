package com.merci.survivalkit.wdl;

import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Observes interactions with villagers and intercepts the merchant screen
 * to capture trades and store them in the download session's {@link VillagerTradeOverlay}.
 */
public final class VillagerTradeCapture {
	private final WorldDownloaderModule module;
	private UUID lastInteractedVillagerUuid;
	private BlockPos lastInteractedVillagerPos;

	public VillagerTradeCapture(WorldDownloaderModule module) {
		this.module = module;
		UseEntityCallback.EVENT.register(this::onUseEntity);
		ScreenEvents.AFTER_INIT.register(this::onScreenInit);
	}

	private InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hitResult) {
		if (module.isEnabled() && level.isClientSide() && entity instanceof AbstractVillager) {
			lastInteractedVillagerUuid = entity.getUUID();
			lastInteractedVillagerPos = entity.blockPosition();
		}
		return InteractionResult.PASS;
	}

	private void onScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
		if (!module.isEnabled()) {
			return;
		}
		if (!(screen instanceof MerchantScreen merchantScreen)) {
			return;
		}

		MerchantMenu menu = merchantScreen.getMenu();

		// Screen tick: captures as soon as ClientboundMerchantOffersPacket arrives
		ScreenEvents.afterTick(merchantScreen).register(s -> capture(client, menu));
		// Screen close: fallback capture before closing
		ScreenEvents.remove(merchantScreen).register(s -> capture(client, menu));
	}

	public void capture(Minecraft client, MerchantMenu menu) {
		if (!module.isEnabled() || client == null || client.level == null) {
			return;
		}
		Optional<DownloadSession> optSession = module.session();
		if (optSession.isEmpty()) {
			return;
		}
		DownloadSession session = optSession.get();

		MerchantOffers offers = menu.getOffers();
		if (offers == null || offers.isEmpty()) {
			return;
		}

		UUID villagerUuid = lastInteractedVillagerUuid;
		BlockPos villagerPos = lastInteractedVillagerPos;

		if (villagerUuid == null) {
			AbstractVillager nearest = findNearestVillager(client);
			if (nearest != null) {
				villagerUuid = nearest.getUUID();
				villagerPos = nearest.blockPosition();
			}
		}

		if (villagerUuid == null) {
			return;
		}

		int level = menu.getTraderLevel();
		int xp = menu.getTraderXp();

		session.tradeOverlay().capture(villagerUuid, new VillagerTradeData(offers.copy(), level, xp));

		// Re-save the chunk containing the villager
		if (villagerPos != null) {
			LevelChunk chunk = client.level.getChunkSource().getChunk(villagerPos.getX() >> 4, villagerPos.getZ() >> 4, false);
			if (chunk != null) {
				session.saveChunk(client.level, chunk);
			}
		}
	}

	private AbstractVillager findNearestVillager(Minecraft client) {
		if (client.player == null || client.level == null) {
			return null;
		}
		double maxDistSq = 36.0;
		AbstractVillager nearest = null;
		double nearestDistSq = Double.MAX_VALUE;

		for (Entity e : client.level.entitiesForRendering()) {
			if (e instanceof AbstractVillager v) {
				double distSq = e.distanceToSqr(client.player);
				if (distSq < maxDistSq && distSq < nearestDistSq) {
					nearest = v;
					nearestDistSq = distSq;
				}
			}
		}
		return nearest;
	}

	public UUID lastInteractedVillagerUuid() {
		return lastInteractedVillagerUuid;
	}

	public void setLastInteractedVillager(UUID uuid, BlockPos pos) {
		this.lastInteractedVillagerUuid = uuid;
		this.lastInteractedVillagerPos = pos;
	}
}
