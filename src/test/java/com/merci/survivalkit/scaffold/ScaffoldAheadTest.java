package com.merci.survivalkit.scaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.vein.Pos;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Scaffold ahead and up: reaching ahead of the next step, keeping the level, and the tower.
 * The floor is y = 63 and ends at x = -1; walking is east.
 */
class ScaffoldAheadTest {
	private static final String COBBLE = "minecraft:cobblestone";
	private static final Vec EAST = new Vec(0.22, 0, 0);

	private final FakeScaffoldSettings settings = new FakeScaffoldSettings();
	private final FakeScaffoldContext client = new FakeScaffoldContext();
	private final Scaffold scaffold = new Scaffold(settings, new Hands());

	@BeforeEach
	void floorEndsBeforeTheGap() {
		client.floorTo(-1);
		client.give(0, COBBLE, 64);
		settings.perTick = 4;
	}

	private void tick() {
		scaffold.tick(client);
	}

	private List<Pos> placed() {
		return client.placed.stream().map(Placement::target).toList();
	}

	// --- reach ahead ---

	@Test
	void walkingAtTheEdgeTheBridgeReachesThatFarAhead() {
		settings.extend = 3;
		client.feet = new Vec(-0.4, 64, 0.5);
		client.motion = EAST;

		tick();

		assertEquals(List.of(new Pos(0, 63, 0), new Pos(1, 63, 0), new Pos(2, 63, 0)), placed());
	}

	@Test
	void withNoReachOnlyTheNextStepIsFilled() {
		client.feet = new Vec(-0.4, 64, 0.5);
		client.motion = EAST;

		tick();

		assertEquals(List.of(new Pos(0, 63, 0)), placed());
	}

	@Test
	void standingStillThereIsNoAhead() {
		settings.extend = 5;
		client.feet = new Vec(-0.4, 64, 0.5);

		tick();

		assertTrue(placed().isEmpty());
	}

	@Test
	void theReachFollowsTheWayOfWalking() {
		client.floorTo(5);
		client.solid.keySet().removeIf(pos -> pos.z() > 0);
		settings.extend = 2;
		client.feet = new Vec(0.5, 64, 0.6);
		client.motion = new Vec(0, 0, 0.22);

		tick();

		assertEquals(List.of(new Pos(0, 63, 1), new Pos(0, 63, 2)), placed());
	}

	// --- the level ---

	/** A jump over the gap without the tower: the block goes on the level jumped from, not under the raised feet. */
	@Test
	void aJumpDoesNotLiftTheBridge() {
		tick();
		client.placed.clear();
		client.feet = new Vec(1.5, 65.2, 0.5);
		client.onGround = false;
		client.jumpHeld = true;
		settings.tower = false;

		tick();

		assertEquals(List.of(new Pos(1, 63, 0)), placed());
	}

	/** Bridging, then up a stair at x = 1: with "keep Y" the bridge carries on below; without, it goes up too. */
	@Test
	void keepYHoldsTheLevelOverAStepUp() {
		settings.keepY = true;
		stepUpDuringARun();

		assertEquals(List.of(new Pos(2, 63, 0)), placed());
	}

	@Test
	void withoutKeepYTheBridgeFollowsTheStepUp() {
		stepUpDuringARun();

		assertEquals(List.of(new Pos(2, 64, 0)), placed());
	}

	@Test
	void onceTheRunIsOverKeepYTakesTheNewLevel() {
		settings.keepY = true;
		client.solid.put(new Pos(1, 63, 0), "minecraft:stone");
		client.solid.put(new Pos(1, 64, 0), "minecraft:stone");
		client.feet = new Vec(1.8, 65, 0.5);
		client.motion = EAST;

		tick();

		assertEquals(List.of(new Pos(2, 64, 0)), placed());
	}

	private void stepUpDuringARun() {
		tick();
		assertEquals(List.of(new Pos(0, 63, 0)), placed());
		client.placed.clear();

		client.solid.put(new Pos(1, 63, 0), "minecraft:stone");
		client.solid.put(new Pos(1, 64, 0), "minecraft:stone_stairs");
		client.feet = new Vec(1.8, 65, 0.5);
		client.motion = EAST;
		tick();
	}

	// --- the tower ---

	private void jumpingStraightUp(double feetY) {
		client.floorTo(5);
		client.feet = new Vec(0.5, 64, 0.5);
		tick();
		client.feet = new Vec(0.5, feetY, 0.5);
		client.onGround = false;
	}

	@Test
	void holdingJumpABlockGoesUnderOnceAWholeBlockUp() {
		jumpingStraightUp(65.1);
		client.jumpHeld = true;

		tick();

		Placement placement = client.placed.getFirst();
		assertEquals(new Pos(0, 64, 0), placement.target());
		assertEquals(Face.UP, placement.face());
	}

	@Test
	void notYetAWholeBlockUpNothingGoesUnder() {
		jumpingStraightUp(64.9);
		client.jumpHeld = true;

		tick();

		assertTrue(placed().isEmpty());
	}

	@Test
	void theTowerKeepsGoingFromTheNewLevel() {
		jumpingStraightUp(65.1);
		client.jumpHeld = true;
		tick();

		client.feet = new Vec(0.5, 65, 0.5);
		client.onGround = true;
		tick();
		client.feet = new Vec(0.5, 66.1, 0.5);
		client.onGround = false;
		tick();

		assertEquals(List.of(new Pos(0, 64, 0), new Pos(0, 65, 0)), placed());
	}

	@Test
	void onlyWithJumpHeldAJumpWithoutTheKeyBuildsNothing() {
		jumpingStraightUp(65.1);

		tick();

		assertTrue(placed().isEmpty());
	}

	@Test
	void withAnyJumpAJumpWithoutTheKeyBuildsToo() {
		jumpingStraightUp(65.1);
		settings.towerOnlyWithJump = false;

		tick();

		assertEquals(List.of(new Pos(0, 64, 0)), placed());
	}

	@Test
	void withTheTowerOffNothingGoesUnderAJump() {
		jumpingStraightUp(65.1);
		client.jumpHeld = true;
		settings.tower = false;

		tick();

		assertTrue(placed().isEmpty());
	}

	/** The tower lifts even a kept level: it is the one thing that does. */
	@Test
	void theTowerLiftsAKeptLevel() {
		settings.keepY = true;
		jumpingStraightUp(65.1);
		client.jumpHeld = true;
		tick();

		client.feet = new Vec(0.8, 65, 0.5);
		client.onGround = true;
		client.motion = EAST;
		client.solid.keySet().removeIf(pos -> pos.y() == 63 && pos.x() == 1);
		tick();

		assertEquals(List.of(new Pos(0, 64, 0), new Pos(1, 64, 0)), placed());
	}
}
