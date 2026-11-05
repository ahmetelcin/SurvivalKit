package com.merci.survivalkit.scaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.area.Area;
import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.vein.Pos;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Scaffold's safety: pausing, SafeWalk, and Yapı Modu's area. The floor is y = 63 and ends at
 * x = -1; the player stands over the gap at (0, 63, 0) unless moved.
 */
class ScaffoldSafetyTest {
	private static final Pos GAP = new Pos(0, 63, 0);
	private static final Vec EAST = new Vec(1, 0, 0);

	private final FakeScaffoldSettings settings = new FakeScaffoldSettings();
	private final FakeScaffoldContext client = new FakeScaffoldContext();
	private final Scaffold scaffold = new Scaffold(settings, new Hands());

	@BeforeEach
	void floorEndsBeforeTheGap() {
		client.floorTo(-1);
		client.give(0, "minecraft:cobblestone", 64);
	}

	private void tick() {
		scaffold.tick(client);
	}

	private List<Pos> placed() {
		return client.placed.stream().map(Placement::target).toList();
	}

	/** On the floor's last block, the keys taking the player east over the edge. */
	private void atTheEdgeHeadingEast() {
		client.feet = new Vec(-0.4, 64, 0.5);
		client.heading = EAST;
	}

	// --- pausing ---

	@Test
	void withAScreenOpenNothingGoesDownUnlessAskedTo() {
		client.screenOpen = true;
		tick();
		assertTrue(placed().isEmpty());

		settings.pauseWithScreen = false;
		tick();
		assertEquals(List.of(GAP), placed());
	}

	@Test
	void fallingFurtherThanAJumpNothingGoesDownUnlessAskedTo() {
		tick();
		client.placed.clear();
		client.solid.remove(GAP);
		client.onGround = false;
		client.fallDistance = 2.0;

		tick();
		assertTrue(placed().isEmpty());

		settings.pauseWhileFalling = false;
		tick();
		assertEquals(List.of(GAP), placed());
	}

	/** Coming down from a jump is not a fall: a bridge jumped along still gets its blocks. */
	@Test
	void comingDownFromAJumpIsNoFall() {
		tick();
		client.placed.clear();
		client.solid.remove(GAP);
		client.onGround = false;
		client.fallDistance = 1.2;

		tick();

		assertEquals(List.of(GAP), placed());
	}

	@Test
	void inLavaNothingGoesDownUnlessAskedTo() {
		client.inLava = true;
		tick();
		assertTrue(placed().isEmpty());

		settings.pauseInLava = false;
		tick();
		assertEquals(List.of(GAP), placed());
	}

	@Test
	void pausedTheSneakItHeldIsLetGo() {
		settings.safeWalk = SafeWalk.ALWAYS;
		tick();
		assertTrue(client.sneaking());

		client.inLava = true;
		tick();

		assertEquals(List.of(true, false), client.sneaks);
	}

	// --- SafeWalk ---

	@Test
	void atAnEdgeWithNoBlocksSafeWalkHoldsSneak() {
		settings.safeWalk = SafeWalk.AT_EDGE;
		client.slots[0] = null;
		atTheEdgeHeadingEast();

		tick();

		assertTrue(client.sneaking());
	}

	@Test
	void atAnEdgeWithBlocksTheBlockGoesDownAndThereIsNoSneak() {
		settings.safeWalk = SafeWalk.AT_EDGE;
		atTheEdgeHeadingEast();

		tick();

		assertEquals(List.of(GAP), placed());
		assertTrue(client.sneaks.isEmpty());
	}

	@Test
	void awayFromAnEdgeSafeWalkLeavesSneakAlone() {
		settings.safeWalk = SafeWalk.AT_EDGE;
		client.slots[0] = null;
		client.feet = new Vec(-2.5, 64, 0.5);
		client.heading = EAST;

		tick();

		assertTrue(client.sneaks.isEmpty());
	}

	@Test
	void withNoKeysHeldThereIsNoEdgeAhead() {
		settings.safeWalk = SafeWalk.AT_EDGE;
		client.slots[0] = null;
		client.feet = new Vec(-0.4, 64, 0.5);

		tick();

		assertTrue(client.sneaks.isEmpty());
	}

