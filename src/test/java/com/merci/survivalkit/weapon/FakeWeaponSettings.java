package com.merci.survivalkit.weapon;

/** Settings as plain fields, with the same defaults the module ships with. */
final class FakeWeaponSettings implements WeaponSettings {
	WeaponTypes types = WeaponTypes.SWORDS;
	int durabilityThreshold = 10;
	WeaponTargets targets = WeaponTargets.MOBS;
	boolean autoAttack;
	int prepSlot;
	boolean selectPrepSlot;
	WeaponSource source = WeaponSource.AIM_THEN_CROWD;
	int range = 8;
	int lockSeconds = 3;
	boolean lockAim = true;

	@Override
	public WeaponTypes types() {
		return types;
	}

	@Override
	public int durabilityThreshold() {
		return durabilityThreshold;
	}

	@Override
	public WeaponTargets targets() {
		return targets;
	}

	@Override
	public boolean autoAttack() {
		return autoAttack;
	}

	@Override
	public int prepSlot() {
		return prepSlot;
	}

	@Override
	public boolean selectPrepSlot() {
		return selectPrepSlot;
	}

	@Override
	public WeaponSource source() {
		return source;
	}

	@Override
	public int range() {
		return range;
	}

	@Override
	public int lockSeconds() {
		return lockSeconds;
	}

	@Override
	public boolean lockAim() {
		return lockAim;
	}
}
