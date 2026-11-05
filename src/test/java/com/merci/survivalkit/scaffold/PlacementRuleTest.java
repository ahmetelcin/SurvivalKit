package com.merci.survivalkit.scaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.vein.Pos;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * Which face to click to fill a block. The target is always (0, 63, 0) — the block under a
 * player standing on (0, 64, 0) — and the eyes 1.62 above the feet, as in the game.
 */
class PlacementRuleTest {
	private static final Pos TARGET = new Pos(0, 63, 0);
	private static final Vec EYES = new Vec(0.5, 64 + 1.62, 0.5);
	private static final double REACH = 4.5;
	/** Nothing put down by İskele itself. */
	private static final Predicate<Pos> NONE_LAID = pos -> false;

	private final Map<Pos, String> blocks = new HashMap<>();

	private Optional<Placement> find() {
		return find(SupportMode.ALL, List.of(), NONE_LAID);
	}

	private Optional<Placement> find(SupportMode mode, List<String> listed, Predicate<Pos> laid) {
		return PlacementRule.find(TARGET, pos -> Optional.ofNullable(blocks.get(pos)), EYES, REACH, mode, listed, laid);
	}

	@Test
	void withNothingAroundThereIsNothingToClick() {
		assertTrue(find().isEmpty());
	}

	@Test
	void theBlockUnderneathIsClickedOnItsTopFaceAtItsMiddle() {
		blocks.put(new Pos(0, 62, 0), "minecraft:stone");

		Placement placement = find().orElseThrow();

		assertEquals(new Pos(0, 62, 0), placement.support());
		assertEquals(Face.UP, placement.face());
		assertEquals(new Vec(0.5, 63.0, 0.5), placement.hit());
		assertEquals(TARGET, placement.target());
	}

	@Test
	void theBlockUnderneathGoesBeforeOneBeside() {
		blocks.put(new Pos(-1, 63, 0), "minecraft:stone");
		blocks.put(new Pos(0, 62, 0), "minecraft:dirt");

		assertEquals(Face.UP, find().orElseThrow().face());
	}

	/** Walking east off a ledge: the block behind is the one to click, on the face that looks at the gap. */
	@Test
	void theBlockBesideIsClickedOnTheFaceThatLooksAtTheGap() {
		blocks.put(new Pos(-1, 63, 0), "minecraft:stone");

		Placement placement = find().orElseThrow();

		assertEquals(new Pos(-1, 63, 0), placement.support());
		assertEquals(Face.EAST, placement.face());
		assertEquals(new Vec(0.0, 63.5, 0.5), placement.hit());
	}

	@Test
	void ofTwoBesideTheNearerFaceWins() {
		Vec eyesOverTheWestHalf = new Vec(0.1, 64 + 1.62, 0.5);
		blocks.put(new Pos(1, 63, 0), "minecraft:stone");
		blocks.put(new Pos(-1, 63, 0), "minecraft:stone");

		Placement placement = PlacementRule.find(TARGET, pos -> Optional.ofNullable(blocks.get(pos)),
			eyesOverTheWestHalf, REACH, SupportMode.ALL, List.of(), NONE_LAID).orElseThrow();

		assertEquals(new Pos(-1, 63, 0), placement.support());
		assertEquals(Face.EAST, placement.face());
	}

	@Test
	void theBlockAboveIsTheLastResort() {
		blocks.put(new Pos(0, 64, 0), "minecraft:stone");

		Placement placement = find().orElseThrow();

		assertEquals(Face.DOWN, placement.face());
		assertEquals(new Vec(0.5, 64.0, 0.5), placement.hit());
	}

	@Test
	void aFaceOutOfReachIsNotClicked() {
		Vec farAbove = new Vec(0.5, 63 + 4.6, 0.5);
		blocks.put(new Pos(0, 62, 0), "minecraft:stone");

		assertTrue(PlacementRule.find(TARGET, pos -> Optional.ofNullable(blocks.get(pos)), farAbove, REACH, SupportMode.ALL, List.of(), NONE_LAID).isEmpty());
	}

	/** Beyond reach underneath, still within reach beside: the side face is used. */
	@Test
	void outOfReachUnderneathFallsBackToBeside() {
		Vec eyes = new Vec(0.5, 63 + 4.4, 0.5);
		blocks.put(new Pos(0, 62, 0), "minecraft:stone");
		blocks.put(new Pos(-1, 63, 0), "minecraft:stone");

		Placement placement = PlacementRule.find(TARGET, pos -> Optional.ofNullable(blocks.get(pos)), eyes, 4.0, SupportMode.ALL, List.of(), NONE_LAID).orElseThrow();

		assertEquals(Face.EAST, placement.face());
	}

	@Test
	void withTheListOnlyListedBlocksAreSupports() {
		blocks.put(new Pos(0, 62, 0), "minecraft:dirt");
		blocks.put(new Pos(-1, 63, 0), "minecraft:cobblestone");

		Placement placement = find(SupportMode.LISTED, List.of("minecraft:cobblestone"), NONE_LAID).orElseThrow();

		assertEquals(new Pos(-1, 63, 0), placement.support());
	}

	@Test
	void withTheListAndNothingListedAroundNothingIsClicked() {
		blocks.put(new Pos(0, 62, 0), "minecraft:dirt");

		assertTrue(find(SupportMode.LISTED, List.of("minecraft:cobblestone"), NONE_LAID).isEmpty());
		assertTrue(find(SupportMode.ALL, List.of("minecraft:cobblestone"), NONE_LAID).isPresent());
	}

	/** "Only the listed" with nothing listed is no limit: anything there will do. */
	@Test
	void withTheListEmptyAnyBlockIsASupport() {
		blocks.put(new Pos(0, 62, 0), "minecraft:dirt");

		Placement placement = find(SupportMode.LISTED, List.of(), NONE_LAID).orElseThrow();

		assertEquals(new Pos(0, 62, 0), placement.support());
	}

	/** A block İskele put down itself is built on whatever the list says. */
	@Test
	void aBlockLaidByScaffoldIsASupportThoughNotListed() {
		Pos laid = new Pos(-1, 63, 0);
		blocks.put(laid, "minecraft:cobblestone");

		Placement placement = find(SupportMode.LISTED, List.of("minecraft:stone"), laid::equals).orElseThrow();

		assertEquals(laid, placement.support());
	}

	/** Only the laid one: the same block somewhere it was not laid is still held to the list. */
	@Test
	void theSameBlockNotLaidByScaffoldIsStillHeldToTheList() {
		blocks.put(new Pos(-1, 63, 0), "minecraft:cobblestone");

		assertTrue(find(SupportMode.LISTED, List.of("minecraft:stone"), new Pos(5, 63, 0)::equals).isEmpty());
	}

	@Test
	void oneWorldAlwaysGivesOneAnswer() {
		for (Face side : List.of(Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST)) {
			blocks.put(side.from(TARGET), "minecraft:stone");
		}

		// All four are as near as each other from the middle; the fixed order decides.
		assertEquals(new Pos(0, 63, -1), find().orElseThrow().support());
	}

	@Test
	void facesAreTheirOwnOppositesOpposite() {
		for (Face face : Face.values()) {
			assertEquals(face, face.opposite().opposite());
			assertEquals(TARGET, face.opposite().from(face.from(TARGET)));
		}
	}
}
