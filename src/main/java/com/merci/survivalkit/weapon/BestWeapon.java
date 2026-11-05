package com.merci.survivalkit.weapon;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The "best weapon for this target" rule: which weapon in reach hits it hardest.
 *
 * <p>Pure on purpose, like {@link com.merci.survivalkit.tool.BestTool}: it is handed weapons
 * already measured and a target already classified, so every rule below can be tried without
 * a game. The game decides who counts as undead or as an arthropod, through its entity tags;
 * nothing here keeps a list of mobs.
 *
 * <p>The order of business: weapons of a type the user did not allow are out; worn weapons are
 * out; of the rest, the one with the most damage against this target, and then the lower slot
 * so the same inventory always resolves the same way.
 */
public final class BestWeapon {
	private static final Comparator<WeaponSlot> BY_SLOT = Comparator.comparingInt(WeaponSlot::slot);

	private BestWeapon() {
	}

	/**
	 * @param durabilityThreshold weapons with fewer uses left than this are never chosen;
	 *                            unbreakable ones are never held back by it
	 */
	public static Optional<WeaponSlot> choose(List<WeaponSlot> available, TargetKind target, WeaponTypes types,
			int durabilityThreshold) {
		Objects.requireNonNull(available, "available");
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(types, "types");

		return available.stream()
			.filter(weapon -> types.allows(weapon.kind()))
			.filter(weapon -> !worn(weapon, durabilityThreshold))
			.min(Comparator.comparingDouble((WeaponSlot weapon) -> damage(weapon, target)).reversed()
				.thenComparing(BY_SLOT));
	}

	/**
	 * The damage one full-strength hit does to this target, the vanilla way: the item's attack
	 * damage, plus 0.5 × level + 0.5 for Sharpness against anything, plus 2.5 × level for Smite
	 * or Bane of Arthropods against the targets their tags name.
	 */
	public static double damage(WeaponSlot weapon, TargetKind target) {
		double damage = weapon.damage();

		if (weapon.sharpness() > 0) {
			damage += 0.5 * weapon.sharpness() + 0.5;
		}

		if (target.sensitiveToSmite()) {
			damage += 2.5 * weapon.smite();
		}

		if (target.sensitiveToBane()) {
			damage += 2.5 * weapon.bane();
		}

		return damage;
	}

	private static boolean worn(WeaponSlot weapon, int threshold) {
		return weapon.durabilityLeft() != WeaponSlot.UNBREAKABLE && weapon.durabilityLeft() < threshold;
	}
}
