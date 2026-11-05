package com.merci.survivalkit.autotool;

import com.merci.survivalkit.tool.ToolSlot;
import com.merci.survivalkit.util.InventoryUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The block under the crosshair and the inventory measured against it — what both tool
 * modules read from the game before handing the choice to {@link com.merci.survivalkit.tool.BestTool}.
 */
final class ClientTools {
	private ClientTools() {
	}

	static Optional<BlockState> targetState(Minecraft minecraft) {
		if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return Optional.empty();
		}

		BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
		return state.isAir() ? Optional.empty() : Optional.of(state);
	}

	static Optional<MiningTarget> target(Minecraft minecraft) {
		return targetState(minecraft).map(state -> new MiningTarget(
			BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
			state.requiresCorrectToolForDrops()));
	}

	static List<ToolSlot> tools(Minecraft minecraft, InventoryUtil.Scope scope) {
		Optional<BlockState> target = targetState(minecraft);

		if (target.isEmpty()) {
			return List.of();
		}

		BlockState state = target.get();
		Inventory inventory = minecraft.player.getInventory();
		// Not getContainerSize(): that counts the armour and offhand slots, which a swap cannot reach.
		int limit = scope == InventoryUtil.Scope.HOTBAR ? Inventory.getSelectionSize() : Inventory.INVENTORY_SIZE;
		List<ToolSlot> tools = new ArrayList<>();

		for (int slot = 0; slot < limit; slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!stack.isEmpty()) {
				tools.add(measure(minecraft, slot, stack, state));
			}
		}

		return tools;
	}

	/**
	 * The item against the block, the way the game will measure it. Efficiency is an attribute
	 * of the player rather than of the stack, and the game only adds it when the item is a
	 * tool for the block at all, so it is added here on the same condition.
	 */
	private static ToolSlot measure(Minecraft minecraft, int slot, ItemStack stack, BlockState state) {
		float speed = stack.getDestroySpeed(state);

		if (speed > 1.0f) {
			int efficiency = level(minecraft, Enchantments.EFFICIENCY, stack);

			if (efficiency > 0) {
				speed += efficiency * efficiency + 1;
			}
		}

		int durabilityLeft = stack.isDamageableItem()
			? stack.getMaxDamage() - stack.getDamageValue()
			: ToolSlot.UNBREAKABLE;

		return new ToolSlot(slot, stack.isCorrectToolForDrops(state), speed,
			level(minecraft, Enchantments.SILK_TOUCH, stack), level(minecraft, Enchantments.FORTUNE, stack), durabilityLeft);
	}

	private static int level(Minecraft minecraft, ResourceKey<Enchantment> key, ItemStack stack) {
		Holder<Enchantment> enchantment = minecraft.level.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(key);
		return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
	}
}
