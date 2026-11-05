package com.merci.survivalkit.scaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.merci.survivalkit.text.TestLang;
import com.merci.survivalkit.vein.Pos;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The Y lock: every block in one y however the feet go, the key that moves it, and the action
 * bar that says where it is. The floor is y = 63 and ends at x = -1; walking is east.
 */
class ScaffoldYLockTest {
	private static final String COBBLE = "minecraft:cobblestone";
	private static final Vec EAST = new Vec(0.22, 0, 0);
	private static final Vec RUNNING_JUMP = new Vec(0.32, 0, 0);

	private final FakeScaffoldSettings settings = new FakeScaffoldSettings();
	private final FakeScaffoldContext client = new FakeScaffoldContext();
	private final Scaffold scaffold = new Scaffold(settings, new Hands());

	@BeforeEach
	void floorEndsBeforeTheGap() {
		TestLang.install("en_us");
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

	/** At the edge, a block down at the floor's level; then in the air mid-jump over the gap, the jump key held. */
	private void runningJumpOverTheGap() {
		client.feet = new Vec(-0.4, 64, 0.5);
		client.motion = EAST;
		tick();
		assertEquals(List.of(new Pos(0, 63, 0)), placed());
		client.placed.clear();

		client.feet = new Vec(0.5, 65.2, 0.5);
		client.motion = RUNNING_JUMP;
		client.onGround = false;
		client.jumpHeld = true;
		tick();
	}

	// --- the level ---

	/** Running and jumping with jump held: the lock keeps the tower down, the next block goes on the floor's level. */
	@Test
	void aRunningJumpWithTheLockStaysOnTheLevel() {
		settings.yLock = YLock.START;

		runningJumpOverTheGap();

		assertEquals(List.of(new Pos(1, 63, 0)), placed());
	}

	/** The same jump without it: the tower takes it, and the bridge goes up a block. */
	@Test
	void aRunningJumpWithoutTheLockLiftsTheBridge() {
		runningJumpOverTheGap();

		assertEquals(List.of(new Pos(0, 64, 0)), placed());
	}

	/** Landing a block higher — up a step — the lock still puts the next block on the level it started on. */
	@Test
	void theStartLockHoldsOverAStepUp() {
		settings.yLock = YLock.START;
		client.feet = new Vec(-0.4, 64, 0.5);
		tick();

		client.solid.put(new Pos(1, 63, 0), "minecraft:stone");
		client.solid.put(new Pos(1, 64, 0), "minecraft:stone_stairs");
		client.feet = new Vec(1.8, 65, 0.5);
		client.motion = EAST;
		tick();

		assertEquals(List.of(new Pos(2, 63, 0)), placed());
	}

	/** Switched off and on again somewhere else: where it starts now is the level. */
	@Test
	void switchedOnAgainTheStartIsTakenAfresh() {
		settings.yLock = YLock.START;
		client.feet = new Vec(-3.5, 64, 0.5);
		tick();
		scaffold.stop(client);

		client.solid.put(new Pos(-1, 65, 0), "minecraft:stone");
		client.feet = new Vec(-0.4, 66, 0.5);
		client.motion = EAST;
		tick();

		assertEquals(List.of(new Pos(0, 65, 0)), placed());
	}

	/** The lock set off and back to "where I started", Scaffold on all along: where the feet are then is the start. */
	@Test
	void setBackToStartTheStartIsTakenAfresh() {
		settings.yLock = YLock.START;
		client.feet = new Vec(-3.5, 64, 0.5);
		tick();
		settings.yLock = YLock.OFF;
		tick();

		client.solid.put(new Pos(-1, 65, 0), "minecraft:stone");
		client.feet = new Vec(-0.4, 66, 0.5);
		settings.yLock = YLock.START;
		tick();
		client.motion = EAST;
		tick();

		assertEquals(List.of(new Pos(0, 65, 0)), placed());
	}

	@Test
	void aSetYPutsTheBridgeThere() {
		settings.yLock = YLock.FIXED;
		settings.fixedY = 62;
		client.solid.put(new Pos(-1, 62, 0), "minecraft:stone");
		client.feet = new Vec(-0.4, 64, 0.5);
		client.motion = EAST;

		tick();

		assertEquals(List.of(new Pos(0, 62, 0)), placed());
	}

	/** A set Y at the feet would be a block inside the player: nothing. */
	@Test
	void aSetYAtTheFeetPutsNothing() {
		settings.yLock = YLock.FIXED;
		settings.fixedY = 64;
		client.feet = new Vec(-0.4, 64, 0.5);
		client.motion = EAST;

		tick();

		assertTrue(placed().isEmpty());
	}

	/** Further down than the hand reaches: nothing, rather than a block anywhere else. */
	@Test
	void aSetYOutOfReachPutsNothing() {
		settings.yLock = YLock.FIXED;
		settings.fixedY = 50;
		client.solid.put(new Pos(-1, 50, 0), "minecraft:stone");
		client.feet = new Vec(-0.4, 64, 0.5);
		client.motion = EAST;

		tick();

		assertTrue(placed().isEmpty());
	}

	// --- the key ---

	/** Started at 63, then up on a ledge at 65: the key brings the lock there. */
	private void upOnALedge() {
		client.feet = new Vec(-3.5, 64, 0.5);
		tick();
		client.solid.put(new Pos(-1, 65, 0), "minecraft:stone");
		client.feet = new Vec(-0.4, 66, 0.5);
		client.motion = EAST;
	}

	@Test
	void theKeyMovesAStartLockToTheFeet() {
		settings.yLock = YLock.START;
		upOnALedge();
		client.lockKeyHeld = true;

		tick();

		assertEquals(List.of(new Pos(0, 65, 0)), placed());
		assertEquals(Lang.of(Keys.SCAFFOLD_Y_LOCKED, 65), client.actionBar.getFirst());
	}

	/** The same ledge without the key: the lock is still where it started. */
	@Test
	void withoutTheKeyTheStartLockStays() {
		settings.yLock = YLock.START;
		upOnALedge();

		tick();

		assertEquals(List.of(new Pos(0, 63, 0)), placed());
	}

	/** Held down, it is one press: the lock does not follow the feet while the key stays down. */
	@Test
	void holdingTheKeyIsOnePress() {
		settings.yLock = YLock.START;
		upOnALedge();
		client.lockKeyHeld = true;
		tick();
		client.placed.clear();

		client.solid.put(new Pos(0, 67, 0), "minecraft:stone");
		client.feet = new Vec(0.8, 68, 0.5);
		tick();

		assertEquals(List.of(new Pos(1, 65, 0)), placed());
	}

	@Test
	void withASetYTheKeyWritesTheNumber() {
		settings.yLock = YLock.FIXED;
		client.feet = new Vec(-3.5, 71, 0.5);
		client.lockKeyHeld = true;

		tick();

		assertEquals(70, settings.fixedY);
		assertEquals(Lang.of(Keys.SCAFFOLD_Y_LOCKED, 70), client.actionBar.getFirst());
	}

	@Test
	void withTheLockOffTheKeySaysSoAndChangesNothing() {
		client.feet = new Vec(-3.5, 71, 0.5);
		client.lockKeyHeld = true;

		tick();

		assertEquals(64, settings.fixedY);
		assertEquals(List.of(Lang.of(Keys.SCAFFOLD_Y_LOCK_OFF)), client.actionBar);
	}

	// --- the action bar ---

	@Test
	void whileLockedTheCountCarriesTheY() {
		settings.yLock = YLock.START;
		client.feet = new Vec(-0.4, 64, 0.5);
		client.motion = EAST;

		tick();

		assertEquals(List.of("Scaffold: 63 blocks · Y 63"), client.actionBar);
	}

	@Test
	void unlockedTheCountIsAlone() {
		client.feet = new Vec(-0.4, 64, 0.5);
		client.motion = EAST;

		tick();

		assertEquals(List.of(Lang.of(Keys.SCAFFOLD_COUNT, 63)), client.actionBar);
	}

	@Test
	void withTheCountOffTheLockedYIsStillShown() {
		settings.yLock = YLock.START;
		settings.showCount = false;
		client.feet = new Vec(-0.4, 64, 0.5);
		client.motion = EAST;

		tick();

		assertEquals(List.of(Lang.of(Keys.SCAFFOLD_Y, 63)), client.actionBar);
	}

	/** The key's line stays up a second: the count, which would be said as the block goes down, waits. */
	@Test
	void theKeysLineIsNotSaidOverAtOnce() {
		settings.yLock = YLock.START;
		upOnALedge();
		client.lockKeyHeld = true;

		for (int i = 0; i < Scaffold.REFRESH_TICKS; i++) {
			tick();
		}

		assertEquals(List.of(new Pos(0, 65, 0)), placed());
		assertEquals(List.of(Lang.of(Keys.SCAFFOLD_Y_LOCKED, 65)), client.actionBar);
	}
}
