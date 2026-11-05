package com.merci.survivalkit.scaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.merci.survivalkit.text.TestLang;
import com.merci.survivalkit.vein.Pos;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * İskele's extras: the bridge's width, the corners of a slantwise walk, the ramp, the descending
 * bridge, the same block, and the torches. The floor is y = 63 and ends at x = -1; walking is east.
 */
class ScaffoldExtrasTest {
	private static final String COBBLE = "minecraft:cobblestone";
	private static final String STONE = "minecraft:stone";
	private static final Vec EAST = new Vec(0.22, 0, 0);
	private static final Vec KEYS_EAST = new Vec(1, 0, 0);

	private final FakeScaffoldSettings settings = new FakeScaffoldSettings();
	private final FakeScaffoldContext client = new FakeScaffoldContext();
	private final Scaffold scaffold = new Scaffold(settings, new Hands());

	@BeforeEach
	void floorEndsBeforeTheGap() {
		TestLang.install("en_us");
		client.floorTo(-1);
		client.give(3, COBBLE, 64);
	}

	private void tick() {
		scaffold.tick(client);
	}

	private List<Pos> placed() {
		return client.placed.stream().map(Placement::target).toList();
	}

	/** On the floor's last block, walking east with the keys. */
	private void atTheEdge(double z) {
		client.feet = new Vec(-0.5, 64, z);
		client.motion = EAST;
		client.heading = KEYS_EAST;
	}

	/** Walks east on y = 64 a tick at a time, as fast as walking goes, from the floor's last block. */
	private void walk(int ticks) {
		atTheEdge(0.5);

		for (int i = 0; i < ticks; i++) {
			tick();
			client.feet = new Vec(client.feet.x() + EAST.x(), 64, 0.5);
		}
	}

	// --- width ---

	@Test
	void widthOneIsTheWayAlone() {
		settings.perTick = 4;
		client.feet = new Vec(0.5, 64, 0.5);
		client.motion = EAST;

		tick();

		assertEquals(List.of(new Pos(0, 63, 0)), placed());
	}

	@Test
	void widthThreeReachesBothSides() {
		settings.perTick = 4;
		settings.width = 3;
		client.feet = new Vec(0.5, 64, 0.5);
		client.motion = EAST;

		tick();

		assertEquals(List.of(new Pos(0, 63, 0), new Pos(0, 63, -1), new Pos(0, 63, 1)), placed());
	}

	@Test
	void widthTwoReachesTheSideTheFeetAreNearer() {
		settings.perTick = 4;
		settings.width = 2;
		client.feet = new Vec(0.5, 64, 0.3);
		client.motion = EAST;

		tick();

		assertEquals(List.of(new Pos(0, 63, 0), new Pos(0, 63, -1)), placed());
	}

	@Test
	void widthTwoNearerTheOtherSideReachesThatOne() {
		settings.perTick = 4;
		settings.width = 2;
		client.feet = new Vec(0.5, 64, 0.7);
		client.motion = EAST;

		tick();

		assertEquals(List.of(new Pos(0, 63, 0), new Pos(0, 63, 1)), placed());
	}

	// --- diagonal ---

	/** Going south-east from (0, 0) to (1, 1): the corner between them goes in, the one nearer the line walked. */
	@Test
	void slantwiseTheCornerIsFilled() {
		client.feet = new Vec(0.9, 64, 0.8);
		client.motion = new Vec(0.2, 0, 0.2);
		tick();

		assertEquals(List.of(new Pos(0, 63, 0), new Pos(1, 63, 0), new Pos(1, 63, 1)), scaffold.targets(client));
	}

	@Test
	void slantwiseNearerTheOtherCornerThatOneIsFilled() {
		client.feet = new Vec(0.8, 64, 0.9);
		client.motion = new Vec(0.2, 0, 0.2);
		tick();

		assertEquals(List.of(new Pos(0, 63, 0), new Pos(0, 63, 1), new Pos(1, 63, 1)), scaffold.targets(client));
	}

	@Test
	void withoutDiagonalTheBlocksTouchOnlyAtTheCorner() {
		settings.diagonal = false;
		client.feet = new Vec(0.9, 64, 0.8);
		client.motion = new Vec(0.2, 0, 0.2);
		tick();

		assertEquals(List.of(new Pos(0, 63, 0), new Pos(1, 63, 1)), scaffold.targets(client));
	}

	// --- ramp ---

