package com.merci.survivalkit.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.weapon.WeaponContext.Target;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Oto Silah, step 2: the best weapon for whatever is under the crosshair, and the auto hit. */
class AutoWeaponAimTest {
	private static final int PLAIN = 0;
	private static final int SMITE = 1;
	private static final int BANE = 2;
	private static final int SHARP = 3;

	private static final Target ZOMBIE = new Target(7, false, new TargetKind(true, false), 3, true);
	private static final Target SPIDER = new Target(8, false, new TargetKind(false, true), 3, true);
	private static final Target COW = new Target(9, false, new TargetKind(false, false), 3, false);
	private static final Target PLAYER = new Target(10, true, new TargetKind(false, false), 3, false);

	private final FakeWeaponSettings settings = new FakeWeaponSettings();
	private final Hands hands = new Hands();
	private final AutoWeapon autoWeapon = new AutoWeapon(settings, hands);
	private final FakeWeaponContext client = new FakeWeaponContext();

	@BeforeEach
	void aPlainSwordInHandAndThreeEnchantedOnes() {
		client.inventory.put(PLAIN, new WeaponSlot(PLAIN, WeaponKind.SWORD, 6, 0, 0, 0, WeaponSlot.UNBREAKABLE, "minecraft:iron_sword"));
		client.inventory.put(SMITE, new WeaponSlot(SMITE, WeaponKind.SWORD, 6, 0, 5, 0, WeaponSlot.UNBREAKABLE, "minecraft:iron_sword"));
		client.inventory.put(BANE, new WeaponSlot(BANE, WeaponKind.SWORD, 6, 0, 0, 5, WeaponSlot.UNBREAKABLE, "minecraft:iron_sword"));
		client.inventory.put(SHARP, new WeaponSlot(SHARP, WeaponKind.SWORD, 6, 5, 0, 0, WeaponSlot.UNBREAKABLE, "minecraft:iron_sword"));
		client.selected = PLAIN;
	}

	private void aimAt(Target target) {
		client.aimed = Optional.of(target);
		autoWeapon.tick(client);
	}

	@Test
	void aZombieUnderTheCrosshairGetsTheSmiteSword() {
		aimAt(ZOMBIE);

		assertEquals(SMITE, client.selected);
	}

	@Test
	void aSpiderGetsTheBaneSword() {
		aimAt(SPIDER);

		assertEquals(BANE, client.selected);
	}

	@Test
	void aCowGetsTheSharpnessSword() {
		aimAt(COW);

		assertEquals(SHARP, client.selected);
	}

	@Test
	void theSwitchHappensOnTheFirstTickTheTargetIsThere() {
		autoWeapon.tick(client);
		assertEquals(List.of(), client.selections);

		aimAt(ZOMBIE);
		assertEquals(List.of(SMITE), client.selections);
	}

	@Test
	void stayingOnTheSameTargetDoesNotSwitchAgain() {
		aimAt(ZOMBIE);
		aimAt(ZOMBIE);
		aimAt(ZOMBIE);

		assertEquals(List.of(SMITE), client.selections);
	}

	@Test
	void nothingUnderTheCrosshairChangesNothing() {
		autoWeapon.tick(client);

		assertEquals(List.of(), client.selections);
	}

	@Test
	void withoutAWeaponInHandNothingIsTaken() {
		client.holdingWeapon = false;
		aimAt(ZOMBIE);

		assertEquals(List.of(), client.selections);
	}

	@Test
	void onlyTheHotbarIsLookedAtWhenAiming() {
		client.inventory.remove(SMITE);
		client.inventory.put(20, new WeaponSlot(20, WeaponKind.SWORD, 6, 0, 5, 0, WeaponSlot.UNBREAKABLE, "minecraft:iron_sword"));
		aimAt(ZOMBIE);

		// The Smite sword in the backpack is not reached for; of the hotbar, Sharpness is best.
		assertEquals(List.of(), client.swaps);
		assertEquals(SHARP, client.selected);
	}

