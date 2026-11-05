package com.merci.survivalkit.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PickerModelTest {
	private static final ItemChoice BREAD = new ItemChoice("minecraft:bread", "Ekmek");
	private static final ItemChoice BEEF = new ItemChoice("minecraft:cooked_beef", "Pişmiş Biftek");
	private static final ItemChoice PORK = new ItemChoice("minecraft:cooked_porkchop", "Pişmiş Domuz Pirzolası");
	private static final ItemChoice APPLE = new ItemChoice("minecraft:golden_apple", "Altın Elma");

	private final List<String> picked = new ArrayList<>();

	private PickerModel model() {
		return new PickerModel(List.of(BREAD, BEEF, PORK, APPLE), () -> picked);
	}

	@Test
	void everythingIsOfferedToBeginWith() {
		assertEquals(4, model().visible().size());
	}

	@Test
	void aSearchNarrowsByName() {
		PickerModel model = model();

		model.setQuery("Ekmek");

		assertEquals(List.of(BREAD), model.visible());
	}

	@Test
	void aSearchNarrowsById() {
		PickerModel model = model();

		model.setQuery("golden_apple");

		assertEquals(List.of(APPLE), model.visible());
	}

	/** The case the Turkish folding exists for. */
	@Test
	void aKeyboardWithoutTurkishLettersStillFindsIt() {
		PickerModel model = model();

		model.setQuery("pismis");

		assertEquals(List.of(BEEF, PORK), model.visible());
	}

	@Test
	void searchIgnoresLetterCase() {
		PickerModel model = model();

		model.setQuery("PISMIS");

		assertEquals(List.of(BEEF, PORK), model.visible());
	}

	@Test
	void clearingTheSearchBringsEverythingBack() {
		PickerModel model = model();
		model.setQuery("ekmek");

		model.setQuery("");

		assertEquals(4, model.visible().size());
	}

	@Test
	void aSearchThatMatchesNothingShowsNothing() {
		PickerModel model = model();

		model.setQuery("elmas kılıç");

		assertEquals(List.of(), model.visible());
	}

	/** Narrowing is done once per keystroke, so the same query must not redo the work. */
	@Test
	void repeatingTheSameQueryKeepsTheSameResult() {
		PickerModel model = model();
		model.setQuery("pismis");
		List<ItemChoice> first = model.visible();

		model.setQuery("pismis");

		assertSame(first, model.visible());
	}

	@Test
	void anUntouchedSearchDoesNotCopyTheList() {
		PickerModel model = model();

		assertSame(model.all(), model.visible());
	}

	// --- what is already on the list ---

	@Test
	void nothingIsMarkedWhenTheListIsEmpty() {
		PickerModel model = model();

		assertFalse(model.isPicked(BREAD));
	}

	@Test
	void somethingOnTheListIsMarked() {
		picked.add("minecraft:bread");
		PickerModel model = model();

		assertTrue(model.isPicked(BREAD));
		assertFalse(model.isPicked(BEEF));
	}

	/** The picker stays open while things are added, so it has to notice them arriving. */
	@Test
	void theMarkFollowsTheListWhileThePickerStaysOpen() {
		PickerModel model = model();
		assertFalse(model.isPicked(BEEF));

		picked.add("minecraft:cooked_beef");

		assertTrue(model.isPicked(BEEF));
	}
}
