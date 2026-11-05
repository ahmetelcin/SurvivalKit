package com.merci.survivalkit.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The shared "best weapon for this target" rule, on its own. Damage is the vanilla attack
 * damage with the player's base included: iron sword 6, diamond sword 7, netherite sword 8,
 * diamond axe 9. Sharpness adds 0.5 × level + 0.5 against anything; Smite and Bane of
 * Arthropods add 2.5 × level, only against the targets the game's tags name.
 */
class BestWeaponTest {
	private static final int THRESHOLD = 10;

	private static final TargetKind ZOMBIE = new TargetKind(true, false);
	private static final TargetKind SPIDER = new TargetKind(false, true);
	private static final TargetKind COW = new TargetKind(false, false);

	private static WeaponSlot sword(int slot, double damage) {
		return new WeaponSlot(slot, WeaponKind.SWORD, damage, 0, 0, 0, WeaponSlot.UNBREAKABLE, "minecraft:iron_sword");
	}

	private static WeaponSlot sword(int slot, double damage, int sharpness, int smite, int bane) {
		return new WeaponSlot(slot, WeaponKind.SWORD, damage, sharpness, smite, bane, WeaponSlot.UNBREAKABLE, "minecraft:iron_sword");
	}

	private static WeaponSlot axe(int slot, double damage) {
		return new WeaponSlot(slot, WeaponKind.AXE, damage, 0, 0, 0, WeaponSlot.UNBREAKABLE, "minecraft:iron_axe");
	}

	private static Optional<WeaponSlot> choose(TargetKind target, WeaponTypes types, WeaponSlot... weapons) {
		return BestWeapon.choose(List.of(weapons), target, types, THRESHOLD);
	}

	private static Optional<WeaponSlot> choose(TargetKind target, WeaponSlot... weapons) {
		return choose(target, WeaponTypes.SWORDS, weapons);
	}

	@Test
	void nothingInReachYieldsNothing() {
		assertTrue(choose(ZOMBIE).isEmpty());
	}

	// --- enchantments by target ---

	@Test
	void aZombieGetsTheSmiteSword() {
		WeaponSlot sharp = sword(0, 6, 5, 0, 0);
		WeaponSlot smite = sword(1, 6, 0, 5, 0);
		WeaponSlot bane = sword(2, 6, 0, 0, 5);

		assertEquals(smite, choose(ZOMBIE, sharp, smite, bane).orElseThrow());
	}

	@Test
	void aSpiderGetsTheBaneSword() {
		WeaponSlot sharp = sword(0, 6, 5, 0, 0);
		WeaponSlot smite = sword(1, 6, 0, 5, 0);
		WeaponSlot bane = sword(2, 6, 0, 0, 5);

		assertEquals(bane, choose(SPIDER, sharp, smite, bane).orElseThrow());
	}

	@Test
	void aCowGetsTheSharpnessSword() {
		WeaponSlot smite = sword(0, 6, 0, 5, 0);
		WeaponSlot bane = sword(1, 6, 0, 0, 5);
		WeaponSlot sharp = sword(2, 6, 5, 0, 0);

		assertEquals(sharp, choose(COW, smite, bane, sharp).orElseThrow());
	}

	@Test
	void smiteCountsForNothingAgainstATargetTheTagDoesNotName() {
		// Smite V is worth 12.5 against a zombie and 0 against a cow; a plain diamond sword wins.
		WeaponSlot smiteIron = sword(0, 6, 0, 5, 0);
		WeaponSlot plainDiamond = sword(1, 7);

		assertEquals(plainDiamond, choose(COW, smiteIron, plainDiamond).orElseThrow());
	}

	@Test
	void sharpnessStillCountsAgainstAZombie() {
		// Neither is Smite: Sharpness I adds 1, so iron 6 + 1 = 7 beats nothing on the 6.
		WeaponSlot plainIron = sword(0, 6);
		WeaponSlot sharpIron = sword(1, 6, 1, 0, 0);

		assertEquals(sharpIron, choose(ZOMBIE, plainIron, sharpIron).orElseThrow());
	}

	@Test
	void theLevelMatters() {
		WeaponSlot smiteOne = sword(0, 6, 0, 1, 0);
		WeaponSlot smiteThree = sword(1, 6, 0, 3, 0);

		assertEquals(smiteThree, choose(ZOMBIE, smiteOne, smiteThree).orElseThrow());
	}

