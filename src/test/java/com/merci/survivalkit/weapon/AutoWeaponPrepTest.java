package com.merci.survivalkit.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.weapon.WeaponContext.Target;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Oto Silah, step 3: the best weapon for whatever is walking up, readied in a slot of the
 * user's choosing before it is aimed at — without the item in hand changing.
 */
class AutoWeaponPrepTest {
	private static final int HAND = 0;
	/** "Slot 5" in the settings is index 4. */
	private static final int PREP_SETTING = 5;
	private static final int PREP = PREP_SETTING - 1;
	private static final int SMITE_IN_BACKPACK = 20;
	private static final int BANE_IN_BACKPACK = 21;

	private static final Target ZOMBIE = new Target(7, false, new TargetKind(true, false), 5, true);
	private static final Target SPIDER = new Target(8, false, new TargetKind(false, true), 5, true);
	private static final Target PLAYER = new Target(10, true, new TargetKind(false, false), 5, false);

	private final FakeWeaponSettings settings = new FakeWeaponSettings();
	private final Hands hands = new Hands();
	private final AutoWeapon autoWeapon = new AutoWeapon(settings, hands);
	private final FakeWeaponContext client = new FakeWeaponContext();

	@BeforeEach
	void breadInHandAndTwoEnchantedSwordsInTheBackpack() {
		client.holdingWeapon = false;
		client.selected = HAND;
		client.inventory.put(SMITE_IN_BACKPACK, new WeaponSlot(0, WeaponKind.SWORD, 6, 0, 5, 0, WeaponSlot.UNBREAKABLE, "minecraft:iron_sword"));
		client.inventory.put(BANE_IN_BACKPACK, new WeaponSlot(0, WeaponKind.SWORD, 6, 0, 0, 5, WeaponSlot.UNBREAKABLE, "minecraft:iron_sword"));
		settings.prepSlot = PREP_SETTING;
	}

	/** The target stays around long enough for the choice to settle: a second and a tick. */
	private void near(Target... targets) {
		client.around = List.of(targets);

		for (int i = 0; i <= Crowd.HOLD_TICKS; i++) {
			client.now++;
			autoWeapon.tick(client);
		}
	}

	private int smiteAt() {
		return client.inventory.entrySet().stream()
			.filter(entry -> entry.getValue().smite() > 0)
			.mapToInt(java.util.Map.Entry::getKey)
			.findFirst().orElse(-1);
	}

	@Test
	void theSlotIsOffByDefault() {
		assertEquals(0, new FakeWeaponSettings().prepSlot);
	}

	@Test
	void withTheSlotOffNothingIsReadied() {
		settings.prepSlot = 0;
		near(ZOMBIE);

		assertEquals(List.of(), client.swaps);
	}

	@Test
	void aZombieWalkingUpGetsTheSmiteSwordReadiedInTheSlot() {
		near(ZOMBIE);

		assertEquals(List.of(SMITE_IN_BACKPACK + "->" + PREP), client.swaps);
		assertEquals(PREP, smiteAt());
	}

	@Test
	void theItemInHandStaysPut() {
		near(ZOMBIE);

		assertEquals(HAND, client.selected);
		assertEquals(List.of(), client.selections);
	}

	@Test
	void onceReadiedItIsNotSwappedAgain() {
		near(ZOMBIE);
		near(ZOMBIE);
		near(ZOMBIE);

		assertEquals(1, client.swaps.size());
	}

	@Test
	void aSpiderAfterwardsGetsTheBaneSwordInstead() {
		near(ZOMBIE);
		near(SPIDER);

		assertEquals(PREP, client.inventory.entrySet().stream()
			.filter(entry -> entry.getValue().bane() > 0).mapToInt(java.util.Map.Entry::getKey).findFirst().orElse(-1));
	}

	@Test
	void nothingNearReadiesNothing() {
		autoWeapon.tick(client);

		assertEquals(List.of(), client.swaps);
	}

	@Test
	void aTargetBeyondTheRangeReadiesNothing() {
		settings.range = 4;
		near(ZOMBIE);

		assertEquals(List.of(), client.swaps);
	}

	@Test
	void theDefaultRangeIsEightBlocks() {
		assertEquals(8, new FakeWeaponSettings().range);
	}

	@Test
	void theSlotFollowsTheMajorityNotTheNearest() {
		Target nearSpider = new Target(8, false, new TargetKind(false, true), 2, true);
		near(nearSpider, zombie(20, 6), zombie(21, 6), zombie(22, 6));

		assertEquals(PREP, smiteAt());
	}

	@Test
	void aCowIsNoReasonToReadyAnything() {
		near(new Target(9, false, new TargetKind(false, false), 2, false));

		assertEquals(List.of(), client.swaps);
	}

	@Test
	void theSlotWaitsASecondBeforeReadying() {
		client.around = List.of(ZOMBIE);

		for (int i = 0; i < Crowd.HOLD_TICKS; i++) {
			client.now++;
			autoWeapon.tick(client);
		}

		assertEquals(List.of(), client.swaps);

		client.now++;
		autoWeapon.tick(client);
		assertEquals(PREP, smiteAt());
	}

	private static Target zombie(int id, double distance) {
		return new Target(id, false, new TargetKind(true, false), distance, true);
	}

	@Test
	void playersAreLeftAloneByDefault() {
		near(PLAYER);

		assertEquals(List.of(), client.swaps);
	}

	@Test
	void whenTheSlotIsTheOneInHandItIsLeftAlone() {
		client.selected = PREP;
		near(ZOMBIE);

		assertEquals(List.of(), client.swaps);
	}

	@Test
	void whenTheBestWeaponIsAlreadyInHandItIsNotMovedOutOfIt() {
		client.inventory.remove(SMITE_IN_BACKPACK);
		client.inventory.put(HAND, new WeaponSlot(0, WeaponKind.SWORD, 6, 0, 5, 0, WeaponSlot.UNBREAKABLE, "minecraft:iron_sword"));
		near(ZOMBIE);

		assertEquals(List.of(), client.swaps);
	}

	@Test
	void selectingTheSlotIsOffByDefault() {
		assertFalse(new FakeWeaponSettings().selectPrepSlot);
	}

	@Test
	void withSelectingOnTheReadiedSlotIsTakenInHand() {
		settings.selectPrepSlot = true;
		near(ZOMBIE);

		assertEquals(PREP, client.selected);
	}

	@Test
	void withSelectingOnTheSlotInHandIsReadiedToo() {
		settings.selectPrepSlot = true;
		client.selected = PREP;
		near(ZOMBIE);

		assertEquals(PREP, smiteAt());
	}

	@Test
	void theSwapWaitsWhileSomethingMoreUrgentHoldsTheInventory() {
		hands.take(() -> { }, Hands.Priority.AUTO_EAT, Hands.Claim.INVENTORY);
		near(ZOMBIE);

		assertEquals(List.of(), client.swaps);
	}

	@Test
	void theInventoryIsOnlyHeldForTheMomentOfTheSwap() {
		near(ZOMBIE);

		assertFalse(hands.holds(autoWeapon));
	}
}
