package com.merci.survivalkit.scaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.vein.Pos;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Scaffold's normal mode: a block under the next step when it is empty. The floor ends at
 * x = -1, so a player at x = 0.5 stands over the gap at (0, 63, 0); walking is east.
 */
class ScaffoldTest {
	private static final Pos GAP = new Pos(0, 63, 0);
	private static final Pos NEXT = new Pos(1, 63, 0);
	private static final String COBBLE = "minecraft:cobblestone";

	private final FakeScaffoldSettings settings = new FakeScaffoldSettings();
	private final FakeScaffoldContext client = new FakeScaffoldContext();
	private final Hands hands = new Hands();
	private final Scaffold scaffold = new Scaffold(settings, hands);

	@BeforeEach
	void floorEndsBeforeTheGap() {
		client.floorTo(-1);
		client.giveItem(0, "minecraft:stone_pickaxe");
		client.give(3, COBBLE, 64);
	}

	private void ticks(int count) {
		for (int i = 0; i < count; i++) {
			scaffold.tick(client);
		}
	}

	@Test
	void onSolidGroundNothingHappens() {
		client.feet = new Vec(-2.5, 64, 0.5);

		ticks(5);

		assertTrue(client.actions.isEmpty(), client.actions.toString());
		assertFalse(hands.holds(scaffold));
	}

	@Test
	void overAGapABlockGoesDownAgainstTheEdge() {
		scaffold.tick(client);

		assertEquals(1, client.placed.size());
		Placement placement = client.placed.getFirst();
		assertEquals(GAP, placement.target());
		assertEquals(new Pos(-1, 63, 0), placement.support());
		assertEquals(Face.EAST, placement.face());
		assertEquals(COBBLE, client.solid.get(GAP));
	}

	/** Still on the floor, walking towards its end: the step ahead is filled before the feet get there. */
	@Test
	void walkingTowardsTheEdgeTheNextStepIsFilledFirst() {
		client.feet = new Vec(-0.4, 64, 0.5);
		client.motion = new Vec(0.22, 0, 0);

		scaffold.tick(client);

		assertEquals(List.of(GAP), targets());
	}

	@Test
	void standingStillAtTheEdgeNothingAheadIsFilled() {
		client.feet = new Vec(-0.4, 64, 0.5);

		ticks(3);

		assertTrue(client.placed.isEmpty());
	}

	@Test
	void theBlocksComeIntoHandAndGoBackOnceNothingMoreIsNeeded() {
		scaffold.tick(client);
		assertEquals(3, client.selected);
		assertTrue(hands.holds(scaffold));

		ticks(Scaffold.IDLE_TICKS - 1);
		assertEquals(3, client.selected);

		scaffold.tick(client);
		assertEquals(0, client.selected);
		assertFalse(hands.holds(scaffold));
	}

	@Test
	void withoutRestoreTheBlocksStayInHand() {
		settings.restorePrevious = false;

		ticks(1 + Scaffold.IDLE_TICKS);

		assertEquals(3, client.selected);
		assertFalse(hands.holds(scaffold));
	}

	@Test
	void fromTheBackpackTheStackIsSwappedInAndBack() {
		client.slots[3] = null;
		client.give(20, COBBLE, 64);

		scaffold.tick(client);
		assertEquals(List.of("swap 20 0", "place " + GAP), client.actions);
		assertEquals(COBBLE, client.held());

		ticks(Scaffold.IDLE_TICKS);
		assertEquals("swap 20 0", client.actions.getLast());
		assertEquals("minecraft:stone_pickaxe", client.held());
	}

	@Test
	void withHotbarScopeTheBackpackIsNotSearched() {
		client.slots[3] = null;
		client.give(20, COBBLE, 64);
		client.scanned = 9;

		ticks(3);

		assertTrue(client.placed.isEmpty());
	}

	@Test
	void withNoBlocksNothingIsTakenOrPlaced() {
		client.slots[3] = null;

		ticks(3);

		assertTrue(client.actions.isEmpty());
		assertFalse(hands.holds(scaffold));
	}

	// --- gap kinds ---

	@Test
	void waterIsLeftByDefaultAndFilledWhenAsked() {
		client.water.add(GAP);

		scaffold.tick(client);
		assertTrue(client.placed.isEmpty());

		settings.fillWater = true;
		scaffold.tick(client);
		assertEquals(List.of(GAP), targets());
	}

