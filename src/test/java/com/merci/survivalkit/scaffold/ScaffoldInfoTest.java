package com.merci.survivalkit.scaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.hud.HudLine;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.merci.survivalkit.text.TestLang;
import com.merci.survivalkit.vein.Pos;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What İskele shows: the blocks left on the action bar, a warning when few are, and the box
 * round the next block. The floor is y = 63 and ends at x = -1; the player stands over the gap.
 */
class ScaffoldInfoTest {
	private static final Pos GAP = new Pos(0, 63, 0);
	private static final String COBBLE = "minecraft:cobblestone";

	private final FakeScaffoldSettings settings = new FakeScaffoldSettings();
	private final FakeScaffoldContext client = new FakeScaffoldContext();
	private final Scaffold scaffold = new Scaffold(settings, new Hands());

	@BeforeEach
	void floorEndsBeforeTheGap() {
		// Without a language the key comes back as it is, with no %s for the number to go in.
		TestLang.install("en_us");
		client.floorTo(-1);
	}

	private void ticks(int count) {
		for (int i = 0; i < count; i++) {
			scaffold.tick(client);
		}
	}

	private static String count(int left) {
		return Lang.of(Keys.SCAFFOLD_COUNT, left);
	}

	private static String low(int left) {
		return "! " + Lang.of(Keys.SCAFFOLD_LOW, left);
	}

	// --- the count ---

	@Test
	void whileBlocksGoDownWhatIsLeftIsShown() {
		client.give(0, COBBLE, 64);

		ticks(1);

		assertEquals(List.of(count(63)), client.actionBar);
	}

	/** Spelled out once, so the helpers above are known to carry the number. */
	@Test
	void theCountReadsAsWords() {
		client.give(0, COBBLE, 64);
		client.give(1, COBBLE, 10);

		ticks(1);

		assertEquals(List.of("Scaffold: 73 blocks"), client.actionBar);
	}

	@Test
	void everyChangeIsShownAtOnce() {
		client.give(0, COBBLE, 64);
		client.motion = new Vec(0.6, 0, 0);

		ticks(2);

		assertEquals(List.of(count(63), count(62)), client.actionBar);
	}

	@Test
	void unchangedItIsSaidAgainOnceASecondBeforeTheGameFadesIt() {
		client.give(0, COBBLE, 64);
		settings.delay = 30;

		ticks(1 + Scaffold.REFRESH_TICKS);

		assertEquals(List.of(count(63), count(63)), client.actionBar);
	}

	@Test
	void everyStackThatCouldBeUsedIsCounted() {
		client.give(0, COBBLE, 30);
		client.give(4, "minecraft:dirt", 20);
		client.give(5, "minecraft:sand", 64);

		ticks(1);

		assertEquals(List.of(count(49)), client.actionBar);
	}

	@Test
	void withNoFallbackOnlyTheListedBlocksAreCounted() {
		settings.fallback = BlockChoice.Fallback.NOTHING;
		settings.preferred = List.of(COBBLE);
		settings.lowWarning = 0;
		client.give(0, COBBLE, 5);
		client.give(4, "minecraft:stone", 64);

		ticks(1);

		assertEquals(List.of(count(4)), client.actionBar);
	}

	@Test
	void belowTheThresholdTheCountIsAWarning() {
		client.give(0, COBBLE, 10);

		ticks(1);

		assertEquals(List.of(low(9)), client.actionBar);
	}

	@Test
	void aThresholdOfNoneNeverWarns() {
		settings.lowWarning = 0;
		client.give(0, COBBLE, 3);

		ticks(1);

		assertEquals(List.of(count(2)), client.actionBar);
	}

	@Test
	void aBlockWantedWithNoneLeftSaysSo() {
		ticks(1);

		assertEquals(List.of("! " + Lang.of(Keys.SCAFFOLD_EMPTY)), client.actionBar);
	}

	@Test
	void onSolidGroundNothingIsShown() {
		client.give(0, COBBLE, 64);
		client.feet = new Vec(-2.5, 64, 0.5);

		ticks(5);

		assertTrue(client.actionBar.isEmpty());
	}

	@Test
	void withTheCountOffNothingIsShown() {
		settings.showCount = false;
		client.give(0, COBBLE, 64);

		ticks(1);
		client.slots[0] = null;
		ticks(1);

		assertTrue(client.actionBar.isEmpty());
	}

