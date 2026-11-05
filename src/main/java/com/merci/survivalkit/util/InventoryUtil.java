package com.merci.survivalkit.util;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

/**
 * The one place that reads the player's inventory and turns it into {@link FoodSlot}
 * snapshots. Everything downstream of here is Minecraft-free and unit-testable; this class
 * is the seam, and it is covered by the game tests rather than by unit tests.
 */
public final class InventoryUtil {
	/** How far to look for something to eat. */
	public enum Scope {
		/** The nine slots that can be selected directly. */
		HOTBAR,
		/** The hotbar plus the backpack. Reaching these needs a swap into the hotbar first. */
		FULL
	}

	private InventoryUtil() {
	}

	public static List<FoodSlot> food(Inventory inventory, Scope scope) {
		// Not getContainerSize(): that counts the armour and offhand slots, which a swap cannot reach.
		int limit = scope == Scope.HOTBAR ? Inventory.getSelectionSize() : Inventory.INVENTORY_SIZE;
		List<FoodSlot> found = new ArrayList<>();

		for (int slot = 0; slot < limit; slot++) {
			ItemStack stack = inventory.getItem(slot);
			FoodProperties food = stack.get(DataComponents.FOOD);

			if (food == null) {
				continue;
			}

			found.add(new FoodSlot(slot, idOf(stack), food.nutrition(), food.saturation(), isRisky(stack)));
		}

		return found;
	}

	/**
	 * The first slot within the scope holding this id, nearest hand first.
	 *
	 * <p>The off hand is not one of the slots looked at — the scan stops short of it, the
	 * same way the food scan does — so asking where a totem is never answers "the one you
	 * are already holding".
	 */
	public static OptionalInt slotOf(Inventory inventory, Scope scope, String id) {
		int limit = scope == Scope.HOTBAR ? Inventory.getSelectionSize() : Inventory.INVENTORY_SIZE;

		for (int slot = 0; slot < limit; slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!stack.isEmpty() && idOf(stack).equals(id)) {
				return OptionalInt.of(slot);
			}
		}

		return OptionalInt.empty();
	}

	/**
	 * Exchanges two slots by asking the server, the way pressing a number key over a stack
	 * does. Symmetric, so calling it again with the same pair undoes it.
	 *
	 * <p>Goes through whichever menu is open, not always the player's own: with a chest or a
	 * server's GUI open, a click aimed at the inventory menu is dropped by the client without
	 * a word, and a swap that was meant to be undone stays done.
	 */
	public static void swap(Minecraft minecraft, int inventorySlot, int hotbarSlot) {
		click(minecraft, inventorySlot, hotbarSlot);
	}

	/**
	 * Exchanges a slot with the off hand, the way pressing the swap-hands key over a stack
	 * does. Symmetric, so calling it again with the same slot puts both back.
	 *
	 * <p>The off hand is addressed as button {@value #OFFHAND_BUTTON} of a swap, which is how
	 * the server is told "the off hand" rather than a numbered hotbar slot. The main hand is
	 * never part of it: whatever is selected stays selected and stays held.
	 */
	public static void swapToOffhand(Minecraft minecraft, int inventorySlot) {
		click(minecraft, inventorySlot, OFFHAND_BUTTON);
	}

	/** The button a swap uses to mean the off hand instead of one of the nine hotbar slots. */
	private static final int OFFHAND_BUTTON = 40;

	/** A number-key swap over that inventory slot, in the menu that is open right now. */
	private static void click(Minecraft minecraft, int inventorySlot, int button) {
		Player player = minecraft.player;

		if (player == null || minecraft.gameMode == null) {
			return;
		}

		AbstractContainerMenu menu = player.containerMenu;
		OptionalInt menuSlot = menuSlotOf(menu, player.getInventory(), inventorySlot);

		// A menu without the player's inventory in it (a lectern) has nowhere to click.
		if (menuSlot.isEmpty()) {
			return;
		}

		minecraft.gameMode.handleContainerInput(menu.containerId, menuSlot.getAsInt(), button, ContainerInput.SWAP, player);
	}

	/**
	 * Where that inventory slot sits in the menu. Every menu numbers it differently — the
	 * player's own lists the backpack first and the hotbar last, a chest puts both after its
	 * own rows — so it is looked up rather than worked out.
	 */
	private static OptionalInt menuSlotOf(AbstractContainerMenu menu, Inventory inventory, int inventorySlot) {
		for (int index = 0; index < menu.slots.size(); index++) {
			Slot slot = menu.slots.get(index);

			if (slot.container == inventory && slot.getContainerSlot() == inventorySlot) {
				return OptionalInt.of(index);
			}
		}

		return OptionalInt.empty();
	}

	/** The namespaced id, spelled the way the priority and block lists spell it. */
	public static String idOf(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	/** Harmful effects now live on the consumable component, not on the food component. */
	public static boolean isRisky(ItemStack stack) {
		Consumable consumable = stack.get(DataComponents.CONSUMABLE);

		if (consumable == null) {
			return false;
		}

		for (ConsumeEffect effect : consumable.onConsumeEffects()) {
			if (effect instanceof ApplyStatusEffectsConsumeEffect applied && hasHarmfulEffect(applied)) {
				return true;
			}
		}

		return false;
	}

	private static boolean hasHarmfulEffect(ApplyStatusEffectsConsumeEffect applied) {
		for (MobEffectInstance instance : applied.effects()) {
			if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				return true;
			}
		}

		return false;
	}
}
