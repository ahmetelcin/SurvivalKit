package com.merci.survivalkit.weapon;

/** Which weapons Oto Silah may reach for. */
public enum WeaponTypes {
	SWORDS,
	AXES,
	BOTH;

	public boolean allows(WeaponKind kind) {
		return switch (this) {
			case SWORDS -> kind == WeaponKind.SWORD;
			case AXES -> kind == WeaponKind.AXE;
			case BOTH -> true;
		};
	}
}
