package com.merci.survivalkit.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.weapon.WeaponContext.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The auto hit's watchdog: it never changes what happens, it only writes to the log when the
 * auto hit sits in one state for over two seconds — so a stall in a real game shows its cause.
 */
class AutoHitWatchTest {
	private static final int PLAIN = 0;
	private static final int SMITE = 1;
	private static final int COOLDOWN_TICKS = 12;

	private static final Target ZOMBIE = new Target(7, false, new TargetKind(true, false), 3, true);

	private final FakeWeaponSettings settings = new FakeWeaponSettings();
	private final Hands hands = new Hands();
	private final List<String> log = new ArrayList<>();
	private final AutoWeapon autoWeapon = new AutoWeapon(settings, hands, log::add);
	private final FakeWeaponContext client = new FakeWeaponContext();

	@BeforeEach
	void theSmiteSwordInHandOnAZombieWithAutoHitOn() {
		client.inventory.put(PLAIN, new WeaponSlot(0, WeaponKind.SWORD, 6, 0, 0, 0, WeaponSlot.UNBREAKABLE, "minecraft:iron_sword"));
		client.inventory.put(SMITE, new WeaponSlot(0, WeaponKind.SWORD, 6, 0, 5, 0, WeaponSlot.UNBREAKABLE, "minecraft:iron_sword"));
		client.selected = SMITE;
		client.aimed = Optional.of(ZOMBIE);
		settings.autoAttack = true;
		settings.lockSeconds = 0;
	}

	private void ticks(int count) {
		for (int i = 0; i < count; i++) {
			autoWeapon.tick(client);
		}
	}

	@Test
	void aCooldownThatNeverFillsIsReportedAfterTwoSeconds() {
		client.attackReady = false;

		ticks(40);
		assertEquals(List.of(), log);

		ticks(1);
		assertEquals(1, log.size());
		assertTrue(log.getFirst().contains("COOLDOWN"), log.getFirst());
	}

	@Test
	void aStallIsReportedOnceNotEveryTick() {
		client.attackReady = false;

		ticks(200);

		assertEquals(1, log.size(), log.toString());
	}

	@Test
	void hittingEveryTickWithoutTheCooldownResettingIsReported() {
		// The attack goes out but nothing happens: the cooldown stays full.
		ticks(41);

		assertEquals(1, log.size(), log.toString());
		assertTrue(log.getFirst().contains("ATTACK"), log.getFirst());
	}

	@Test
	void aSelectionThatKeepsGettingPulledBackIsReported() {
		// Something else puts the plain sword back every tick, and the switch goes out again.
		for (int i = 0; i < 41; i++) {
			client.selected = PLAIN;
			autoWeapon.tick(client);
		}

		assertEquals(1, log.size(), log.toString());
		assertTrue(log.getFirst().contains("SWITCH"), log.getFirst());
	}

	@Test
	void anOrdinaryFightIsNeverReported() {
		// Hit, wait out the cooldown, hit again: for ten seconds.
		for (int i = 0; i < 200; i++) {
			client.attackReady = i % COOLDOWN_TICKS == 0;
			autoWeapon.tick(client);
		}

		assertEquals(17, client.attacks.size());
		assertEquals(List.of(), log);
	}

	@Test
	void theEndOfAReportedStallIsLoggedWithHowLongItLasted() {
		client.attackReady = false;
		ticks(60);

		client.attackReady = true;
		ticks(1);

		assertEquals(2, log.size(), log.toString());
		assertTrue(log.get(1).contains("60"), log.get(1));
	}

	@Test
	void aSecondStallIsReportedAgain() {
		client.attackReady = false;
		ticks(60);
		client.attackReady = true;
		ticks(1);
		client.attackReady = false;
		ticks(60);

		assertEquals(3, log.size(), log.toString());
	}

	@Test
	void withAutoHitOffNothingIsWatched() {
		settings.autoAttack = false;
		client.attackReady = false;

		ticks(200);

		assertEquals(List.of(), log);
	}

	@Test
	void nothingAimedAtIsNotAStall() {
		client.aimed = Optional.empty();

		ticks(200);

		assertEquals(List.of(), log);
	}

	@Test
	void noWeaponInHandIsNotAStall() {
		client.holdingWeapon = false;

		ticks(200);

		assertEquals(List.of(), log);
	}

	@Test
	void anOpenScreenBreaksTheCount() {
		// A second and a half waiting, a screen open for a while, another second and a half.
		client.attackReady = false;
		ticks(30);
		client.ready = false;
		ticks(100);
		client.ready = true;
		ticks(30);

		assertEquals(List.of(), log);
	}

	@Test
	void lookingAwayBreaksTheCount() {
		client.attackReady = false;
		ticks(30);
		client.aimed = Optional.empty();
		ticks(1);
		client.aimed = Optional.of(ZOMBIE);
		ticks(30);

		assertEquals(List.of(), log);
	}

	@Test
	void theWatchNeverChangesWhatHappens() {
		ticks(41);

		assertEquals(41, client.attacks.size());
		assertEquals(List.of(), client.selections);
	}
}