	// --- the outline ---

	@Test
	void theBlockStillToFillIsOutlined() {
		ticks(2);

		assertEquals(List.of(GAP, GAP), client.outlined);
	}

	/** One block a tick and the bridge reaching three ahead: the box is on the next one waiting. */
	@Test
	void theOutlineIsOnTheNextBlockWaiting() {
		client.give(0, COBBLE, 64);
		settings.extend = 3;
		client.feet = new Vec(-0.4, 64, 0.5);
		client.motion = new Vec(0.22, 0, 0);

		ticks(1);

		assertEquals(List.of(GAP), client.placed.stream().map(Placement::target).toList());
		assertEquals(List.of(new Pos(1, 63, 0)), client.outlined);
	}

	@Test
	void withNothingToFillNothingIsOutlined() {
		client.give(0, COBBLE, 64);

		ticks(1);

		assertTrue(client.outlined.isEmpty());
	}

	@Test
	void withTheOutlineOffNothingIsDrawn() {
		settings.showTarget = false;

		ticks(2);

		assertTrue(client.outlined.isEmpty());
	}

	// --- the support list ---

	/** A real profile: "only the listed" with the list empty is no limit. */
	@Test
	void withTheListEmptyAnythingIsBuiltOn() {
		client.give(0, COBBLE, 64);
		settings.supportMode = SupportMode.LISTED;
		settings.supports = List.of();

		ticks(2);

		assertEquals(List.of(GAP), client.placed.stream().map(Placement::target).toList());
		assertTrue(client.actionBar.stream().noneMatch(line -> line.contains(Lang.of(Keys.SCAFFOLD_NO_SUPPORT))), client.actionBar.toString());
	}

	/** The floor's stone is not on the list: no box round a block that cannot go down. */
	@Test
	void withNothingAllowedToBuildAgainstNothingIsOutlined() {
		client.give(0, COBBLE, 64);
		settings.supportMode = SupportMode.LISTED;
		settings.supports = List.of("minecraft:dirt");

		ticks(2);

		assertTrue(client.placed.isEmpty());
		assertTrue(client.outlined.isEmpty(), "çerçeve konamayacak bloğu göstermemeli: " + client.outlined);
	}

	@Test
	void withTheFloorListedTheGapIsOutlined() {
		settings.supportMode = SupportMode.LISTED;
		settings.supports = List.of("minecraft:stone");

		ticks(1);

		assertEquals(List.of(GAP), client.outlined);
	}

	@Test
	void whatTheListRefusesIsSaidOnce() {
		client.give(0, COBBLE, 64);
		settings.supportMode = SupportMode.LISTED;
		settings.supports = List.of("minecraft:cobblestone");

		ticks(3);

		HudLine refused = new HudLine(Lang.of(Keys.SCAFFOLD_NO_SUPPORT), true);
		assertEquals(refused, scaffold.hudLines().getFirst());
		assertEquals(1, client.actionBar.stream().filter(line -> line.equals("! " + Lang.of(Keys.SCAFFOLD_NO_SUPPORT))).count(),
			"eylem çubuğuna bir kez: " + client.actionBar);
	}

	/** Once a block has gone down, the list refusing again is said again. */
	@Test
	void afterABlockWentDownItIsSaidAgain() {
		client.give(0, COBBLE, 64);
		settings.supportMode = SupportMode.LISTED;
		settings.supports = List.of("minecraft:dirt");
		ticks(2);
		settings.supports = List.of("minecraft:stone");
		ticks(1);
		client.solid.remove(GAP);
		settings.supports = List.of("minecraft:dirt");

		ticks(2);

		assertEquals(2, client.actionBar.stream().filter(line -> line.equals("! " + Lang.of(Keys.SCAFFOLD_NO_SUPPORT))).count(),
			client.actionBar.toString());
	}

	@Test
	void withAnySupportAllowedNothingIsSaidAboutTheList() {
		client.give(0, COBBLE, 64);
		settings.supportMode = SupportMode.LISTED;
		settings.supports = List.of("minecraft:stone");

		ticks(3);

		assertTrue(client.actionBar.stream().noneMatch(line -> line.contains(Lang.of(Keys.SCAFFOLD_NO_SUPPORT))), client.actionBar.toString());
	}
}
