package com.merci.survivalkit.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Where an open list of options sits and which of them the mouse is over.
 *
 * <p>Pure geometry on purpose. What went wrong the first time round was a drawing rule and a
 * clicking rule that disagreed, so here there is one rule and a test can ask it the same
 * questions the screen does.
 */
class DropdownTest {
	private static final String ID = "fallback";
	private static final int BUTTON_X = 120;
	private static final int BUTTON_TOP = 40;
	private static final int BUTTON_BOTTOM = 52;
	private static final int WIDTH = 96;
	private static final int OPTIONS = 4;

	/** The settings panel the list has to stay inside. */
	private static final int PANEL_TOP = 30;
	private static final int PANEL_BOTTOM = 200;

	private Dropdown dropdown;

	@BeforeEach
	void setUp() {
		dropdown = new Dropdown();
	}

	private void openIt() {
		dropdown.open(ID, BUTTON_X, BUTTON_TOP, BUTTON_BOTTOM, WIDTH, OPTIONS, PANEL_TOP, PANEL_BOTTOM);
	}

	@Test
	void aFreshDropdownIsClosed() {
		assertFalse(dropdown.isOpen());
		assertFalse(dropdown.isOpen(ID));
	}

	@Test
	void openingItRemembersWhichSettingItBelongsTo() {
		openIt();

		assertTrue(dropdown.isOpen());
		assertTrue(dropdown.isOpen(ID));
		assertFalse(dropdown.isOpen("other"));
	}

	@Test
	void closingItForgetsEverything() {
		openIt();

		dropdown.close();

		assertFalse(dropdown.isOpen());
		assertEquals(OptionalInt.empty(), dropdown.optionAt(BUTTON_X + 4, BUTTON_BOTTOM + 4));
	}

	/** Under the button, as wide as the button: what is clicked is what is drawn. */
	@Test
	void theListHangsUnderTheButtonAndIsAsWideAsIt() {
		openIt();

		Dropdown.Bounds bounds = dropdown.bounds();

		assertEquals(BUTTON_X, bounds.x());
		assertEquals(BUTTON_BOTTOM, bounds.y());
		assertEquals(WIDTH, bounds.width());
		assertEquals(OPTIONS * Dropdown.ROW_HEIGHT, bounds.height());
	}

	/**
	 * A setting near the bottom of the panel would hang its list off the end. It opens
	 * upwards instead, which keeps every option both visible and clickable.
	 */
	@Test
	void nearTheBottomItOpensUpwardsInstead() {
		int buttonTop = PANEL_BOTTOM - 20;
		int buttonBottom = buttonTop + 12;

		dropdown.open(ID, BUTTON_X, buttonTop, buttonBottom, WIDTH, OPTIONS, PANEL_TOP, PANEL_BOTTOM);

		Dropdown.Bounds bounds = dropdown.bounds();

		assertEquals(buttonTop - OPTIONS * Dropdown.ROW_HEIGHT, bounds.y());
		assertTrue(bounds.y() >= PANEL_TOP, "liste panelin üstünden de çıkmamalı");
	}

	@Test
	void theMouseOverTheFirstRowIsTheFirstOption() {
		openIt();

		assertEquals(OptionalInt.of(0), dropdown.optionAt(BUTTON_X + 10, BUTTON_BOTTOM + 1));
	}

	@Test
	void everyRowAnswersWithItsOwnIndex() {
		openIt();

		for (int index = 0; index < OPTIONS; index++) {
			double middle = BUTTON_BOTTOM + index * Dropdown.ROW_HEIGHT + Dropdown.ROW_HEIGHT / 2.0;

			assertEquals(OptionalInt.of(index), dropdown.optionAt(BUTTON_X + 10, middle),
				"satır " + index + " kendi sırasını vermeliydi");
		}
	}

	@Test
	void theMouseBesideTheListIsOverNoOption() {
		openIt();

		assertEquals(OptionalInt.empty(), dropdown.optionAt(BUTTON_X - 1, BUTTON_BOTTOM + 4));
		assertEquals(OptionalInt.empty(), dropdown.optionAt(BUTTON_X + WIDTH + 1, BUTTON_BOTTOM + 4));
	}

	@Test
	void theMouseAboveOrBelowTheListIsOverNoOption() {
		openIt();

		assertEquals(OptionalInt.empty(), dropdown.optionAt(BUTTON_X + 10, BUTTON_BOTTOM - 1));
		assertEquals(OptionalInt.empty(),
			dropdown.optionAt(BUTTON_X + 10, BUTTON_BOTTOM + OPTIONS * Dropdown.ROW_HEIGHT + 1));
	}

	@Test
	void containsAgreesWithTheRowsItOffers() {
		openIt();

		assertTrue(dropdown.contains(BUTTON_X + 10, BUTTON_BOTTOM + 4));
		assertFalse(dropdown.contains(BUTTON_X + 10, BUTTON_BOTTOM - 4));
		assertFalse(dropdown.contains(BUTTON_X - 4, BUTTON_BOTTOM + 4));
	}

	@Test
	void aClosedDropdownContainsNothing() {
		assertFalse(dropdown.contains(BUTTON_X + 10, BUTTON_BOTTOM + 4));
	}

	/** Each row's top, so the screen draws the labels where the clicks are answered. */
	@Test
	void theRowTopsAreWhereTheOptionsAreAnswered() {
		openIt();

		for (int index = 0; index < OPTIONS; index++) {
			int top = dropdown.optionTop(index);

			assertEquals(OptionalInt.of(index), dropdown.optionAt(BUTTON_X + 10, top + 1),
				"satır " + index + " çizildiği yerde tıklanmalıydı");
		}
	}
}
