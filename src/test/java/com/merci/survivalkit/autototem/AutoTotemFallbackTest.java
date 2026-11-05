package com.merci.survivalkit.autototem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What happens once the totems run out: the stand-in the player named, and the one line in
 * chat telling them why the hand no longer holds what it used to.
 */
class AutoTotemFallbackTest {
	private static final String TOTEM = AutoTotem.TOTEM;
	private static final String SHIELD = "minecraft:shield";
	private static final String MAP = "minecraft:filled_map";
	private static final int TOTEM_SLOT = 5;
	private static final int SHIELD_SLOT = 3;
	private static final int MAP_SLOT = 8;

	private FakeTotemSettings settings;
	private FakeTotemContext client;
	private Hands hands;
	private AutoTotem autoTotem;

	@BeforeEach
	void setUp() {
		settings = new FakeTotemSettings();
		hands = new Hands();
		autoTotem = new AutoTotem(settings, hands);
		client = new FakeTotemContext();
	}

	// --- the stand-in ---

	@Test
	void withNoTotemTheFallbackItemGoesInTheOffHand() {
		settings.fallback = List.of(SHIELD);
		client.inventory.put(SHIELD_SLOT, SHIELD);

		autoTotem.tick(client);

		assertEquals(List.of(SHIELD_SLOT), client.offhandSwaps);
		assertEquals(SHIELD, client.offhandId);
	}

	@Test
	void theFirstFallbackItemInReachIsTheOneTaken() {
		settings.fallback = List.of(SHIELD, MAP);
		client.inventory.put(MAP_SLOT, MAP);

		autoTotem.tick(client);

		assertEquals(MAP, client.offhandId);
	}

	@Test
	void theListIsAnOrderNotASetSoTheEarlierEntryWins() {
		settings.fallback = List.of(SHIELD, MAP);
		client.inventory.put(MAP_SLOT, MAP);
		client.inventory.put(SHIELD_SLOT, SHIELD);

		autoTotem.tick(client);

		assertEquals(SHIELD, client.offhandId);
	}

	@Test
	void aFallbackItemAlreadyInTheOffHandIsLeftAlone() {
		settings.fallback = List.of(SHIELD);
		client.offhandId = SHIELD;
		client.inventory.put(SHIELD_SLOT, SHIELD);

		autoTotem.tick(client);

		assertEquals(List.of(), client.offhandSwaps);
	}

	@Test
	void aTotemTakesTheFallbackItemsPlaceAsSoonAsOneTurnsUp() {
		settings.fallback = List.of(SHIELD);
		client.inventory.put(SHIELD_SLOT, SHIELD);
		autoTotem.tick(client);
		autoTotem.tick(client);
		client.offhandSwaps.clear();

		client.inventory.put(TOTEM_SLOT, TOTEM);
		autoTotem.tick(client);

		assertEquals(List.of(TOTEM_SLOT), client.offhandSwaps);
		assertEquals(TOTEM, client.offhandId);
	}

	/**
	 * The point of the whole feature: with nothing to put in the hand, the module has no
	 * business holding it. AutoEat eats off hand, and it may do so here.
	 */
	@Test
	void withNothingToPutThereTheOffHandIsLeftForSomebodyElse() {
		settings.fallback = List.of();

		autoTotem.tick(client);

		assertEquals(List.of(), client.offhandSwaps);
		assertTrue(hands.take(new Hands.Owner() {
			@Override
			public void handsTaken() {
			}
		}, Hands.Priority.AUTO_EAT, Hands.Claim.OFF_HAND));
	}

	@Test
	void aFallbackItemThatIsNowhereInReachHoldsNothingEither() {
		settings.fallback = List.of(SHIELD);

		autoTotem.tick(client);

		assertEquals(List.of(), client.offhandSwaps);
		assertTrue(hands.take(new Hands.Owner() {
			@Override
			public void handsTaken() {
			}
		}, Hands.Priority.AUTO_EAT, Hands.Claim.OFF_HAND));
	}

	/**
	 * The swap is waited for by looking at the hand, and the thing being waited for is the
	 * item that was actually sent — not a totem. Waiting for a totem that is never coming
	 * would hold both hands for the whole timeout with the job already done.
	 */
	@Test
	void theFallbackSwapIsSeenWhenItLandsRatherThanWaitedOut() {
		settings.fallback = List.of(SHIELD);
		client.inventory.put(SHIELD_SLOT, SHIELD);

		autoTotem.tick(client);
		autoTotem.tick(client);

		assertTrue(hands.take(new Hands.Owner() {
			@Override
			public void handsTaken() {
			}
		}, Hands.Priority.AUTO_EAT, Hands.Claim.OFF_HAND));
	}

	// --- the warning ---

	@Test
	void theWarningComesWhenTheLastTotemIsGone() {
		autoTotem.tick(client);

		assertEquals(List.of(Lang.of(Keys.TOTEM_OUT)), client.said);
	}

	@Test
	void theWarningIsSaidOnceNotEveryTick() {
		for (int tick = 0; tick < 5; tick++) {
			autoTotem.tick(client);
		}

		assertEquals(1, client.said.size());
	}

	@Test
	void aNewTotemArmsTheWarningAgain() {
		autoTotem.tick(client);

		client.inventory.put(TOTEM_SLOT, TOTEM);
		autoTotem.tick(client);
		autoTotem.tick(client);
		client.totemPops();

		autoTotem.tick(client);

		assertEquals(2, client.said.size());
	}

	@Test
	void aTotemStillInTheOffHandIsNotRunningOut() {
		client.offhandId = TOTEM;

		autoTotem.tick(client);

		assertEquals(List.of(), client.said);
	}

	@Test
	void theWarningCanBeTurnedOff() {
		settings.warnWhenOut = false;

		autoTotem.tick(client);

		assertEquals(List.of(), client.said);
	}

	@Test
	void aboveTheHealthThresholdNoTotemIsWantedAndNoneIsMissed() {
		settings.mode = TotemMode.HEALTH_THRESHOLD;
		client.health = 20.0f;

		autoTotem.tick(client);

		assertEquals(List.of(), client.said);
	}

	@Test
	void belowTheHealthThresholdTheMissingTotemIsWorthSaying() {
		settings.mode = TotemMode.HEALTH_THRESHOLD;
		client.health = 4.0f;

		autoTotem.tick(client);

		assertEquals(List.of(Lang.of(Keys.TOTEM_OUT)), client.said);
	}
}
