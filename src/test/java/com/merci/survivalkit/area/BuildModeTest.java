package com.merci.survivalkit.area;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.area.Corners.Corner;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.merci.survivalkit.text.TestLang;
import com.merci.survivalkit.vein.Pos;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildModeTest {
	@TempDir
	Path dir;

	private BuildMode mode;
	private final FakeBuildContext client = new FakeBuildContext();

	@BeforeEach
	void setUp() {
		TestLang.install("tr_tr");
		mode = new BuildMode(new AreaStore(dir.resolve("areas.json"), line -> {
		}));
	}

	@Test
	void theLeftClickMarksTheFirstCornerAndTakesTheClick() {
		assertTrue(mode.attack(client, true));

		assertEquals(new Pos(10, 64, 10), mode.corners(client.place).first());
		assertEquals(List.of(Lang.of(Keys.BUILD_CORNER, 1, "10, 64, 10")), client.said);
	}

	@Test
	void holdingTheAttackKeyIsTakenTooButMarksNothingNew() {
		mode.attack(client, true);
		client.target = new Pos(11, 64, 10);

		assertTrue(mode.attack(client, false), "basılı tutmak da kırmaya başlamamalı");
		assertEquals(new Pos(10, 64, 10), mode.corners(client.place).first());
	}

	@Test
	void theRightClickMarksTheSecondAndTheAreaIsSaid() {
		mode.attack(client, true);
		client.target = new Pos(12, 66, 4);

		assertTrue(mode.click(client, Corner.SECOND));

		assertEquals(Optional.of(Area.between(new Pos(10, 64, 10), new Pos(12, 66, 4))), mode.area(client.place()));
		assertEquals(Lang.of(Keys.BUILD_AREA, "10, 64, 10", "12, 66, 4", 3, 3, 7, 63L), client.said.getLast());
		assertTrue(client.said.getLast().contains("63"), client.said.getLast());
	}

	@Test
	void withoutTheWandTheClicksAreTheGamesOwn() {
		client.wand = false;

		assertFalse(mode.attack(client, true));
		assertFalse(mode.click(client, Corner.SECOND));
		assertEquals(Corners.NONE, mode.corners(client.place));
		assertEquals(List.of(), client.said);
	}

	@Test
	void withTheCrosshairOnNothingTheClickIsTheGamesOwn() {
		client.target = null;

		assertFalse(mode.attack(client, true));
		assertFalse(mode.click(client, Corner.SECOND));
	}

	@Test
	void aHeldRightClickOnTheSameBlockSaysItOnce() {
		mode.click(client, Corner.SECOND);
		mode.click(client, Corner.SECOND);
		mode.click(client, Corner.SECOND);

		assertEquals(1, client.said.size(), client.said.toString());
	}

	@Test
	void theCommandPutsTheCornerWhereThePlayerStands() {
		mode.markHere(client, Corner.SECOND);

		assertEquals(new Pos(0, 64, 0), mode.corners(client.place).second());
	}

	@Test
	void anotherDimensionHasNoArea() {
		mode.attack(client, true);
		mode.click(client, Corner.SECOND);
		client.place = new Place("sp:Test", "minecraft:the_nether");

		assertEquals(Optional.empty(), mode.area(client.place()));
		mode.tick(client);
		assertEquals(Corners.NONE, client.framed);
	}

	@Test
	void theTickDrawsWhatIsMarkedHere() {
		mode.attack(client, true);
		mode.tick(client);

		assertEquals(new Corners(new Pos(10, 64, 10), null), client.framed);
	}

	@Test
	void clearingForgetsTheAreaAndSaysSo() {
		mode.attack(client, true);
		mode.click(client, Corner.SECOND);
		client.said.clear();

		mode.clear(client);
		mode.clear(client);

		assertEquals(Optional.empty(), mode.area(client.place()));
		assertEquals(List.of(Lang.of(Keys.BUILD_CLEARED), Lang.of(Keys.BUILD_NO_AREA)), client.said);
	}

	@Test
	void showingWhileOffSaysTheAreaHoldsNothingBack() {
		mode.attack(client, true);
		client.said.clear();

		mode.show(client, false);

		assertEquals(List.of(Lang.of(Keys.BUILD_CORNER, 1, "10, 64, 10"), Lang.of(Keys.BUILD_OFF)), client.said);
	}

	@Test
	void outsideAWorldNothingIsMarked() {
		client.place = null;

		assertFalse(mode.attack(client, true));
		mode.markHere(client, Corner.FIRST);
		assertEquals(List.of(Lang.of(Keys.BUILD_NOT_IN_WORLD)), client.said);
	}
}
