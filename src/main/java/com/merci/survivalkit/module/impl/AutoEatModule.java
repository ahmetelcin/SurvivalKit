package com.merci.survivalkit.module.impl;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.autoeat.AutoEat;
import com.merci.survivalkit.autoeat.ClientEatContext;
import com.merci.survivalkit.autoeat.EatContext;
import com.merci.survivalkit.autoeat.EatSettings;
import com.merci.survivalkit.autoeat.EatingHand;
import com.merci.survivalkit.autoeat.ThresholdMode;
import com.merci.survivalkit.module.Category;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.settings.RegistryListSetting;
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.util.InventoryUtil;
import com.merci.survivalkit.util.ItemPriorityResolver.Fallback;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/** Keeps the hunger bar topped up. All of the thinking lives in {@link AutoEat}. */
public final class AutoEatModule extends Module implements EatSettings {
	/**
	 * Food you would not want eaten for you. Most of it hurts; chorus fruit does not, but it
	 * teleports you somewhere random, which halfway across a bridge or mid-fight is worse.
	 * Shipped blocked; the user can unblock any of it.
	 */
	private static final List<String> BLOCKED_BY_DEFAULT = List.of(
		"minecraft:rotten_flesh",
		"minecraft:spider_eye",
		"minecraft:poisonous_potato",
		"minecraft:pufferfish",
		"minecraft:chicken",
		"minecraft:suspicious_stew",
		"minecraft:chorus_fruit");

	// The headings are sections: menu only. Every setting keeps the id it was saved under
	// before there were headings, so existing profiles load as they are.
	private final SettingGroup trigger = settings()
		.add(SettingGroup.section("trigger", "autoeat.group.trigger"));

	private final EnumSetting<ThresholdMode> thresholdMode = trigger
		.add(new EnumSetting<>("threshold_mode", "autoeat.threshold_mode", ThresholdMode.FIXED));

	private final IntSetting threshold = trigger
		.add(new IntSetting("threshold", "autoeat.threshold", 17, 1, 19));

	private final BoolSetting eatEarlyWhenHurt = trigger
		.add(new BoolSetting("eat_early_when_hurt", "autoeat.eat_early_when_hurt", false));

	private final IntSetting earlyEatingHealth = trigger
		.add(new IntSetting("early_eating_health", "autoeat.early_eating_health", 14, 1, 19));

	private final SettingGroup food = settings()
		.add(SettingGroup.section("food", "autoeat.group.food"));

	private final RegistryListSetting priority = food
		.add(new RegistryListSetting("priority", "autoeat.priority", List.of(), RegistryListSetting.Kind.FOOD));

	private final RegistryListSetting blocked = food
		.add(new RegistryListSetting("blocked", "autoeat.blocked", BLOCKED_BY_DEFAULT, RegistryListSetting.Kind.FOOD));

	private final EnumSetting<Fallback> fallback = food
		.add(new EnumSetting<>("fallback", "autoeat.fallback", Fallback.HIGHEST_NUTRITION));

	private final SettingGroup goldenApples = settings()
		.add(SettingGroup.section("golden_apples", "autoeat.group.golden_apples"));

	private final BoolSetting goldenApple = goldenApples
		.add(new BoolSetting("golden_apple", "autoeat.golden_apple", false));

	private final IntSetting healthThreshold = goldenApples
		.add(new IntSetting("health_threshold", "autoeat.health_threshold", 8, 1, 19));

	private final BoolSetting enchantedGoldenApple = goldenApples
		.add(new BoolSetting("enchanted_golden_apple", "autoeat.enchanted_golden_apple", false));

	private final SettingGroup hands = settings()
		.add(SettingGroup.section("hands", "autoeat.group.hands"));

	private final EnumSetting<InventoryUtil.Scope> scope = hands
		.add(new EnumSetting<>("scope", "autoeat.scope", InventoryUtil.Scope.HOTBAR));

	private final EnumSetting<EatingHand> eatingHand = hands
		.add(new EnumSetting<>("eating_hand", "autoeat.eating_hand", EatingHand.MAIN_HAND));

	private final BoolSetting keepOffhandTotem = hands
		.add(new BoolSetting("keep_offhand_totem", "autoeat.keep_offhand_totem", true));

	private final BoolSetting returnToSlot = hands
		.add(new BoolSetting("return_to_slot", "autoeat.return_to_slot", true));

	private final AutoEat autoEat = new AutoEat(this, SurvivalKit.hands());
	private EatContext client;

	public AutoEatModule() {
		super("autoeat", Category.PLAYER, "key.keyboard.k");

		// A fixed line is only drawn in fixed mode; automatic works its own out from the food.
		threshold.setVisibleWhen(() -> thresholdMode.get() == ThresholdMode.FIXED);
		earlyEatingHealth.setVisibleWhen(eatEarlyWhenHurt::get);

		// Both of these only mean anything while the golden apple rule is on.
		healthThreshold.setVisibleWhen(goldenApple::get);
		enchantedGoldenApple.setVisibleWhen(goldenApple::get);

		// Before the game reads the keys, not only with the other modules at the end of the
		// tick: a bite the server finishes ends in between, with the use key still down.
		ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
			if (isEnabled() && minecraft.player != null) {
				autoEat.beforeKeysAreRead(client());
			}
		});

		// There is no totem to protect in a hand the food never goes near.
		keepOffhandTotem.setVisibleWhen(() -> eatingHand.get() == EatingHand.OFF_HAND);
	}

	@Override
	public EatingHand eatingHand() {
		return eatingHand.get();
	}

	@Override
	public boolean keepOffhandTotem() {
		return keepOffhandTotem.get();
	}

	@Override
	public ThresholdMode thresholdMode() {
		return thresholdMode.get();
	}

	@Override
	public boolean eatEarlyWhenHurt() {
		return eatEarlyWhenHurt.get();
	}

	@Override
	public int earlyEatingHealth() {
		return earlyEatingHealth.get();
	}

	@Override
	public int hungerThreshold() {
		return threshold.get();
	}

	@Override
	public List<String> priority() {
		return priority.get();
	}

	@Override
	public List<String> blocked() {
		return blocked.get();
	}

	@Override
	public Fallback fallback() {
		return fallback.get();
	}

	@Override
	public boolean goldenApple() {
		return goldenApple.get();
	}

	@Override
	public boolean enchantedGoldenApple() {
		return enchantedGoldenApple.get();
	}

	@Override
	public int healthThreshold() {
		return healthThreshold.get();
	}

	@Override
	public boolean returnToSlot() {
		return returnToSlot.get();
	}

	@Override
	protected void onTick() {
		autoEat.tick(client());
	}

	@Override
	protected void onDisable() {
		autoEat.stop(client());
	}

	/** Built on first use: at construction time the client is not up yet. */
	private EatContext client() {
		if (this.client == null) {
			this.client = new ClientEatContext(Minecraft.getInstance(), scope::get);
		}

		return this.client;
	}
}
