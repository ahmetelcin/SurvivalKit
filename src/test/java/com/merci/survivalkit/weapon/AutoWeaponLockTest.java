package com.merci.survivalkit.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.weapon.WeaponContext.Target;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Oto Silah, step 4: the fight lock. For a while after a hit given or taken, no change is made
 * that would throw away the attack cooldown. The game only resets it when the item in hand
 * becomes a different item ({@code Player.tick}: {@code !ItemStack.isSameItem}), so a Sharpness
 * iron sword for a Smite iron sword is free; and a cooldown that is already full has nothing to
 * lose, so any switch is free then.
 */
class AutoWeaponLockTest {
	private static final int SMITE = 1;
	private static final int SHARP = 3;
	private static final int PREP = 4;
	private static final int SMITE_IN_BACKPACK = 20;

	private static final String IRON = "minecraft:iron_sword";
	private static final String DIAMOND = "minecraft:diamond_sword";

	private static final Target ZOMBIE = new Target(7, false, new TargetKind(true, false), 3, true);
	private static final Target PHANTOM = new Target(8, false, new TargetKind(true, false), 3, true);
	private static final Target COW = new Target(9, false, new TargetKind(false, false), 3, false);

	private final FakeWeaponSettings settings = new FakeWeaponSettings();
	private final Hands hands = new Hands();
	private final AutoWeapon autoWeapon = new AutoWeapon(settings, hands);
	private final FakeWeaponContext client = new FakeWeaponContext();

	/** A Sharpness diamond sword in hand and a Smite iron sword beside it: two different items. */
	@BeforeEach
	void aSharpnessDiamondSwordInHandAndASmiteIronSwordBesideIt() {
		client.inventory.put(SMITE, sword(0, 5, IRON));
		client.inventory.put(SHARP, sword(5, 0, DIAMOND));
		client.selected = SHARP;
		client.now = 1000;
	}

	private static WeaponSlot sword(int sharpness, int smite, String item) {
		return new WeaponSlot(0, WeaponKind.SWORD, 6, sharpness, smite, 0, WeaponSlot.UNBREAKABLE, item);
	}

	/** A hit on the cow, as the tick after it sees it: the cooldown starts over. */
	private void hitTheCow() {
		client.aimed = Optional.of(COW);
		client.hitSomething = true;
		client.attackReady = false;
		autoWeapon.tick(client);
	}

	private void later(int ticks) {
		client.now += ticks;
	}

	private void aimAt(Target target) {
		client.aimed = Optional.of(target);
		autoWeapon.tick(client);
	}

	/** For the HUD: the lock's ticks left, counting down from the hit, none before it or after it runs out. */
	@Test
	void theLockSaysHowLongItHasLeft() {
		assertEquals(0, autoWeapon.lockTicksLeft(client.now));

		hitTheCow();
		assertEquals(60, autoWeapon.lockTicksLeft(client.now));

		later(20);
		assertEquals(40, autoWeapon.lockTicksLeft(client.now));

		later(40);
		assertEquals(0, autoWeapon.lockTicksLeft(client.now));
	}

	@Test
	void defaults() {
		assertEquals(3, new FakeWeaponSettings().lockSeconds);
		assertTrue(new FakeWeaponSettings().lockAim);
	}

	// --- a switch that would reset the cooldown is held back ---

	@Test
	void rightAfterAHitASwitchToAnotherItemWaits() {
		hitTheCow();
		later(5);
		aimAt(ZOMBIE);

		assertEquals(SHARP, client.selected);
	}

	@Test
	void onceTheCooldownIsFullTheSwitchIsFreeEvenWhileLocked() {
		hitTheCow();
		later(5);
		aimAt(ZOMBIE);
		assertEquals(SHARP, client.selected);

		client.attackReady = true;
		later(10);
		aimAt(ZOMBIE);
		assertEquals(SMITE, client.selected);
	}

	@Test
	void onceTheLockRunsOutTheSwitchComesBack() {
		hitTheCow();
		later(3 * 20);
		aimAt(ZOMBIE);

		assertEquals(SMITE, client.selected);
	}

	@Test
	void theLockEndsExactlyWhenTheSecondsAreUp() {
		hitTheCow();
		later(3 * 20 - 1);
		aimAt(ZOMBIE);
		assertEquals(SHARP, client.selected);

		later(1);
		aimAt(ZOMBIE);
		assertEquals(SMITE, client.selected);
	}

	// --- a switch that keeps the cooldown is free ---

	/**
	 * The report from the game: phantoms about, Sharpness in hand, a hit — and the Sharpness
	 * sword stayed for the whole fight. Both swords were the same item, so the switch never
	 * cost the cooldown anything; the lock had no reason to hold it.
	 */
	@Test
	void aSwitchBetweenTwoOfTheSameItemIsFreeDuringTheLock() {
		client.inventory.put(SHARP, sword(5, 0, IRON));
		hitTheCow();
		later(5);
		aimAt(PHANTOM);

		assertEquals(SMITE, client.selected);
	}