	@Test
	void lavaIsFilledByDefaultAndLeftWhenAsked() {
		client.lava.add(GAP);
		settings.fillLava = false;

		scaffold.tick(client);
		assertTrue(client.placed.isEmpty());

		settings.fillLava = true;
		scaffold.tick(client);
		assertEquals(List.of(GAP), targets());
	}

	@Test
	void airIsLeftWhenAsked() {
		settings.fillAir = false;

		ticks(3);

		assertTrue(client.placed.isEmpty());
	}

	// --- pace ---

	@Test
	void oneBlockATickByDefault() {
		client.motion = new Vec(0.6, 0, 0);

		scaffold.tick(client);
		assertEquals(List.of(GAP), targets());

		scaffold.tick(client);
		assertEquals(List.of(GAP, NEXT), targets());
	}

	@Test
	void perTickPutsMoreDownInOneTick() {
		client.motion = new Vec(0.6, 0, 0);
		settings.perTick = 2;

		scaffold.tick(client);

		assertEquals(List.of(GAP, NEXT), targets());
	}

	@Test
	void theDelaySkipsThatManyTicksAfterABlock() {
		client.motion = new Vec(0.6, 0, 0);
		settings.delay = 2;

		ticks(3);
		assertEquals(List.of(GAP), targets());

		scaffold.tick(client);
		assertEquals(List.of(GAP, NEXT), targets());
	}

	// --- what is clicked ---

	@Test
	void aBlockThatDoesSomethingWhenUsedIsNotClicked() {
		client.interactive.add(new Pos(-1, 63, 0));

		ticks(3);

		assertTrue(client.placed.isEmpty());
	}

	@Test
	void nothingWhileAScreenIsOpen() {
		client.screenOpen = true;

		ticks(3);

		assertTrue(client.actions.isEmpty());
	}

	@Test
	void aRefusedPlacementCountsForNothing() {
		client.accepts = false;

		ticks(Scaffold.IDLE_TICKS);

		assertTrue(client.placed.isEmpty());
		assertEquals(0, client.selected);
	}

	// --- the hands ---

	@Test
	void scaffoldTakesTheHandFromAMeal() {
		List<String> interrupted = new ArrayList<>();
		Hands.Owner eating = () -> interrupted.add("eat");
		hands.take(eating, Hands.Priority.AUTO_EAT, Hands.Claim.MAIN_HAND);

		scaffold.tick(client);

		assertEquals(List.of("eat"), interrupted);
		assertEquals(List.of(GAP), targets());
	}

	@Test
	void scaffoldTakesTheHandFromOtomatikAlet() {
		Hands.Owner tool = () -> { };
		hands.take(tool, Hands.Priority.AUTO_TOOL, Hands.Claim.MAIN_HAND);

		scaffold.tick(client);

		assertFalse(hands.holds(tool));
		assertEquals(List.of(GAP), targets());
	}

	@Test
	void aTotemOutranksScaffold() {
		hands.take(() -> { }, Hands.Priority.AUTO_TOTEM, Hands.Claim.MAIN_HAND);

		ticks(3);

		assertTrue(client.placed.isEmpty());
		assertEquals(0, client.selected);
	}

	@Test
	void interruptedEverythingGoesBack() {
		client.slots[3] = null;
		client.give(20, COBBLE, 64);
		scaffold.tick(client);

		hands.take(() -> { }, Hands.Priority.AUTO_TOTEM, Hands.Claim.MAIN_HAND);

		assertEquals("minecraft:stone_pickaxe", client.held());
		assertEquals(COBBLE, client.slots[20].id());
	}

	@Test
	void switchedOffTheHandGoesBack() {
		scaffold.tick(client);

		scaffold.stop(client);

		assertEquals(0, client.selected);
		assertFalse(hands.holds(scaffold));
	}

	/** Leaving a world switches every module off after the player is gone. */
	@Test
	void switchedOffWithNoWorldNothingIsTouched() {
		scaffold.tick(client);
		client.actions.clear();
		client.ready = false;

		scaffold.stop(client);

		assertTrue(client.actions.isEmpty());
		assertFalse(hands.holds(scaffold));
	}

	private List<Pos> targets() {
		return client.placed.stream().map(Placement::target).toList();
	}
}
