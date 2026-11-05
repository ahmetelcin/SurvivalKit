package com.merci.survivalkit.weapon;

/** What the user has asked of Oto Silah, read fresh every tick. */
public interface WeaponSettings {
	/** Which weapons may be reached for. Swords by default. */
	WeaponTypes types();

	/** Weapons with fewer uses left than this are never taken out. */
	int durabilityThreshold();

	/** Mobs only by default; players too when asked. */
	WeaponTargets targets();

	/** What the weapon in hand is picked by. Aim, then the crowd around, by default. */
	WeaponSource source();

	/** How near, in blocks, a hostile mob has to be to count for the crowd and the ready slot. */
	int range();

	/** Whether an aimed target is hit, once the cooldown is full. Off by default. */
	boolean autoAttack();

	/** The hotbar slot, 1-9, the best weapon is readied in; 0 is off. */
	int prepSlot();

	/** Whether the prepared slot is also selected. Off by default: the hand stays as it is. */
	boolean selectPrepSlot();

	/**
	 * Seconds after a hit given or taken during which nothing is put in hand that would reset
	 * the attack cooldown; 0 is off.
	 */
	int lockSeconds();

	/** Whether the fight lock also holds back the switch on aim and the crowd's switch. */
	boolean lockAim();
}