	@Test
	void takingAHitLocksToo() {
		client.wasHurt = true;
		client.attackReady = false;
		autoWeapon.tick(client);
		later(20);
		aimAt(ZOMBIE);

		assertEquals(SHARP, client.selected);
	}

	@Test
	void eachHitStartsTheLockAgain() {
		hitTheCow();
		later(50);
		hitTheCow();
		later(50);
		aimAt(ZOMBIE);

		assertEquals(SHARP, client.selected);
	}

	@Test
	void withLockAimOffTheSwitchOnAimCarriesOnDuringTheLock() {
		settings.lockAim = false;
		hitTheCow();
		later(20);
		aimAt(ZOMBIE);

		assertEquals(SMITE, client.selected);
	}

	@Test
	void withTheLockAtZeroNothingIsLocked() {
		settings.lockSeconds = 0;
		hitTheCow();
		aimAt(ZOMBIE);

		assertEquals(SMITE, client.selected);
	}

	@Test
	void anOldHitDoesNotLockLater() {
		// The hit is taken off the client the tick it happens, even with nothing aimed at; a
		// hit that was never read would otherwise lock the first fight after it.
		client.hitSomething = true;
		client.attackReady = false;
		autoWeapon.tick(client);
		later(10 * 20);
		aimAt(ZOMBIE);

		assertEquals(SMITE, client.selected);
	}

	@Test
	void aHitMadeWhileTheModuleWasOffDoesNotLockWhenItComesOn() {
		// Off, the module does not tick, so a hit made then is still waiting on the client when
		// it comes back on. Starting the module clears it: it happened while nobody was watching.
		client.hitSomething = true;
		client.wasHurt = true;
		client.attackReady = false;
		autoWeapon.start(client);
		aimAt(ZOMBIE);

		assertEquals(SMITE, client.selected);
	}

	@Test
	void startingTheModuleEndsALockFromBefore() {
		hitTheCow();
		autoWeapon.start(client);
		later(20);
		aimAt(ZOMBIE);

		assertEquals(SMITE, client.selected);
	}

	@Test
	void underTheLockTheAutoHitWaitsForTheCooldownAndThenTheSwitch() {
		settings.autoAttack = true;
		hitTheCow();
		later(5);
		aimAt(ZOMBIE);
		assertEquals(List.of(), client.attacks);

		// Full now: the switch is free and takes this tick, the hit comes on the next.
		client.attackReady = true;
		aimAt(ZOMBIE);
		assertEquals(List.of(), client.attacks);
		aimAt(ZOMBIE);

		assertEquals(SMITE, client.selected);
		assertEquals(List.of(ZOMBIE.id()), client.attacks);
	}

	@Test
	void theCrowdIsHeldBackTheSameWay() {
		hitTheCow();
		client.aimed = Optional.empty();
		client.around = List.of(ZOMBIE);

		for (int i = 0; i < Crowd.HOLD_TICKS + 1; i++) {
			later(1);
			autoWeapon.tick(client);
		}

		assertEquals(SHARP, client.selected);
	}

	// --- the ready slot ---

	@Test
	void theReadySlotIsFilledDuringTheLockSinceTheHandIsNotTouched() {
		settings.prepSlot = PREP + 1;
		client.inventory.remove(SMITE);
		client.inventory.put(SMITE_IN_BACKPACK, sword(0, 5, IRON));

		client.wasHurt = true;
		client.attackReady = false;
		client.around = List.of(ZOMBIE);

		for (int i = 0; i < Crowd.HOLD_TICKS + 1; i++) {
			later(1);
			autoWeapon.tick(client);
		}

		assertEquals(List.of(SMITE_IN_BACKPACK + "->" + PREP), client.swaps);
	}

	@Test
	void selectingTheReadySlotWaitsIfItWouldResetTheCooldown() {
		settings.prepSlot = PREP + 1;
		settings.selectPrepSlot = true;
		client.inventory.remove(SMITE);
		client.inventory.put(SMITE_IN_BACKPACK, sword(0, 5, IRON));

		client.wasHurt = true;
		client.attackReady = false;
		client.around = List.of(ZOMBIE);

		for (int i = 0; i < Crowd.HOLD_TICKS + 1; i++) {
			later(1);
			autoWeapon.tick(client);
		}

		assertEquals(List.of(SMITE_IN_BACKPACK + "->" + PREP), client.swaps);
		assertEquals(SHARP, client.selected);

		client.attackReady = true;
		autoWeapon.tick(client);
		assertEquals(PREP, client.selected);
	}
}
