package com.merci.survivalkit.autoeat;

import com.merci.survivalkit.util.FoodSlot;
import com.merci.survivalkit.util.InventoryUtil;
import com.merci.survivalkit.util.PauseConditions;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Wires {@link AutoEat} to the running client. The state machine itself stays Minecraft-free. */
public final class ClientEatContext implements EatContext {
	private final Minecraft minecraft;
	private final Supplier<InventoryUtil.Scope> scope;

	public ClientEatContext(Minecraft minecraft, Supplier<InventoryUtil.Scope> scope) {
		this.minecraft = minecraft;
		this.scope = scope;
	}

	@Override
	public boolean ready() {
		// The screen is only asked about once there is a world, the way the old check did it.
		boolean inWorld = minecraft.player != null && minecraft.level != null;
		return PauseConditions.active(new PauseConditions.State(inWorld, inWorld && minecraft.gui.screen() != null));
	}

	@Override
	public int foodLevel() {
		return minecraft.player.getFoodData().getFoodLevel();
	}

	@Override
	public float health() {
		return minecraft.player.getHealth();
	}

	@Override
	public boolean usingItem() {
		return minecraft.player.isUsingItem();
	}

	@Override
	public int selectedSlot() {
		return minecraft.player.getInventory().getSelectedSlot();
	}

	@Override
	public List<FoodSlot> food() {
		return InventoryUtil.food(minecraft.player.getInventory(), scope.get());
	}

	@Override
	public void selectSlot(int slot) {
		if (minecraft.player == null) {
			return;
		}

		minecraft.player.getInventory().setSelectedSlot(slot);
	}

	@Override
	public void swap(int inventorySlot, int hotbarSlot) {
		InventoryUtil.swap(minecraft, inventorySlot, hotbarSlot);
	}

	@Override
	public String offhandId() {
		ItemStack offhand = minecraft.player.getOffhandItem();
		return offhand.isEmpty() ? "" : InventoryUtil.idOf(offhand);
	}

	@Override
	public void swapToOffhand(int inventorySlot) {
		InventoryUtil.swapToOffhand(minecraft, inventorySlot);
	}

	/**
	 * Asks the game to use that hand, the same call the use key ends up making — but for one
	 * hand, rather than trying the main hand and falling through to the off hand.
	 */
	@Override
	public void startUsing(EatingHand hand) {
		if (minecraft.player == null || minecraft.gameMode == null) {
			return;
		}

		minecraft.gameMode.useItem(minecraft.player,
				hand == EatingHand.OFF_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
	}

	/**
	 * With "toggle use" turned on, the vanilla key flips on every setDown(true) and ignores
	 * setDown(false) — so in that mode we only poke it when the state actually has to change.
	 */
	@Override
	public void holdUseKey(boolean held) {
		var keyUse = minecraft.options.keyUse;

		if (!minecraft.options.toggleUse().get()) {
			keyUse.setDown(held);
			return;
		}

		if (keyUse.isDown() != held) {
			keyUse.setDown(true);
		}
	}
}
