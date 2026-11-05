package com.merci.survivalkit.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.weapon.WeaponContext.Target;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The selection source: with nothing aimed at, the weapon the hostile mobs around call for most
 * comes out — but only once that majority has lasted a second, and then it stays.
 */
class AutoWeaponCrowdTest {
	private static final int PLAIN = 0;
	private static final int SMITE = 1;
	private static final int BANE = 2;
	private static final int SHARP = 3;

	private static final TargetKind UNDEAD = new TargetKind(true, false);
	private static final TargetKind ARTHROPOD = new TargetKind(false, true);
	private static final TargetKind OTHER = new TargetKind(false, false);

	private final FakeWeaponSettings settings = new FakeWeaponSettings();
	private final Hands hands = new Hands();
	private final AutoWeapon autoWeapon = new AutoWeapon(settings, hands);
	private final FakeWeaponContext client = new FakeWeaponContext();

	@BeforeEach
	void aPlainSwordInHandAndThreeEnchantedOnes() {
		client.inventory.put(PLAIN, sword(0, 0, 0));
		client.inventory.put(SMITE, sword(0, 5, 0));
		client.inventory.put(BANE, sword(0, 0, 5));
		client.inventory.put(SHARP, sword(5, 0, 0));
		client.selected = PLAIN;
	}

	private static WeaponSlot sword(int sharpness, int smite, int bane) {
		return new WeaponSlot(0, WeaponKind.SWORD, 6, sharpness, smite, bane, WeaponSlot.UNBREAKABLE, "minecraft:iron_sword");
	}

	private static Target zombie(int id, double distance) {
		return new Target(id, false, UNDEAD, distance, true);
	}

	private static Target spider(int id, double distance) {
		return new Target(id, false, ARTHROPOD, distance, true);
	}

	private static Target creeper(int id, double distance) {
		return new Target(id, false, OTHER, distance, true);
	}

	private static Target cow(int id, double distance) {
		return new Target(id, false, OTHER, distance, false);
	}

	private void ticks(int count) {
		for (int i = 0; i < count; i++) {
			client.now++;
			autoWeapon.tick(client);
		}
	}

	/** Long enough for a majority to be taken: a second and a tick. */
	private void settle() {
		ticks(Crowd.HOLD_TICKS + 1);
	}

	private void around(Target... targets) {
		client.around = List.of(targets);
	}

	private final List<Target> threeZombiesAndANearSpider = List.of(
		spider(10, 2), zombie(11, 4), zombie(12, 5), zombie(13, 6));

	@Test
	void theSourceDefaultsToAimThenCrowd() {
		assertEquals(WeaponSource.AIM_THEN_CROWD, new FakeWeaponSettings().source);
	}

	/** The other tests count in {@link Crowd#HOLD_TICKS}; this one says what it is. */
	@Test
	void aNewMajorityHasToHoldForASecond() {
		assertEquals(20, Crowd.HOLD_TICKS);
	}

	@Test
	void threeZombiesAndASpiderNothingAimedGetTheSmiteSword() {
		client.around = threeZombiesAndANearSpider;
		settle();

		assertEquals(SMITE, client.selected);
	}

	@Test
	void theMajorityWaitsASecondBeforeTheHandChanges() {
		client.around = threeZombiesAndANearSpider;
		ticks(Crowd.HOLD_TICKS);

		assertEquals(List.of(), client.selections);

		ticks(1);
		assertEquals(List.of(SMITE), client.selections);
	}

	@Test
	void anAimedSpiderComesFirstAndTheCrowdAfterIt() {
		client.around = threeZombiesAndANearSpider;
		settle();

		client.aimed = Optional.of(spider(10, 2));
		ticks(1);
		assertEquals(BANE, client.selected);

		client.aimed = Optional.empty();
		ticks(1);
		assertEquals(SMITE, client.selected);
	}

	@Test
	void aMajorityThatComesAndGoesInUnderASecondChangesNothing() {
		client.around = threeZombiesAndANearSpider;
		settle();

		around(spider(10, 2), spider(14, 3), spider(15, 3), spider(16, 3), zombie(11, 4), zombie(12, 5), zombie(13, 6));
		ticks(Crowd.HOLD_TICKS - 1);
		client.around = threeZombiesAndANearSpider;
		ticks(Crowd.HOLD_TICKS * 2);

		assertEquals(List.of(SMITE), client.selections);
	}

