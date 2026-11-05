package com.merci.survivalkit.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.weapon.WeaponContext.Target;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Yapı Modu's wand is a wooden sword, and Oto Silah leaves it alone: not swapped out of the hand,
 * not swung by the auto hit, not chosen, not moved into or out of the ready slot. Each test has
 * its twin with the wand not reserved, where Oto Silah does act — so it is the reservation that
 * holds it back.
 */
class AutoWeaponWandTest {
	private static final int WAND = 0;
	private static final int SMITE = 1;
	private static final int SMITE_IN_BACKPACK = 20;
	private static final Target ZOMBIE = new Target(7, false, new TargetKind(true, false), 3, true);

	private final FakeWeaponSettings settings = new FakeWeaponSettings();
	private final AutoWeapon autoWeapon = new AutoWeapon(settings, new Hands());
	private final FakeWeaponContext client = new FakeWeaponContext();

	@BeforeEach
	void theWandInHandAndASmiteSwordBesideIt() {
		client.inventory.put(WAND, new WeaponSlot(WAND, WeaponKind.SWORD, 4, 0, 0, 0, WeaponSlot.UNBREAKABLE, "minecraft:wooden_sword"));
		client.inventory.put(SMITE, new WeaponSlot(SMITE, WeaponKind.SWORD, 6, 0, 5, 0, WeaponSlot.UNBREAKABLE, "minecraft:iron_sword"));
		client.selected = WAND;
		client.reserved.add(WAND);
	}

	private void aimAtAZombie(int ticks) {
		client.aimed = Optional.of(ZOMBIE);

		for (int i = 0; i < ticks; i++) {
			client.now++;
			autoWeapon.tick(client);
		}
	}

	@Test
	void aimingAtAZombieWithTheWandInHandKeepsTheWand() {
		settings.autoAttack = true;
		aimAtAZombie(5);

		assertEquals(WAND, client.selected);
		assertEquals(List.of(), client.selections);
		assertEquals(List.of(), client.attacks, "değnekle otomatik vurulmamalı");
	}

	@Test
	void negativeControlWithoutTheReservationTheSwordIsSwappedIn() {
		client.reserved.clear();
		aimAtAZombie(1);

		assertEquals(SMITE, client.selected);
	}

	@Test
	void theWandIsNeverTheWeaponChosen() {
		// The wand made the best weapon there is; the hand holds an ordinary sword.
		client.inventory.put(WAND, new WeaponSlot(WAND, WeaponKind.SWORD, 20, 0, 5, 0, WeaponSlot.UNBREAKABLE, "minecraft:wooden_sword"));
		client.inventory.put(2, new WeaponSlot(2, WeaponKind.SWORD, 5, 0, 0, 0, WeaponSlot.UNBREAKABLE, "minecraft:stone_sword"));
		client.selected = 2;
		aimAtAZombie(1);

		assertEquals(SMITE, client.selected);
	}

	@Test
	void theWandInTheReadySlotStaysThere() {
		settings.prepSlot = WAND + 1;
		client.selected = 5;
		client.holdingWeapon = false;
		client.inventory.remove(SMITE);
		client.inventory.put(SMITE_IN_BACKPACK, new WeaponSlot(0, WeaponKind.SWORD, 6, 0, 5, 0, WeaponSlot.UNBREAKABLE, "minecraft:iron_sword"));
		client.around = List.of(ZOMBIE);

		for (int i = 0; i <= Crowd.HOLD_TICKS; i++) {
			client.now++;
			autoWeapon.tick(client);
		}

		assertEquals(List.of(), client.swaps, "değnek hazırlık slotundan çıkarılmamalı");

		client.reserved.clear();
		client.now++;
		autoWeapon.tick(client);
		assertEquals(1, client.swaps.size(), "negatif kontrol: değnek ayrılmamışken hazırlık slotu doldurulmalı: " + client.swaps);
	}

	@Test
	void theReadySlotIsNotSelectedAwayFromTheWand() {
		settings.prepSlot = 3;
		settings.selectPrepSlot = true;
		client.around = List.of(ZOMBIE);

		for (int i = 0; i <= Crowd.HOLD_TICKS; i++) {
			client.now++;
			autoWeapon.tick(client);
		}

		assertEquals(WAND, client.selected);
	}
}
