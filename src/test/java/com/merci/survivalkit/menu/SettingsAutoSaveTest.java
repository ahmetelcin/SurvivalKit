package com.merci.survivalkit.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingsAutoSaveTest {
	private static final int DELAY = 20;

	private static boolean tickFor(SettingsAutoSave save, int ticks) {
		boolean fired = false;

		for (int tick = 0; tick < ticks; tick++) {
			fired |= save.tick();
		}

		return fired;
	}

	@Test
	void nothingIsDueWhileNothingHasChanged() {
		SettingsAutoSave save = new SettingsAutoSave(DELAY);

		assertFalse(tickFor(save, DELAY * 3));
		assertFalse(save.isPending());
	}

	@Test
	void aChangeIsWrittenAfterTheDelay() {
		SettingsAutoSave save = new SettingsAutoSave(DELAY);
		save.touch();

		assertFalse(tickFor(save, DELAY - 1), "süre dolmadan yazılmamalı");
		assertTrue(save.tick(), "süre dolunca yazılmalı");
	}

	@Test
	void theWriteHappensOnlyOnce() {
		SettingsAutoSave save = new SettingsAutoSave(DELAY);
		save.touch();
		tickFor(save, DELAY);

		assertFalse(tickFor(save, DELAY * 2));
	}

	/** Dragging a slider touches it every tick; the write waits for the dragging to stop. */
	@Test
	void aSecondChangePushesTheDeadlineBack() {
		SettingsAutoSave save = new SettingsAutoSave(DELAY);
		save.touch();
		tickFor(save, DELAY - 1);

		save.touch();

		assertFalse(tickFor(save, DELAY - 1), "ikinci değişiklik süreyi baştan başlatmalı");
		assertTrue(save.tick());
	}

	@Test
	void aChangeIsPendingUntilItIsWritten() {
		SettingsAutoSave save = new SettingsAutoSave(DELAY);
		save.touch();

		assertTrue(save.isPending());
		tickFor(save, DELAY);
		assertFalse(save.isPending());
	}

	@Test
	void flushingWritesAnOutstandingChangeStraightAway() {
		SettingsAutoSave save = new SettingsAutoSave(DELAY);
		save.touch();

		assertTrue(save.flush());
		assertFalse(save.isPending());
	}

	@Test
	void flushingWithNothingOutstandingWritesNothing() {
		assertFalse(new SettingsAutoSave(DELAY).flush());
	}

	@Test
	void aDelayOfNothingMakesNoSense() {
		assertThrows(IllegalArgumentException.class, () -> new SettingsAutoSave(0));
	}

	@Test
	void theDefaultDelayIsAboutASecond() {
		assertTrue(SettingsAutoSave.DELAY_TICKS == 20);
	}
}