	@Test
	void inTheAirSafeWalkCannotHelp() {
		settings.safeWalk = SafeWalk.AT_EDGE;
		client.slots[0] = null;
		atTheEdgeHeadingEast();
		client.onGround = false;

		tick();

		assertTrue(client.sneaks.isEmpty());
	}

	@Test
	void turningAwayFromTheEdgeLetsSneakGo() {
		settings.safeWalk = SafeWalk.AT_EDGE;
		client.slots[0] = null;
		atTheEdgeHeadingEast();
		tick();

		client.heading = new Vec(-1, 0, 0);
		tick();

		assertEquals(List.of(true, false), client.sneaks);
	}

	/** Held back on the edge with the middle of the feet over the gap: letting go of the keys is no reason to stand up. */
	@Test
	void hangingOverTheEdgeSneakStaysWithNoKeysHeld() {
		settings.safeWalk = SafeWalk.AT_EDGE;
		client.slots[0] = null;
		client.feet = new Vec(0.2, 64, 0.5);

		tick();

		assertTrue(client.sneaking());
	}

	/** The game's sneak holds the body back but not its speed: still sliding for the edge, sneak stays. */
	@Test
	void stillSlidingForTheEdgeSneakStays() {
		settings.safeWalk = SafeWalk.AT_EDGE;
		client.slots[0] = null;
		atTheEdgeHeadingEast();
		tick();

		client.heading = new Vec(0, 0, 0);
		client.motion = new Vec(0.05, 0, 0);
		tick();

		assertEquals(List.of(true), client.sneaks);
	}

	@Test
	void onceTheSlideHasDiedAwaySneakIsLetGo() {
		settings.safeWalk = SafeWalk.AT_EDGE;
		client.slots[0] = null;
		atTheEdgeHeadingEast();
		tick();

		client.heading = new Vec(0, 0, 0);
		client.motion = new Vec(0.001, 0, 0);
		tick();

		assertEquals(List.of(true, false), client.sneaks);
	}

	@Test
	void alwaysHoldsSneakEvenAwayFromAnEdge() {
		settings.safeWalk = SafeWalk.ALWAYS;
		client.feet = new Vec(-2.5, 64, 0.5);

		tick();
		tick();

		assertEquals(List.of(true), client.sneaks);
	}

	@Test
	void offNeverTouchesSneak() {
		client.slots[0] = null;
		atTheEdgeHeadingEast();

		tick();

		assertTrue(client.sneaks.isEmpty());
	}

	@Test
	void switchedOffTheSneakItHeldIsLetGo() {
		settings.safeWalk = SafeWalk.ALWAYS;
		tick();

		scaffold.stop(client);

		assertEquals(List.of(true, false), client.sneaks);
	}

	/** Stopped at the edge the feet have no motion, but the keys still say where they are going. */
	@Test
	void stoppedAtTheEdgeTheKeysStillSayWhereTheNextStepIs() {
		client.feet = new Vec(-0.4, 64, 0.5);
		client.motion = new Vec(0, 0, 0);
		client.heading = EAST;

		tick();

		assertEquals(List.of(GAP), placed());
	}

	// --- the area ---

	@Test
	void withAnAreaOnlyTheBlocksInsideGoDown() {
		settings.extend = 3;
		settings.perTick = 4;
		client.area = Optional.of(Area.between(new Pos(-5, 60, -5), new Pos(1, 70, 5)));
		client.feet = new Vec(-0.4, 64, 0.5);
		client.motion = new Vec(0.22, 0, 0);

		tick();

		assertEquals(List.of(GAP, new Pos(1, 63, 0)), placed());
	}

	@Test
	void outsideTheAreaNothingGoesDownAndSafeWalkStops() {
		settings.safeWalk = SafeWalk.AT_EDGE;
		client.area = Optional.of(Area.between(new Pos(-5, 60, -5), new Pos(-1, 70, 5)));
		atTheEdgeHeadingEast();

		tick();

		assertTrue(placed().isEmpty());
		assertTrue(client.sneaking());
	}

	@Test
	void withNoAreaEverywhereGoes() {
		tick();

		assertFalse(placed().isEmpty());
	}
}
