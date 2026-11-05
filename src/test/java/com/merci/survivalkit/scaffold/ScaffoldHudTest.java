package com.merci.survivalkit.scaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.hud.HudLine;
import com.merci.survivalkit.text.TestLang;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Scaffold on the HUD, with "show on the action bar too" off as it ships: the blocks left and the
 * locked y the whole time, what it has to say on top for three seconds, and nothing on the
 * action bar. The floor ends at x = -1; walking is east.
 */
class ScaffoldHudTest {
	private static final String COBBLE = "minecraft:cobblestone";

	private final FakeScaffoldSettings settings = new FakeScaffoldSettings();
	private final FakeScaffoldContext client = new FakeScaffoldContext();
	private final Scaffold scaffold = new Scaffold(settings, new Hands());

	@BeforeEach
	void onTheFloorWithCobble() {
		TestLang.install("en_us");
		settings.actionBar = false;
		client.floorTo(-1);
		client.give(0, COBBLE, 64);
		client.feet = new Vec(-2.5, 64, 0.5);
	}

	@Test
	void standingStillTheCountIsThereAndTheActionBarSaysNothing() {
		scaffold.tick(client);

		assertEquals(List.of(HudLine.of("Scaffold: 64 blocks")), scaffold.hudLines());
		assertTrue(client.actionBar.isEmpty(), client.actionBar.toString());
	}

	@Test
	void aBlockDownTheCountGoesDown() {
		client.feet = new Vec(0.5, 64, 0.5);
		scaffold.tick(client);

		assertEquals(List.of(HudLine.of("Scaffold: 63 blocks")), scaffold.hudLines());
		assertTrue(client.actionBar.isEmpty(), client.actionBar.toString());
	}

	@Test
	void lockedTheYIsBesideTheCount() {
		settings.yLock = YLock.FIXED;
		settings.fixedY = 70;
		scaffold.tick(client);

		assertEquals(List.of(HudLine.of("Scaffold: 64 blocks · Y 70")), scaffold.hudLines());
	}

	@Test
	void withTheCountOffTheLockedYAlone() {
		settings.showCount = false;
		settings.yLock = YLock.FIXED;
		settings.fixedY = 70;
		scaffold.tick(client);

		assertEquals(List.of(HudLine.of("Scaffold: Y 70")), scaffold.hudLines());
	}

	@Test
	void fewBlocksAreAWarning() {
		client.give(0, COBBLE, 5);
		scaffold.tick(client);

		assertEquals(List.of(new HudLine("Scaffold: only 5 blocks left!", true)), scaffold.hudLines());
	}

	/** The lock key's line goes on top, on the HUD and not the action bar, and goes after three seconds. */
	@Test
	void whatIsSaidIsOnTheHudForThreeSeconds() {
		settings.yLock = YLock.START;
		client.lockKeyHeld = true;
		scaffold.tick(client);
		client.lockKeyHeld = false;

		assertEquals(List.of(HudLine.of("Scaffold: Y locked at 63"), HudLine.of("Scaffold: 64 blocks · Y 63")), scaffold.hudLines());
		assertTrue(client.actionBar.isEmpty(), client.actionBar.toString());

		for (int tick = 0; tick < Scaffold.MESSAGE_TICKS; tick++) {
			scaffold.tick(client);
		}

		assertEquals(List.of(HudLine.of("Scaffold: 64 blocks · Y 63")), scaffold.hudLines());
	}

	@Test
	void withTheActionBarOnItIsSaidThereToo() {
		settings.actionBar = true;
		settings.yLock = YLock.START;
		client.lockKeyHeld = true;
		scaffold.tick(client);

		assertEquals(List.of("Scaffold: Y locked at 63"), client.actionBar);
		assertEquals(HudLine.of("Scaffold: Y locked at 63"), scaffold.hudLines().getFirst());
	}

	@Test
	void switchedOffThereIsNothing() {
		scaffold.tick(client);
		scaffold.stop(client);

		assertEquals(List.of(), scaffold.hudLines());
	}
}
