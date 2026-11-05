package com.merci.survivalkit.weapon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * A hand-driven stand-in for the client: a slot-to-weapon map that really moves when swapped,
 * a selection, and whatever is under the crosshair or walking up.
 */
final class FakeWeaponContext implements WeaponContext {
	boolean ready = true;
	boolean holdingWeapon = true;
	boolean attackReady = true;
	Optional<Target> aimed = Optional.empty();
	/** Everyone around, at any distance; {@link #around} keeps those in range. */
	List<Target> around = List.of();
	int selected;
	/** Slots holding another module's item — the wand. */
	final Set<Integer> reserved = new HashSet<>();
	boolean hitSomething;
	boolean wasHurt;
	long now;
	/** Slot to weapon, with the weapon's own slot field ignored: this map says where it is. */
	final Map<Integer, WeaponSlot> inventory = new TreeMap<>();

	final List<Integer> selections = new ArrayList<>();
	final List<String> swaps = new ArrayList<>();
	final List<Integer> attacks = new ArrayList<>();

	@Override
	public boolean ready() {
		return ready;
	}

	@Override
	public Optional<Target> aimed() {
		return aimed;
	}

	@Override
	public List<Target> around(double range) {
		return around.stream().filter(target -> target.distance() <= range).toList();
	}

	@Override
	public List<WeaponSlot> weapons() {
		List<WeaponSlot> weapons = new ArrayList<>();
		inventory.forEach((slot, weapon) -> weapons.add(new WeaponSlot(slot, weapon.kind(), weapon.damage(),
			weapon.sharpness(), weapon.smite(), weapon.bane(), weapon.durabilityLeft(), weapon.item())));
		return weapons;
	}

	@Override
	public boolean holdingWeapon() {
		return holdingWeapon;
	}

	@Override
	public boolean reserved(int slot) {
		return reserved.contains(slot);
	}

	@Override
	public int selectedSlot() {
		return selected;
	}

	@Override
	public void selectSlot(int slot) {
		selections.add(slot);
		selected = slot;
	}

	@Override
	public void swap(int inventorySlot, int hotbarSlot) {
		swaps.add(inventorySlot + "->" + hotbarSlot);
		WeaponSlot from = inventory.remove(inventorySlot);
		WeaponSlot to = inventory.remove(hotbarSlot);

		if (from != null) {
			inventory.put(hotbarSlot, from);
		}

		if (to != null) {
			inventory.put(inventorySlot, to);
		}
	}

	@Override
	public boolean attackReady() {
		return attackReady;
	}

	@Override
	public void attack(int entityId) {
		attacks.add(entityId);
	}

	@Override
	public boolean hitSomethingSinceLastTick() {
		boolean hit = hitSomething;
		hitSomething = false;
		return hit;
	}

	@Override
	public boolean hurtSinceLastTick() {
		boolean hurt = wasHurt;
		wasHurt = false;
		return hurt;
	}

	@Override
	public long gameTime() {
		return now;
	}
}
