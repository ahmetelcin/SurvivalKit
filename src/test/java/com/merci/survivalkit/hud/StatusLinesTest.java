package com.merci.survivalkit.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.merci.survivalkit.text.TestLang;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What each module's HUD box says. */
class StatusLinesTest {
	@BeforeEach
	void english() {
		TestLang.install("en_us");
	}

	@Test
	void totemsCarried() {
		assertEquals(List.of(HudLine.of("Totems: 3")), StatusLines.totems(3));
	}

	@Test
	void noTotemsIsAWarning() {
		assertEquals(List.of(new HudLine("Totems: 0", true)), StatusLines.totems(0));
	}

	/** Seconds left, rounded up: a lock with a tick left is still locked. */
	@Test
	void theFightLockWithItsSecondsLeft() {
		assertEquals(List.of(HudLine.of("Auto Weapon: locked · 3 s")), StatusLines.weaponLock(60));
		assertEquals(List.of(HudLine.of("Auto Weapon: locked · 1 s")), StatusLines.weaponLock(1));
	}

	@Test
	void noFightLock() {
		assertEquals(List.of(HudLine.of("Auto Weapon: not locked")), StatusLines.weaponLock(0));
	}

	@Test
	void aVeinJobWithItsBlocksAndHowFarItHasGot() {
		assertEquals(List.of(HudLine.of("Vein Breaker: 12 blocks · 40%")), StatusLines.vein(OptionalInt.of(40), 12));
	}

	@Test
	void betweenVeinJobsNothing() {
		assertEquals(List.of(), StatusLines.vein(OptionalInt.empty(), 12));
	}

	@Test
	void theDownloader() {
		assertEquals(List.of(HudLine.of("World Downloader: downloading · 142 chunks")), StatusLines.downloader(false, true, 142));
		assertEquals(List.of(HudLine.of("World Downloader: not downloading · 142 chunks")), StatusLines.downloader(false, false, 142));
		assertEquals(List.of(new HudLine("World Downloader: the server does not allow it", true)), StatusLines.downloader(true, false, 0));
	}

	@Test
	void inTurkish() {
		TestLang.install("tr_tr");

		assertEquals(List.of(HudLine.of("Damar Kırıcı: 12 blok · %40")), StatusLines.vein(OptionalInt.of(40), 12));
		assertEquals(List.of(HudLine.of("Oto Silah: kilitli · 2 sn")), StatusLines.weaponLock(40));
	}
}
