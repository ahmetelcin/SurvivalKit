package com.merci.survivalkit.wdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** The download lands in .minecraft/saves, so the folder name has to survive Windows. */
class SaveNameTest {
	private static final LocalDateTime WHEN = LocalDateTime.of(2026, 9, 22, 21, 58, 3);
	private static final String STAMP = "-20260922-215803";
	/** Everything Windows refuses in a path segment, backslash included. */
	private static final String FORBIDDEN = "/" + (char) 92 + ":*?" + '"' + "<>|";

	@Test
	void theAddressAndTheMomentMakeUpTheName() {
		assertEquals("mc.example.net" + STAMP, SaveName.of("mc.example.net", WHEN));
	}

	@Test
	void thePortIsDropped() {
		assertEquals("mc.example.net" + STAMP, SaveName.of("mc.example.net:25565", WHEN));
	}

	@Test
	void charactersWindowsRefusesAreReplaced() {
		String name = SaveName.of("a" + FORBIDDEN + "b", WHEN);

		for (char forbidden : FORBIDDEN.toCharArray()) {
			assertTrue(name.indexOf(forbidden) < 0, "yasak karakter kaldı: " + forbidden + " (" + name + ")");
		}
	}

	@Test
	void anEmptyAddressStillProducesAUsableName() {
		assertEquals("sunucu" + STAMP, SaveName.of("", WHEN));
	}

	@Test
	void anAddressOfOnlyBadCharactersStillProducesAUsableName() {
		assertEquals("sunucu" + STAMP, SaveName.of("///", WHEN));
	}

	/** Windows paths are capped, and the save folder already sits several levels deep. */
	@Test
	void aVeryLongAddressIsTruncated() {
		String name = SaveName.of("a".repeat(200), WHEN);

		assertTrue(name.length() <= SaveName.MAX_LENGTH, "ad çok uzun: " + name.length());
		assertTrue(name.endsWith(STAMP), "zaman damgası korunmalı: " + name);
	}

	@Test
	void trailingDotsAndSpacesAreDropped() {
		assertEquals("mc.example" + STAMP, SaveName.of("mc.example. ", WHEN));
	}

	// --- continuing the same world ---

	@Test
	void everyDownloadFromAServerGoesToTheSameFolder() {
		assertEquals(SaveName.forServer("oyna.example.net:25565"), SaveName.forServer("oyna.example.net"));
		assertEquals("oyna.example.net", SaveName.forServer("oyna.example.net:25565"));
	}

	@Test
	void twoServersNeverShareAFolder() {
		assertNotEquals(SaveName.forServer("a.example.net"), SaveName.forServer("b.example.net"));
	}

	@Test
	void theContinuedFolderIsCleanedAndCappedTheSameWay() {
		assertEquals("sunucu", SaveName.forServer(":25565"));
		assertTrue(SaveName.forServer("x".repeat(200)).length() <= SaveName.MAX_LENGTH);
	}
}