	/** Up: the next block on is a block higher, and the block under it goes in first to hold it. */
	@Test
	void rampUpPutsTheNextStepABlockHigher() {
		settings.perTick = 4;
		settings.ramp = Ramp.UP;
		atTheEdge(0.5);

		tick();

		assertEquals(List.of(new Pos(0, 63, 0), new Pos(0, 64, 0)), placed());
	}

	/** Up on the first step, the next one is higher again. */
	@Test
	void rampUpKeepsClimbing() {
		settings.perTick = 4;
		settings.ramp = Ramp.UP;
		atTheEdge(0.5);
		tick();
		client.placed.clear();

		client.feet = new Vec(0.5, 65, 0.5);
		tick();

		assertEquals(List.of(new Pos(1, 64, 0), new Pos(1, 65, 0)), placed());
	}

	/** Down: the next block on is a block lower, held by a block under the step before. */
	@Test
	void rampDownPutsTheNextStepABlockLower() {
		settings.perTick = 4;
		settings.ramp = Ramp.DOWN;
		atTheEdge(0.5);

		tick();

		// And the step after, a block lower again, on the block put under this one.
		assertEquals(List.of(new Pos(-1, 62, 0), new Pos(0, 62, 0), new Pos(0, 61, 0), new Pos(1, 61, 0)), placed());
	}

	// --- the support list and İskele's own blocks ---

	/** Only the floor's stone listed: the first block goes against it, every one after against the last. */
	@Test
	void withTheFloorListedTheBridgeGoesOnAlongItself() {
		settings.supportMode = SupportMode.LISTED;
		settings.supports = List.of(STONE);

		walk(30);

		assertTrue(bridge() >= 5, "bridge " + bridge() + ", placed " + placed());
	}

	/** A block of the bridge broken and something unlisted put there by hand: no longer built against. */
	@Test
	void aLaidBlockReplacedByHandIsHeldToTheListAgain() {
		settings.supportMode = SupportMode.LISTED;
		settings.supports = List.of(STONE);
		atTheEdge(0.5);
		tick();
		client.solid.put(new Pos(0, 63, 0), "minecraft:granite");
		client.placed.clear();

		// Near the block's east edge, so the next one on is the gap beyond it.
		client.feet = new Vec(0.8, 64, 0.5);
		tick();

		assertEquals(List.of(), placed());
	}

	/** Walked away from, the bridge is forgotten: a block of it far behind is held to the list. */
	@Test
	void aBridgeFarBehindIsForgotten() {
		settings.supportMode = SupportMode.LISTED;
		settings.supports = List.of(STONE);
		atTheEdge(0.5);
		tick();
		client.placed.clear();

		client.feet = new Vec(-40.5, 64, 0.5);
		tick();
		client.feet = new Vec(0.8, 64, 0.5);
		tick();

		assertEquals(List.of(), placed());
	}

	/**
	 * With no blocks to hand the step up has nothing beside it to go against, only the block to
	 * go under it does: the step is still the one boxed.
	 */
	@Test
	void rampUpTheStepIsOutlined() {
		client.slots[3] = null;
		settings.ramp = Ramp.UP;
		atTheEdge(0.5);

		tick();

		assertEquals(List.of(new Pos(0, 64, 0)), client.outlined);
	}

	@Test
	void withoutARampTheNextBlockIsOnTheLevel() {
		settings.perTick = 4;
		atTheEdge(0.5);

		tick();

		assertEquals(List.of(new Pos(0, 63, 0)), placed());
	}

	/** A ramp's step is never put where the body is: up on the step's column, it waits for the jump. */
	@Test
	void rampUpNeverPutsAStepInsideThePlayer() {
		settings.perTick = 4;
		settings.ramp = Ramp.UP;
		atTheEdge(0.5);
		tick();
		client.placed.clear();
		client.solid.remove(new Pos(0, 64, 0));

		client.feet = new Vec(0.2, 64, 0.5);
		client.heading = new Vec(0, 0, 0);
		client.motion = new Vec(0, 0, 0);
		tick();

		assertFalse(placed().contains(new Pos(0, 64, 0)), placed().toString());
	}

	/** With the lock on, the lock slides with the ramp: the action bar's Y is the step's. */
	@Test
	void withALockTheLockSlidesAlong() {
		settings.perTick = 4;
		settings.ramp = Ramp.UP;
		settings.yLock = YLock.START;
		atTheEdge(0.5);
		tick();

		client.feet = new Vec(0.5, 65, 0.5);
		tick();

		assertEquals(Lang.of(Keys.SCAFFOLD_WITH_Y, Lang.of(Keys.SCAFFOLD_COUNT, 60), 64), client.actionBar.getLast());
	}

