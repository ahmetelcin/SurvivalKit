package com.merci.survivalkit.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** ↺ after a preset: one setting at a time back to what it was before, while the preset's value still stands. */
class PresetUndoTest {
	private final PresetValuesTest.Probe probe = new PresetValuesTest.Probe();
	private final PresetUndo undo = new PresetUndo();

	private void apply(String json) {
		undo.record(PresetValues.apply(probe.settings(), JsonParser.parseString(json).getAsJsonObject()));
	}

	@Test
	void eachChangedSettingGoesBackOnItsOwn() {
		probe.width.set(2);
		apply("{\"width\": 3, \"torches\": true}");

		assertTrue(undo.undo(probe.width));

		assertEquals(2, probe.width.get());
		assertTrue(probe.torches.get());
		assertTrue(undo.canUndo(probe.torches));
		assertFalse(undo.canUndo(probe.width));
	}

	@Test
	void aSettingThePresetDidNotChangeHasNothingToUndo() {
		apply("{\"width\": 1, \"torches\": true}");

		assertFalse(undo.canUndo(probe.width));
		assertFalse(undo.canUndo(probe.depth));
	}

	/** Changed again by hand, the preset's value is gone and so is going back past it. */
	@Test
	void changedByHandItIsNoLongerThePresets() {
		apply("{\"width\": 3}");
		probe.width.set(2);

		assertFalse(undo.canUndo(probe.width));
		assertFalse(undo.undo(probe.width));
		assertEquals(2, probe.width.get());
	}

	@Test
	void twoPresetsInARowGoBackToBeforeTheFirst() {
		apply("{\"width\": 2}");
		apply("{\"width\": 3}");

		undo.undo(probe.width);

		assertEquals(1, probe.width.get());
	}

	/** The second preset put it back where it started: nothing is left to undo. */
	@Test
	void backWhereItStartedThereIsNothingToUndo() {
		apply("{\"width\": 3}");
		apply("{\"width\": 1}");

		assertFalse(undo.canUndo(probe.width));
	}

	@Test
	void clearedThereIsNothingToUndo() {
		apply("{\"width\": 3}");

		undo.clear();

		assertFalse(undo.canUndo(probe.width));
	}
}
