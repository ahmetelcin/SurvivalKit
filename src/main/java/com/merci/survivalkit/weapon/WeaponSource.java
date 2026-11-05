package com.merci.survivalkit.weapon;

/** What Oto Silah picks the weapon in hand by. */
public enum WeaponSource {
	/** Only what is under the crosshair. */
	AIM_ONLY,
	/** What is under the crosshair; with nothing there, the hostile mobs around. The default. */
	AIM_THEN_CROWD
}
