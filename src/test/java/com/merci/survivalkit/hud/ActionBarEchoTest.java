package com.merci.survivalkit.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A HUD box's lines on the action bar: once when they change, again every second, nothing when there is nothing. */
class ActionBarEchoTest {
	private final ActionBarEcho echo = new ActionBarEcho();

	@Test
	void theLinesAreSaidAsOneAtOnce() {
		assertEquals(Optional.of(new HudLine("a · b", false)), echo.next(List.of(HudLine.of("a"), HudLine.of("b"))));
	}

	@Test
	void unchangedTheyAreSaidAgainOnlyOnceASecond() {
		List<HudLine> lines = List.of(HudLine.of("a"));
		echo.next(lines);
		int said = 0;

		for (int tick = 1; tick <= 2 * ActionBarEcho.REFRESH_TICKS; tick++) {
			if (echo.next(lines).isPresent()) {
				said++;
			}
		}

		assertEquals(2, said);
	}

	@Test
	void changedTheyAreSaidAtOnce() {
		echo.next(List.of(HudLine.of("a")));

		assertEquals(Optional.of(HudLine.of("b")), echo.next(List.of(HudLine.of("b"))));
	}

	@Test
	void aWarningAnywhereMakesTheLineAWarning() {
		assertTrue(echo.next(List.of(HudLine.of("a"), new HudLine("b", true))).orElseThrow().warning());
	}

	@Test
	void withNothingToSayNothingIsSaidAndTheNextLineIsSaidAtOnce() {
		echo.next(List.of(HudLine.of("a")));

		assertEquals(Optional.empty(), echo.next(List.of()));
		assertEquals(Optional.of(HudLine.of("a")), echo.next(List.of(HudLine.of("a"))));
	}
}