	// --- the item's own damage ---

	@Test
	void theItemsOwnDamageCounts() {
		WeaponSlot iron = sword(0, 6);
		WeaponSlot netherite = sword(1, 8);

		assertEquals(netherite, choose(COW, iron, netherite).orElseThrow());
	}

	@Test
	void damageAndEnchantmentAddUp() {
		// Netherite 8 against iron with Sharpness III: 6 + 2 = 8. A tie, so the lower slot.
		// Sharpness V on the iron: 6 + 3 = 9, and it wins.
		WeaponSlot netherite = sword(0, 8);
		WeaponSlot sharpThree = sword(1, 6, 3, 0, 0);
		WeaponSlot sharpFive = sword(2, 6, 5, 0, 0);

		assertEquals(netherite, choose(COW, netherite, sharpThree).orElseThrow());
		assertEquals(sharpFive, choose(COW, netherite, sharpThree, sharpFive).orElseThrow());
	}

	@Test
	void equalWeaponsResolveToTheLowerSlot() {
		WeaponSlot first = sword(3, 7);
		WeaponSlot second = sword(6, 7);

		assertEquals(first, choose(COW, second, first).orElseThrow());
	}

	// --- weapon types ---

	@Test
	void swordsOnlyByDefaultSoAnAxeIsLeftOut() {
		WeaponSlot sword = sword(0, 7);
		WeaponSlot axe = axe(1, 9);

		assertEquals(sword, choose(COW, WeaponTypes.SWORDS, sword, axe).orElseThrow());
	}

	@Test
	void withAxesOnlyASwordIsLeftOut() {
		WeaponSlot sword = sword(0, 8);
		WeaponSlot axe = axe(1, 7);

		assertEquals(axe, choose(COW, WeaponTypes.AXES, sword, axe).orElseThrow());
	}

	@Test
	void withBothTheHarderHitterWins() {
		WeaponSlot sword = sword(0, 7);
		WeaponSlot axe = axe(1, 9);

		assertEquals(axe, choose(COW, WeaponTypes.BOTH, sword, axe).orElseThrow());
	}

	@Test
	void withOnlyTheWrongTypeInReachNothingIsChosen() {
		assertTrue(choose(COW, WeaponTypes.SWORDS, axe(0, 9)).isEmpty());
	}

	// --- durability ---

	@Test
	void aWeaponBelowTheThresholdIsNeverChosen() {
		WeaponSlot worn = new WeaponSlot(0, WeaponKind.SWORD, 8, 0, 5, 0, THRESHOLD - 1, "minecraft:iron_sword");
		WeaponSlot plain = new WeaponSlot(1, WeaponKind.SWORD, 6, 0, 0, 0, 200, "minecraft:iron_sword");

		assertEquals(plain, choose(ZOMBIE, worn, plain).orElseThrow());
	}

	@Test
	void aWeaponExactlyAtTheThresholdIsStillUsable() {
		WeaponSlot atThreshold = new WeaponSlot(0, WeaponKind.SWORD, 8, 0, 0, 0, THRESHOLD, "minecraft:iron_sword");

		assertEquals(atThreshold, choose(COW, atThreshold).orElseThrow());
	}

	@Test
	void unbreakableWeaponsIgnoreTheThreshold() {
		WeaponSlot unbreakable = sword(0, 8);

		assertEquals(unbreakable, BestWeapon.choose(List.of(unbreakable), COW, WeaponTypes.SWORDS, Integer.MAX_VALUE - 1).orElseThrow());
	}

	// --- the numbers themselves ---

	@Test
	void theDamageIsTheVanillaOne() {
		assertEquals(6 + 3.0, BestWeapon.damage(sword(0, 6, 5, 0, 0), COW), 1e-9);
		assertEquals(6 + 12.5, BestWeapon.damage(sword(0, 6, 0, 5, 0), ZOMBIE), 1e-9);
		assertEquals(6 + 12.5, BestWeapon.damage(sword(0, 6, 0, 0, 5), SPIDER), 1e-9);
		assertEquals(6, BestWeapon.damage(sword(0, 6, 0, 5, 0), SPIDER), 1e-9);
	}
}
