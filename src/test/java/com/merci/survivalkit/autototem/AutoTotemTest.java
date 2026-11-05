package com.merci.survivalkit.autototem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.hands.Hands;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Keeping a totem in the off hand: when one goes in, when it does not, and what happens to
 * whatever was in that hand first.
 */
class AutoTotemTest {
	private static final String TOTEM = AutoTotem.TOTEM;
	private static final String SHIELD = "minecraft:shield";
	private static final int TOTEM_SLOT = 5;
	private static final int SPARE_TOTEM_SLOT = 7;

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
		client.inventory.put(TOTEM_SLOT, TOTEM);
	}

	// --- always ---

	@Test
	void anEmptyOffHandGetsATotem() {
		autoTotem.tick(client);

		assertEquals(List.of(TOTEM_SLOT), client.offhandSwaps);
		assertEquals(TOTEM, client.offhandId);
	}

	@Test
	void aTotemAlreadyInTheOffHandIsLeftAlone() {
		client.offhandId = TOTEM;

		autoTotem.tick(client);

		assertEquals(List.of(), client.offhandSwaps);
	}

	@Test
	void withNoTotemAnywhereNothingIsMoved() {
		client.inventory.clear();
		client.offhandId = SHIELD;

		autoTotem.tick(client);

		assertEquals(List.of(), client.offhandSwaps);
		assertEquals(SHIELD, client.offhandId);
	}

	@Test
	void nothingHappensWhileAScreenIsOpen() {
		client.ready = false;

		autoTotem.tick(client);

		assertEquals(List.of(), client.offhandSwaps);
	}

	/** The one that matters: the totem is spent, and the next one goes straight in. */
	@Test
	void aSpentTotemIsReplaced() {
		autoTotem.tick(client);
		settle();
		client.inventory.put(SPARE_TOTEM_SLOT, TOTEM);

		client.totemPops();
		autoTotem.tick(client);

		assertEquals(TOTEM, client.offhandId);
		assertEquals(List.of(TOTEM_SLOT, SPARE_TOTEM_SLOT), client.offhandSwaps);
	}

	// --- the hands ---

	@Test
	void theHandsAreClaimedForTheSwapAndGivenBackAfterwards() {
		autoTotem.tick(client);

		assertTrue(hands.holds(autoTotem), "takas uçarken eller tutulmalıydı");

		autoTotem.tick(client);

		assertFalse(hands.holds(autoTotem), "takas inince eller bırakılmalıydı");
	}

	/** Nothing outranks it, so a lesser hold is simply taken off whoever had it. */
	@Test
	void aLesserModuleHoldingTheOffHandIsInterrupted() {
		Interruptible eating = new Interruptible();
		hands.take(eating, Hands.Priority.AUTO_EAT, Hands.Claim.OFF_HAND);

		autoTotem.tick(client);

		assertEquals(1, eating.interrupted);
		assertEquals(List.of(TOTEM_SLOT), client.offhandSwaps);
	}

	// --- the health threshold ---

	@Test
	void aboveTheThresholdTheHandIsLeftAlone() {
		settings.mode = TotemMode.HEALTH_THRESHOLD;
		settings.healthThreshold = 8;
		client.health = 12.0f;
		client.offhandId = SHIELD;

		autoTotem.tick(client);

		assertEquals(List.of(), client.offhandSwaps);
		assertEquals(SHIELD, client.offhandId);
	}

	@Test
	void belowTheThresholdTheTotemGoesIn() {
		settings.mode = TotemMode.HEALTH_THRESHOLD;
		settings.healthThreshold = 8;
		client.health = 6.0f;
		client.offhandId = SHIELD;

		autoTotem.tick(client);

		assertEquals(TOTEM, client.offhandId);
	}

	@Test
	void atTheThresholdItIsNotYetLowEnough() {
		settings.mode = TotemMode.HEALTH_THRESHOLD;
		settings.healthThreshold = 8;
		client.health = 8.0f;

		autoTotem.tick(client);

		assertEquals(List.of(), client.offhandSwaps);
	}

	// --- giving the hand back ---

	@Test
	void whenHealthRecoversTheOldItemComesBack() {
		hurtWithAShieldInHand();

		client.health = 20.0f;
		autoTotem.tick(client);

		assertEquals(SHIELD, client.offhandId);
		assertEquals(TOTEM, client.inventory.get(TOTEM_SLOT), "totem geldiği slota dönmeliydi");
	}

	/** Negative control: with the setting off, the totem stays where it is. */
	@Test
	void theRestoreCanBeTurnedOff() {
		settings.restoreOffhand = false;
		hurtWithAShieldInHand();

		client.health = 20.0f;
		autoTotem.tick(client);

		assertEquals(TOTEM, client.offhandId);
	}

	/** Negative control: in the always mode there is nothing to give back. */
	@Test
	void theAlwaysModeNeverGivesTheHandBack() {
		client.offhandId = SHIELD;
		autoTotem.tick(client);
		settle();

		client.health = 20.0f;
		autoTotem.tick(client);

		assertEquals(TOTEM, client.offhandId);
	}

	/** After a totem is spent and replaced, the shield is still the thing that comes back. */
	@Test
	void theBorrowedHandGoesBackEvenAfterATotemWasSpent() {
		hurtWithAShieldInHand();
		client.inventory.put(SPARE_TOTEM_SLOT, TOTEM);
		client.totemPops();
		autoTotem.tick(client);
		settle();

		client.health = 20.0f;
		autoTotem.tick(client);

		assertEquals(SHIELD, client.offhandId);
	}

	@Test
	void switchingTheModuleOffPutsTheOldItemBack() {
		hurtWithAShieldInHand();

		autoTotem.stop(client);

		assertEquals(SHIELD, client.offhandId);
	}

	// --- plumbing ---

	/**
	 * Hurt, with a shield in the off hand, so a totem has been borrowed into it.
	 *
	 * <p>The borrow is asserted rather than assumed: without it every test that follows
	 * would find the shield still in hand and call that success.
	 */
	private void hurtWithAShieldInHand() {
		settings.mode = TotemMode.HEALTH_THRESHOLD;
		settings.healthThreshold = 8;
		client.health = 6.0f;
		client.offhandId = SHIELD;
		autoTotem.tick(client);
		settle();

		assertEquals(TOTEM, client.offhandId, "hazırlık: totem diğer ele girmeliydi");
		assertEquals(SHIELD, client.inventory.get(TOTEM_SLOT), "hazırlık: kalkan totemin slotunda beklemeliydi");
	}

	/** One more tick, for the swap to land and the hands to be let go. */
	private void settle() {
		autoTotem.tick(client);
	}

	private static final class Interruptible implements Hands.Owner {
		private int interrupted;

		@Override
		public void handsTaken() {
			interrupted++;
		}
	}
}
