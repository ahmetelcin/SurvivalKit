package com.merci.survivalkit.autotool;

import com.merci.survivalkit.tool.ToolSlot;
import com.merci.survivalkit.util.InventoryUtil;
import com.merci.survivalkit.util.PauseConditions;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Wires {@link ToolPicker} to the running client. */
public final class ClientPickContext implements PickContext {
	private final Minecraft minecraft;
	private final Supplier<InventoryUtil.Scope> scope;

	public ClientPickContext(Minecraft minecraft, Supplier<InventoryUtil.Scope> scope) {
		this.minecraft = minecraft;
		this.scope = scope;
	}

	@Override
	public boolean ready() {
		boolean inWorld = minecraft.player != null && minecraft.level != null && minecraft.gameMode != null;
		return PauseConditions.active(new PauseConditions.State(inWorld, inWorld && minecraft.gui.screen() != null));
	}

	@Override
	public boolean creative() {
		return minecraft.player.isCreative();
	}

	/** The same call the game's key handling makes, so what is taken here the game never sees. */
	@Override
	public int takePickClicks() {
		int clicks = 0;

		while (minecraft.options.keyPickItem.consumeClick()) {
			clicks++;
		}

		return clicks;
	}

	@Override
	public Optional<MiningTarget> target() {
		return ClientTools.target(minecraft);
	}

	/** Anywhere in the inventory, the way the game's own pick block looks. */
	@Override
	public boolean blockInInventory() {
		return ClientTools.targetState(minecraft)
			.map(state -> state.getBlock().asItem())
			.filter(item -> item != Items.AIR)
			.map(this::inInventory)
			.orElse(false);
	}

	@Override
	public List<ToolSlot> tools() {
		return ClientTools.tools(minecraft, scope.get());
	}

	@Override
	public int selectedSlot() {
		return minecraft.player.getInventory().getSelectedSlot();
	}

	@Override
	public void selectSlot(int slot) {
		minecraft.player.getInventory().setSelectedSlot(slot);
	}

	@Override
	public void swap(int inventorySlot, int hotbarSlot) {
		InventoryUtil.swap(minecraft, inventorySlot, hotbarSlot);
	}

	private boolean inInventory(Item item) {
		return minecraft.player.getInventory().getNonEquipmentItems().stream().anyMatch(stack -> stack.is(item));
	}
}