	@Test
	void theHandIsOnlyHeldForTheMomentOfTheSwitch() {
		aimAt(ZOMBIE);

		assertFalse(hands.holds(autoWeapon));
	}

	@Test
	void nothingMovesWhileSomethingMoreUrgentHoldsTheHand() {
		hands.take(() -> { }, Hands.Priority.AUTO_EAT, Hands.Claim.MAIN_HAND);
		aimAt(ZOMBIE);

		assertEquals(List.of(), client.selections);
	}

	@Test
	void itOutranksOtomatikAlet() {
		Hands.Owner autoTool = () -> { };
		hands.take(autoTool, Hands.Priority.AUTO_TOOL, Hands.Claim.MAIN_HAND);
		aimAt(ZOMBIE);

		assertEquals(SMITE, client.selected);
	}

	@Test
	void whileNotReadyNothingHappens() {
		client.ready = false;
		aimAt(ZOMBIE);

		assertEquals(List.of(), client.selections);
	}

	// --- who counts as a target ---

	@Test
	void playersAreLeftAloneByDefault() {
		aimAt(PLAYER);

		assertEquals(List.of(), client.selections);
	}

	@Test
	void playersCountWhenTheSettingSaysSo() {
		settings.targets = WeaponTargets.MOBS_AND_PLAYERS;
		aimAt(PLAYER);

		assertEquals(SHARP, client.selected);
	}

	@Test
	void theWeaponTypesAndThresholdAreHonoured() {
		client.inventory.put(SMITE, new WeaponSlot(SMITE, WeaponKind.SWORD, 6, 0, 5, 0, 3, "minecraft:iron_sword"));
		client.inventory.put(5, new WeaponSlot(5, WeaponKind.AXE, 9, 0, 5, 0, WeaponSlot.UNBREAKABLE, "minecraft:iron_axe"));
		aimAt(ZOMBIE);

		// The Smite sword is worn and the Smite axe is not a sword: Sharpness is what is left.
		assertEquals(SHARP, client.selected);
	}

	// --- the auto hit ---

	@Test
	void theAutoHitIsOffByDefault() {
		aimAt(ZOMBIE);
		aimAt(ZOMBIE);

		assertEquals(List.of(), client.attacks);
	}

	@Test
	void withTheAutoHitOnTheTargetIsHitWhenTheCooldownIsFull() {
		settings.autoAttack = true;
		aimAt(ZOMBIE);
		aimAt(ZOMBIE);

		assertEquals(List.of(ZOMBIE.id()), client.attacks);
	}

	@Test
	void theAutoHitNeverSwingsBeforeTheCooldownIsFull() {
		settings.autoAttack = true;
		client.attackReady = false;
		aimAt(ZOMBIE);
		aimAt(ZOMBIE);
		aimAt(ZOMBIE);

		assertEquals(List.of(), client.attacks);
	}

	@Test
	void theAutoHitWaitsForTheRightWeaponFirst() {
		// The tick it switches is not the tick it swings: the switch resets the cooldown.
		settings.autoAttack = true;
		aimAt(ZOMBIE);

		assertEquals(List.of(SMITE), client.selections);
		assertEquals(List.of(), client.attacks);
	}

	@Test
	void theAutoHitLeavesPlayersAloneByDefault() {
		settings.autoAttack = true;
		aimAt(PLAYER);

		assertEquals(List.of(), client.attacks);
	}

	@Test
	void theAutoHitNeedsAWeaponInHand() {
		settings.autoAttack = true;
		client.holdingWeapon = false;
		aimAt(ZOMBIE);

		assertEquals(List.of(), client.attacks);
	}

	@Test
	void settingDefaults() {
		FakeWeaponSettings defaults = new FakeWeaponSettings();

		assertEquals(WeaponTypes.SWORDS, defaults.types);
		assertEquals(WeaponTargets.MOBS, defaults.targets);
		assertFalse(defaults.autoAttack);
		assertTrue(defaults.lockAim);
	}
}
