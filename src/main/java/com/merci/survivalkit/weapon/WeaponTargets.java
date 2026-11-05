package com.merci.survivalkit.weapon;

/** Who Oto Silah picks a weapon for. */
public enum WeaponTargets {
	/** Mobs only. The default. */
	MOBS,
	/** Mobs and other players. */
	MOBS_AND_PLAYERS;

	public boolean allows(WeaponContext.Target target) {
		return !target.player() || this == MOBS_AND_PLAYERS;
	}
}