	/** The ramp keeps the tower down, like the lock: jumping with jump held puts nothing under the feet. */
	@Test
	void aRampKeepsTheTowerDown() {
		settings.ramp = Ramp.DOWN;
		client.feet = new Vec(-2.5, 64, 0.5);
		tick();

		client.feet = new Vec(-2.5, 65.2, 0.5);
		client.onGround = false;
		client.jumpHeld = true;
		tick();

		assertFalse(placed().contains(new Pos(-3, 64, 0)), placed().toString());
	}

	// --- descending bridge ---

	/** Locked, a press of sneak takes the lock a block down; the action bar says where. */
	@Test
	void sneakTakesTheLockDown() {
		settings.descend = true;
		settings.yLock = YLock.START;
		client.feet = new Vec(-2.5, 64, 0.5);
		tick();

		client.sneakKeyHeld = true;
		tick();

		assertEquals(Lang.of(Keys.SCAFFOLD_Y_LOCKED, 62), client.actionBar.getLast());
		atTheEdge(0.5);
		client.sneakKeyHeld = false;
		assertTrue(scaffold.targets(client).stream().allMatch(pos -> pos.y() == 62), scaffold.targets(client).toString());
	}

	@Test
	void holdingSneakIsOnePress() {
		settings.descend = true;
		settings.yLock = YLock.START;
		client.feet = new Vec(-2.5, 64, 0.5);
		tick();

		client.sneakKeyHeld = true;
		tick();
		tick();
		tick();

		assertEquals(Lang.of(Keys.SCAFFOLD_Y_LOCKED, 62), client.actionBar.getLast());
		atTheEdge(0.5);
		client.sneakKeyHeld = false;
		tick();
		assertTrue(scaffold.targets(client).stream().allMatch(pos -> pos.y() == 62), scaffold.targets(client).toString());
	}

	@Test
	void withDescendOffSneakChangesNothing() {
		settings.yLock = YLock.START;
		client.feet = new Vec(-2.5, 64, 0.5);
		tick();

		client.sneakKeyHeld = true;
		tick();
		client.sneakKeyHeld = false;

		atTheEdge(0.5);
		assertTrue(scaffold.targets(client).stream().allMatch(pos -> pos.y() == 63), scaffold.targets(client).toString());
	}

	/** With no lock there is nothing to take down: sneak says nothing and the level follows the feet. */
	@Test
	void withTheLockOffSneakChangesNothing() {
		settings.descend = true;
		client.feet = new Vec(-2.5, 64, 0.5);
		tick();

		client.sneakKeyHeld = true;
		tick();

		assertTrue(client.actionBar.isEmpty(), client.actionBar.toString());
	}

	/** The lock key puts the lock back where the feet are, sneaking undone. */
	@Test
	void theLockKeyUndoesTheDescent() {
		settings.descend = true;
		settings.yLock = YLock.START;
		client.feet = new Vec(-2.5, 64, 0.5);
		tick();
		client.sneakKeyHeld = true;
		tick();
		client.sneakKeyHeld = false;

		client.lockKeyHeld = true;
		tick();

		assertEquals(Lang.of(Keys.SCAFFOLD_Y_LOCKED, 63), client.actionBar.getLast());
	}

	/**
	 * Stopped at an edge, with the bridge beyond already a block lower: that is a step down, not
	 * an edge, and SafeWalk lets go. Not lowered, the same block a level down is an edge.
	 */
	@Test
	void safeWalkLetsTheFeetDownTheStep() {
		settings.descend = true;
		settings.yLock = YLock.START;
		settings.safeWalk = SafeWalk.AT_EDGE;
		client.solid.put(new Pos(0, 62, 0), COBBLE);
		client.feet = new Vec(-0.4, 64, 0.5);
		client.heading = KEYS_EAST;
		client.give(3, COBBLE, 0);
		tick();
		assertTrue(client.sneaking(), "not lowered, the gap is an edge");

		client.sneakKeyHeld = true;
		tick();

		assertFalse(client.sneaking(), "lowered, the block beyond is a step down");
	}

	// --- same block ---

