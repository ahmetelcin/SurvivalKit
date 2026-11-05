package com.merci.survivalkit.weapon;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.util.InventoryUtil;
import com.merci.survivalkit.util.PauseConditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;

/** Wires {@link AutoWeapon} to the running client. Which weapon is best stays Minecraft-free. */
public final class ClientWeaponContext implements WeaponContext {
	/** A player's bare attack damage; the item's modifiers are added to it. */
	private static final double BASE_DAMAGE = 1.0;

	private final Minecraft minecraft;
	private boolean hit;
	private float lastHealth = -1;

	public ClientWeaponContext(Minecraft minecraft) {
		this.minecraft = minecraft;

		// Fired on this client when the player hits something, by hand or by auto hit alike.
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() && player == minecraft.player) {
				hit = true;
			}

			return InteractionResult.PASS;
		});
	}

	@Override
	public boolean ready() {
		boolean inWorld = minecraft.player != null && minecraft.level != null && minecraft.gameMode != null;
		return PauseConditions.active(new PauseConditions.State(inWorld, inWorld && minecraft.gui.screen() != null));
	}

	@Override
	public Optional<Target> aimed() {
		if (!(minecraft.hitResult instanceof EntityHitResult hitResult)) {
			return Optional.empty();
		}

		return target(hitResult.getEntity());
	}

	@Override
	public List<Target> around(double range) {
		Player player = minecraft.player;

		// The box is only the first cut: its corners are further than the range, the distance is not.
		return minecraft.level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range)).stream()
			.filter(entity -> player.distanceTo(entity) <= range)
			.flatMap(entity -> target(entity).stream())
			.toList();
	}

	@Override
	public List<WeaponSlot> weapons() {
		Inventory inventory = minecraft.player.getInventory();
		List<WeaponSlot> weapons = new ArrayList<>();

		// Not getContainerSize(): that counts the armour and offhand slots, which a swap cannot reach.
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			measure(slot, inventory.getItem(slot)).ifPresent(weapons::add);
		}

		return weapons;
	}

	@Override
	public boolean holdingWeapon() {
		return kindOf(minecraft.player.getMainHandItem()).isPresent();
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
		minecraft.player.getInventory().setSelectedSlot(slot);
	}

	@Override
	public void swap(int inventorySlot, int hotbarSlot) {
		InventoryUtil.swap(minecraft, inventorySlot, hotbarSlot);
	}

	/** The same measure the game draws the crosshair's attack indicator from. */
	@Override
	public boolean attackReady() {
		return minecraft.player.getAttackStrengthScale(0.0f) >= 1.0f;
	}

	/** The same calls a left click on the entity ends up making. */
	@Override
	public void attack(int entityId) {
		Entity entity = minecraft.level.getEntity(entityId);

		if (entity == null || minecraft.gameMode == null) {
			return;
		}

		minecraft.gameMode.attack(minecraft.player, entity);
		minecraft.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
	}

	@Override
	public boolean hitSomethingSinceLastTick() {
		boolean was = hit;
		hit = false;
		return was;
	}

	/** Read off the health bar: any drop since the last look counts as a hit taken. */
	@Override
	public boolean hurtSinceLastTick() {
		float health = minecraft.player.getHealth();
		boolean hurt = lastHealth >= 0 && health < lastHealth;
		lastHealth = health;
		return hurt;
	}

	@Override
	public long gameTime() {
		return minecraft.level.getGameTime();
	}

	/** Mobs and other players; not armour stands, item frames or the like. Hostile is the game's own {@link Enemy}. */
	private Optional<Target> target(Entity entity) {
		if (!(entity instanceof Mob) && !(entity instanceof Player)) {
			return Optional.empty();
		}

		if (entity == minecraft.player || !entity.isAlive()) {
			return Optional.empty();
		}

		TargetKind kind = new TargetKind(
			entity.typeHolder().is(EntityTypeTags.SENSITIVE_TO_SMITE),
			entity.typeHolder().is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS));

		return Optional.of(new Target(entity.getId(), entity instanceof Player, kind, minecraft.player.distanceTo(entity),
			entity instanceof Enemy));
	}

	private Optional<WeaponSlot> measure(int slot, ItemStack stack) {
		return kindOf(stack).map(kind -> {
			double damage = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
				.compute(Attributes.ATTACK_DAMAGE, BASE_DAMAGE, EquipmentSlot.MAINHAND);
			int durabilityLeft = stack.isDamageableItem()
				? stack.getMaxDamage() - stack.getDamageValue()
				: WeaponSlot.UNBREAKABLE;

			return new WeaponSlot(slot, kind, damage,
				level(Enchantments.SHARPNESS, stack), level(Enchantments.SMITE, stack),
				level(Enchantments.BANE_OF_ARTHROPODS, stack), durabilityLeft,
				BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		});
	}

	private static Optional<WeaponKind> kindOf(ItemStack stack) {
		if (stack.is(ItemTags.SWORDS)) {
			return Optional.of(WeaponKind.SWORD);
		}

		if (stack.is(ItemTags.AXES)) {
			return Optional.of(WeaponKind.AXE);
		}

		return Optional.empty();
	}

	private int level(ResourceKey<Enchantment> key, ItemStack stack) {
		Holder<Enchantment> enchantment = minecraft.level.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(key);
		return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
	}
}
