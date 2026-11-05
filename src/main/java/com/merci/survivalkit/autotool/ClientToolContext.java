package com.merci.survivalkit.autotool;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.tool.ToolSlot;
import com.merci.survivalkit.util.InventoryUtil;
import com.merci.survivalkit.util.PauseConditions;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Wires {@link AutoTool} to the running client. Which tool is best stays Minecraft-free. */
public final class ClientToolContext implements ToolContext {
	private final Minecraft minecraft;
	private final Supplier<InventoryUtil.Scope> scope;

	public ClientToolContext(Minecraft minecraft, Supplier<InventoryUtil.Scope> scope) {
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

	@Override
	public boolean attackHeld() {
		return minecraft.options.keyAttack.isDown();
	}

	@Override
	public boolean breaking() {
		return minecraft.gameMode.isDestroying();
	}

	@Override
	public Optional<MiningTarget> target() {
		return ClientTools.target(minecraft);
	}

	@Override
	public List<ToolSlot> tools() {
		return ClientTools.tools(minecraft, scope.get());
	}

	@Override
	public boolean holdingTool() {
		ItemStack held = minecraft.player.getMainHandItem();
		return held.is(ItemTags.PICKAXES) || held.is(ItemTags.AXES) || held.is(ItemTags.SHOVELS)
			|| held.is(ItemTags.HOES) || held.is(Items.SHEARS);
	}

	@Override
	public boolean holdingSword() {
		return minecraft.player.getMainHandItem().is(ItemTags.SWORDS);
	}

	@Override
	public int selectedSlot() {
		return minecraft.player.getInventory().getSelectedSlot();
	}

	@Override
	public boolean reserved(int slot) {
		return SurvivalKit.reserved(minecraft.player.getInventory().getItem(slot));
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
}
