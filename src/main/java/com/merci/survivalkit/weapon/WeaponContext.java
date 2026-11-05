package com.merci.survivalkit.weapon;

import java.util.List;
import java.util.Optional;

/** What {@link AutoWeapon} may ask of the running client, and what it may tell it to do. */
public interface WeaponContext {
	/**
	 * A living thing Oto Silah might fight.
	 *
	 * @param id       the entity's id in the level
	 * @param player   it is a player rather than a mob
	 * @param kind     what the game's entity tags say about it
	 * @param distance from the player's eyes, in blocks
	 * @param hostile  a monster by the game's own reckoning — zombies, spiders, phantoms; not
	 *                 cows, villagers or iron golems
	 */
	record Target(int id, boolean player, TargetKind kind, double distance, boolean hostile) {
	}

	/** False while there is no player or a screen is open. */
	boolean ready();

	/** The mob or player under the crosshair, if there is one. */
	Optional<Target> aimed();

	/** Every mob and player within this many blocks, whether or not it is aimed at. */
	List<Target> around(double range);

	/** Every sword and axe in the inventory, hotbar and backpack, measured. */
	List<WeaponSlot> weapons();

	/** The main hand holds a sword or an axe. */
	boolean holdingWeapon();

	int selectedSlot();

	/**
	 * Another module's item sits in this inventory slot — Yapı Modu's wand while it is on — and
	 * stays there: it is not a weapon to choose, and with it in hand the hand is left alone.
	 */
	boolean reserved(int slot);

	void selectSlot(int slot);

	/** Exchanges the contents of two inventory slots. */
	void swap(int inventorySlot, int hotbarSlot);

	/** The attack cooldown is full: a hit now lands at full strength. */
	boolean attackReady();

	/** Hits this entity, the same call a click on it makes. */
	void attack(int entityId);

	/** The player hit something since the last time this was asked. Asking clears it. */
	boolean hitSomethingSinceLastTick();

	/** The player took damage since the last time this was asked. Asking clears it. */
	boolean hurtSinceLastTick();

	/** The level's tick count, to time the fight lock by. */
	long gameTime();
}
