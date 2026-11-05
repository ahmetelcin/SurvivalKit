package com.merci.survivalkit.wdl;

import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Captures what is inside a horse, donkey, mule or llama, and a chest or hopper minecart, when
 * the player opens it.
 *
 * <p>The server only sends those contents while the screen is open, so this is the one chance.
 * They go to the session's {@link EntityInventoryOverlay}, which lays them over the entity when
 * it is written, and the chunk is saved again on the spot.
 *
 * <p>Saddle and body armour are not taken from the screen: the game already sends those to every
 * client, to draw them.
 */
public final class EntityInventoryCapture {
	private final WorldDownloaderModule module;
	private Entity clicked;
	private long clickedAt;

	public EntityInventoryCapture(WorldDownloaderModule module) {
		this.module = module;
		UseEntityCallback.EVENT.register(this::onUseEntity);
		ScreenEvents.AFTER_INIT.register(this::onScreenInit);
	}

	private InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hit) {
		if (module.isEnabled() && level.isClientSide() && hasInventory(entity)) {
			clicked = entity;
			clickedAt = level.getGameTime();
		}

		return InteractionResult.PASS;
	}

	private static boolean hasInventory(Entity entity) {
		return entity instanceof AbstractHorse || entity instanceof AbstractMinecartContainer;
	}

	private void onScreenInit(Minecraft client, Screen screen, int width, int height) {
		if (!module.isEnabled() || client.player == null || client.level == null
				|| !(screen instanceof AbstractContainerScreen<?> containerScreen)) {
			return;
		}

		AbstractContainerMenu menu = containerScreen.getMenu();
		Optional<Entity> target = target(client, menu);

		if (target.isEmpty()) {
			return;
		}

		// The contents arrive a moment after the screen: taken on every tick it is open, and
		// once more as it closes.
		ScreenEvents.afterTick(screen).register(open -> capture(client, menu, target.get()));
		ScreenEvents.remove(screen).register(closed -> capture(client, menu, target.get()));
	}

	private Optional<Entity> target(Minecraft client, AbstractContainerMenu menu) {
		Optional<Entity> ridden = menu instanceof AbstractMountInventoryMenu
			? Optional.ofNullable(client.player.getVehicle()).filter(EntityInventoryCapture::hasInventory)
			: Optional.empty();
		Optional<Entity> recent = Optional.ofNullable(clicked).filter(entity -> !entity.isRemoved());
		int ticksSince = (int) Math.min(Integer.MAX_VALUE, client.level.getGameTime() - clickedAt);
		double distance = recent.map(entity -> entity.distanceToSqr(client.player)).orElse(Double.MAX_VALUE);

		return InventoryTarget.choose(ridden, recent, ticksSince, distance)
			.filter(entity -> fits(entity, menu));
	}

	/** A mount's screen for an animal, a container's screen for a cart: never one for the other. */
	private static boolean fits(Entity entity, AbstractContainerMenu menu) {
		if (entity instanceof AbstractHorse) {
			return menu instanceof AbstractMountInventoryMenu;
		}

		return !(menu instanceof AbstractMountInventoryMenu) && entity instanceof AbstractMinecartContainer cart
			&& entitySlots(menu) == cart.getContainerSize();
	}

	private static int entitySlots(AbstractContainerMenu menu) {
		int count = 0;

		for (Slot slot : menu.slots) {
			if (!(slot.container instanceof Inventory)) {
				count++;
			}
		}

		return count;
	}

	private void capture(Minecraft client, AbstractContainerMenu menu, Entity entity) {
		Optional<DownloadSession> session = module.session();

		if (session.isEmpty() || client.level == null) {
			return;
		}

		List<ItemStackWithSlot> items = new ArrayList<>();

		for (Slot slot : menu.slots) {
			// The player's own slots, and an animal's saddle and armour slots, which are
			// subclasses: only the plain container slots are the bags or the cart.
			if (slot.container instanceof Inventory || (entity instanceof AbstractHorse && slot.getClass() != Slot.class)) {
				continue;
			}

			if (!slot.getItem().isEmpty()) {
				items.add(new ItemStackWithSlot(slot.getContainerSlot(), slot.getItem().copy()));
			}
		}

		session.get().inventoryOverlay().capture(entity.getUUID(), items);

		LevelChunk chunk = client.level.getChunkSource().getChunk(entity.getBlockX() >> 4, entity.getBlockZ() >> 4, false);

		if (chunk != null) {
			session.get().saveChunk(client.level, chunk);
		}
	}
}
