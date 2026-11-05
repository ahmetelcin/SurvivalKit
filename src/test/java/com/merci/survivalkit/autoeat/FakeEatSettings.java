package com.merci.survivalkit.autoeat;

import com.merci.survivalkit.util.ItemPriorityResolver.Fallback;
import java.util.List;

/** The settings as plain fields, so a test can change one mid-run the way the menu would. */
final class FakeEatSettings implements EatSettings {
	ThresholdMode thresholdMode = ThresholdMode.FIXED;
	int hungerThreshold = 17;
	boolean eatEarlyWhenHurt;
	int earlyEatingHealth = 14;
	List<String> priority = List.of();
	List<String> blocked = List.of();
	Fallback fallback = Fallback.HIGHEST_NUTRITION;
	boolean goldenApple;
	boolean enchantedGoldenApple;
	int healthThreshold = 8;
	boolean returnToSlot = true;
	EatingHand eatingHand = EatingHand.MAIN_HAND;
	boolean keepOffhandTotem = true;

	@Override
	public ThresholdMode thresholdMode() {
		return thresholdMode;
	}

	@Override
	public boolean eatEarlyWhenHurt() {
		return eatEarlyWhenHurt;
	}

	@Override
	public int earlyEatingHealth() {
		return earlyEatingHealth;
	}

	@Override
	public int hungerThreshold() {
		return hungerThreshold;
	}

	@Override
	public List<String> priority() {
		return priority;
	}

	@Override
	public List<String> blocked() {
		return blocked;
	}

	@Override
	public Fallback fallback() {
		return fallback;
	}

	@Override
	public boolean goldenApple() {
		return goldenApple;
	}

	@Override
	public boolean enchantedGoldenApple() {
		return enchantedGoldenApple;
	}

	@Override
	public int healthThreshold() {
		return healthThreshold;
	}

	@Override
	public boolean returnToSlot() {
		return returnToSlot;
	}

	@Override
	public EatingHand eatingHand() {
		return eatingHand;
	}

	@Override
	public boolean keepOffhandTotem() {
		return keepOffhandTotem;
	}
}