	@Test
	void sameBlockBuildsWithWhatIsUnderfoot() {
		settings.sameBlock = true;
		client.give(5, STONE, 64);
		atTheEdge(0.5);

		tick();

		assertEquals(STONE, client.solid.get(new Pos(0, 63, 0)));
	}

	@Test
	void withoutSameBlockTheListDecides() {
		client.give(5, STONE, 64);
		atTheEdge(0.5);

		tick();

		assertEquals(COBBLE, client.solid.get(new Pos(0, 63, 0)));
	}

	@Test
	void sameBlockNotCarriedTheListDecides() {
		settings.sameBlock = true;
		atTheEdge(0.5);

		tick();

		assertEquals(COBBLE, client.solid.get(new Pos(0, 63, 0)));
	}

	/** Standing on sand with sand in the inventory: sand is never used, same block or not. */
	@Test
	void sameBlockStillLeavesTheNeverUseListAlone() {
		settings.sameBlock = true;
		client.solid.put(new Pos(-1, 63, 0), "minecraft:sand");
		client.give(5, "minecraft:sand", 64);
		atTheEdge(0.5);

		tick();

		assertEquals(COBBLE, client.solid.get(new Pos(0, 63, 0)));
	}

	// --- torches ---

	@Test
	void aTorchGoesOnTheSideEveryNBlocks() {
		settings.torches = true;
		settings.torchInterval = 3;
		client.giveTorches(6, 16);

		walk(40);

		assertTrue(bridge() >= 8, "bridge " + bridge());
		assertEquals(Set.of(2, 5, 8), torchColumns());
		assertTrue(client.torches.keySet().stream().allMatch(pos -> pos.y() == 63 && Math.abs(pos.z()) == 1), client.torches.toString());
		assertTrue(client.torches.keySet().stream().noneMatch(pos -> pos.y() == 64), "nothing on the walkway");
	}

	@Test
	void withTorchesOffNoneGoesDown() {
		client.giveTorches(6, 16);

		walk(40);

		assertTrue(client.torches.isEmpty(), client.torches.toString());
	}

	/** Both sides of the second block taken: its torch waits for the third, and the count starts again from there. */
	@Test
	void withNoRoomTheTorchWaitsForTheNextBlock() {
		settings.torches = true;
		settings.torchInterval = 2;
		client.giveTorches(6, 16);
		client.solid.put(new Pos(1, 63, 1), STONE);
		client.solid.put(new Pos(1, 63, -1), STONE);

		walk(40);

		assertTrue(bridge() >= 7, "bridge " + bridge());
		assertEquals(Set.of(2, 4, 6, 8), torchColumns());
	}

	@Test
	void outOfTorchesItIsSaidOnce() {
		settings.torches = true;
		settings.torchInterval = 1;

		walk(40);

		assertEquals(1, client.actionBar.stream().filter(line -> line.equals("! " + Lang.of(Keys.SCAFFOLD_NO_TORCHES))).count(),
			client.actionBar.toString());
	}

	/** Width 3: the torch goes past the side blocks, never in a side block's place. */
	@Test
	void onAWideBridgeTheTorchGoesOutside() {
		settings.torches = true;
		settings.torchInterval = 3;
		settings.width = 3;
		settings.perTick = 3;
		client.giveTorches(6, 16);

		walk(40);

		assertTrue(client.torches.keySet().stream().allMatch(pos -> Math.abs(pos.z()) == 2), client.torches.toString());
		assertFalse(client.torches.isEmpty());
	}

	/**
	 * Width 3 and the last block gone on the way itself: the sides are still to come, so the torch
	 * does not take a side block's place, though nothing else can go down there now.
	 */
	@Test
	void aTorchNeverTakesASideBlocksPlace() {
		settings.torches = true;
		settings.torchInterval = 1;
		settings.width = 3;
		client.give(3, COBBLE, 1);
		client.giveTorches(6, 16);
		client.feet = new Vec(0.5, 64, 0.5);
		client.motion = EAST;

		tick();
		tick();
		tick();

		assertEquals(List.of(new Pos(0, 63, 0)), placed());
		assertTrue(client.torches.isEmpty(), client.torches.toString());
	}

	/** How many blocks of bridge from x = 0 on, in a row. */
	private int bridge() {
		int x = 0;

		while (client.solid.containsKey(new Pos(x, 63, 0))) {
			x++;
		}

		return x;
	}

	private Set<Integer> torchColumns() {
		return new TreeSet<>(client.torches.keySet().stream().map(Pos::x).toList());
	}
}
