package com.merci.survivalkit.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A module's HUD lines kept between ticks, echoed on the action bar only when asked. */
class ModuleStatusTest {
	private final ModuleStatus status = new ModuleStatus();
	private final List<HudLine> actionBar = new ArrayList<>();

	@Test
	void theLinesAreKept() {
		status.update(List.of(HudLine.of("a")), false, actionBar::add);

		assertEquals(List.of(HudLine.of("a")), status.lines());
	}

	@Test
	void offTheActionBarHearsNothing() {
		status.update(List.of(HudLine.of("a")), false, actionBar::add);

		assertEquals(List.of(), actionBar);
	}

	@Test
	void onTheActionBarHearsThem() {
		status.update(List.of(HudLine.of("a")), true, actionBar::add);

		assertEquals(List.of(HudLine.of("a")), actionBar);
	}

	@Test
	void clearedThereIsNothing() {
		status.update(List.of(HudLine.of("a")), true, actionBar::add);
		status.clear();

		assertEquals(List.of(), status.lines());
	}
}
