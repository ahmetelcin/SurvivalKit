package com.merci.survivalkit.autototem;

import com.merci.survivalkit.util.InventoryUtil;
import com.merci.survivalkit.util.PauseConditions;
import java.util.OptionalInt;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Wires {@link AutoTotem} to the running client. The rules themselves stay Minecraft-free. */
public final class ClientTotemContext implements TotemContext {
	private final Minecraft minecraft;
	private final Supplier<InventoryUtil.Scope> scope;

	public ClientTotemContext(Minecraft minecraft, Supplier<InventoryUtil.Scope> scope) {
		this.minecraft = minecraft;
		this.scope = scope;
	}

	@Override
	public boolean ready() {
		boolean inWorld = minecraft.player != null && minecraft.level != null;
		return PauseConditions.active(new PauseConditions.State(inWorld, inWorld && minecraft.gui.screen() != null));
	}

	@Override
	public float health() {
		return minecraft.player.getHealth();
	}

	@Override
	public String offhandId() {
		ItemStack offhand = minecraft.player.getOffhandItem();
		return offhand.isEmpty() ? "" : InventoryUtil.idOf(offhand);
	}

	@Override
	public OptionalInt slotOf(String id) {
		return InventoryUtil.slotOf(minecraft.player.getInventory(), scope.get(), id);
	}

	@Override
	public int count(String id) {
		Inventory inventory = minecraft.player.getInventory();
		int count = 0;

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!stack.isEmpty() && InventoryUtil.idOf(stack).equals(id)) {
				count += stack.getCount();
			}
		}

		return count;
	}

	@Override
	public void say(String message) {
		minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
	}

	@Override
	public void swapToOffhand(int inventorySlot) {
		InventoryUtil.swapToOffhand(minecraft, inventorySlot);
	}
}