	@Test
	void aMajorityThatLastsASecondChangesTheWeapon() {
		client.around = threeZombiesAndANearSpider;
		settle();

		around(spider(10, 2), spider(14, 3), spider(15, 3), spider(16, 3), zombie(11, 4), zombie(12, 5), zombie(13, 6));
		settle();

		assertEquals(List.of(SMITE, BANE), client.selections);
	}

	@Test
	void theCountRestartsWhenTheNewMajorityBreaksOff() {
		client.around = threeZombiesAndANearSpider;
		settle();

		List<Target> spiders = List.of(spider(10, 2), spider(14, 3), spider(15, 3), spider(16, 3),
			zombie(11, 4), zombie(12, 5), zombie(13, 6));
		client.around = spiders;
		ticks(Crowd.HOLD_TICKS - 1);
		client.around = threeZombiesAndANearSpider;
		ticks(1);
		client.around = spiders;
		ticks(Crowd.HOLD_TICKS - 1);

		assertEquals(List.of(SMITE), client.selections);
	}

	@Test
	void onceChosenTheWeaponStaysWhenTheCrowdIsGone() {
		client.around = threeZombiesAndANearSpider;
		settle();
		around();
		settle();

		assertEquals(List.of(SMITE), client.selections);
	}

	@Test
	void withTheCrowdGoneASwordPickedByHandIsLeftInHand() {
		client.around = threeZombiesAndANearSpider;
		settle();
		around();
		client.selected = SHARP;
		settle();

		assertEquals(SHARP, client.selected);
	}

	@Test
	void theSameMajorityComingBackNeedsNoNewSecond() {
		client.around = threeZombiesAndANearSpider;
		settle();
		around();
		client.selected = PLAIN;
		settle();

		client.around = threeZombiesAndANearSpider;
		ticks(1);
		assertEquals(SMITE, client.selected);
	}

	@Test
	void aTieGoesToTheNearestMob() {
		around(zombie(11, 4), zombie(12, 5), spider(10, 2), spider(14, 6));
		settle();

		assertEquals(BANE, client.selected);
	}

	@Test
	void mobsWhoseBestWeaponIsTheSameCountTogether() {
		// Without a Bane sword the spider's best is Sharpness, the same as the creeper's: the two
		// count as one side and tie the two zombies, and the spider is the nearest. Counted by
		// kind instead, the zombies would win two to one.
		client.inventory.remove(BANE);
		around(spider(10, 2), creeper(20, 5), zombie(11, 3), zombie(12, 4));
		settle();

		assertEquals(SHARP, client.selected);
	}

	@Test
	void cowsAndVillagersAreNotCounted() {
		around(cow(30, 2), cow(31, 2), cow(32, 2), cow(33, 2), zombie(11, 5));
		settle();

		assertEquals(SMITE, client.selected);
	}

	@Test
	void playersAreNotCounted() {
		settings.targets = WeaponTargets.MOBS_AND_PLAYERS;
		Target player = new Target(40, true, OTHER, 2, false);
		around(player, new Target(41, true, OTHER, 2, true), zombie(11, 5));
		settle();

		assertEquals(SMITE, client.selected);
	}

	@Test
	void mobsBeyondTheRangeAreNotCounted() {
		around(zombie(11, 4), spider(10, 9), spider(14, 9), spider(15, 9));
		settle();

		assertEquals(SMITE, client.selected);
	}

	@Test
	void theRangeIsASetting() {
		settings.range = 10;
		around(zombie(11, 4), spider(10, 9), spider(14, 9), spider(15, 9));
		settle();

		assertEquals(BANE, client.selected);
	}

	@Test
	void withAimOnlyTheCrowdNeverChangesTheHand() {
		settings.source = WeaponSource.AIM_ONLY;
		client.around = threeZombiesAndANearSpider;
		settle();

		assertEquals(List.of(), client.selections);
	}

	@Test
	void withoutAWeaponInHandTheCrowdChangesNothing() {
		client.holdingWeapon = false;
		client.around = threeZombiesAndANearSpider;
		settle();

		assertEquals(List.of(), client.selections);
	}

	@Test
	void theCrowdNeverSwingsOnItsOwn() {
		settings.autoAttack = true;
		client.around = threeZombiesAndANearSpider;
		settle();

		assertEquals(List.of(), client.attacks);
	}
}
