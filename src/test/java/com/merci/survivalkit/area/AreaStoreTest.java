package com.merci.survivalkit.area;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.vein.Pos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** An area belongs to one server and one dimension, and outlives the game. */
class AreaStoreTest {
	private static final Place OVERWORLD = new Place("mp:play.example.net", "minecraft:overworld");
	private static final Place NETHER = new Place("mp:play.example.net", "minecraft:the_nether");
	private static final Place OTHER_SERVER = new Place("mp:other.example.net", "minecraft:overworld");
	private static final Corners BOX = new Corners(new Pos(1, 64, 2), new Pos(9, 70, -8));

	@TempDir
	Path dir;

	private final List<String> log = new ArrayList<>();

	private AreaStore store() {
		return new AreaStore(dir.resolve("survivalkit").resolve("areas.json"), log::add);
	}

	@Test
	void anAreaIsThereAgainAfterARestart() {
		store().put(OVERWORLD, BOX);

		assertEquals(BOX, store().get(OVERWORLD));
	}

	@Test
	void anotherDimensionOrServerHasNoneOfIt() {
		AreaStore store = store();
		store.put(OVERWORLD, BOX);

		assertEquals(Corners.NONE, store.get(NETHER));
		assertEquals(Corners.NONE, store.get(OTHER_SERVER));
		assertEquals(Corners.NONE, store().get(NETHER));
	}

	@Test
	void eachPlaceKeepsItsOwn() {
		Corners nether = new Corners(new Pos(0, 30, 0), null);
		AreaStore store = store();
		store.put(OVERWORLD, BOX);
		store.put(NETHER, nether);

		AreaStore again = store();
		assertEquals(BOX, again.get(OVERWORLD));
		assertEquals(nether, again.get(NETHER));
	}

	@Test
	void clearingOnePlaceLeavesTheOthers() {
		AreaStore store = store();
		store.put(OVERWORLD, BOX);
		store.put(NETHER, BOX);
		store.put(OVERWORLD, Corners.NONE);

		assertEquals(Corners.NONE, store().get(OVERWORLD));
		assertEquals(BOX, store().get(NETHER));
	}

	@Test
	void aBrokenFileCostsOnlyWhatIsBroken() throws IOException {
		Path file = dir.resolve("survivalkit").resolve("areas.json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, """
			{"mp:play.example.net": {
			  "minecraft:overworld": {"first": [1, 64, 2], "second": "not a corner"},
			  "minecraft:the_nether": 7
			}, "mp:x": []}
			""");

		assertEquals(new Corners(new Pos(1, 64, 2), null), store().get(OVERWORLD));
		assertEquals(Corners.NONE, store().get(NETHER));
	}

	@Test
	void anUnreadableFileIsLoggedNotThrown() throws IOException {
		Path file = dir.resolve("survivalkit").resolve("areas.json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, "{ this is not json");

		assertEquals(Corners.NONE, store().get(OVERWORLD));
		assertTrue(log.size() == 1, "bir uyarı satırı: " + log);
	}
}
