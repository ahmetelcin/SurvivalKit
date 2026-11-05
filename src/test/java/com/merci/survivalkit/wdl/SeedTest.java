package com.merci.survivalkit.wdl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Seeds are typed by hand, so they follow the same rules as the world creation box. */
class SeedTest {
	@Test
	void aNumberIsTheSeedItself() {
		assertEquals(1234567890123L, Seed.parse("1234567890123"));
	}

	@Test
	void aNegativeNumberIsKept() {
		assertEquals(-4172144997902289642L, Seed.parse("-4172144997902289642"));
	}

	@Test
	void surroundingWhitespaceIsIgnored() {
		assertEquals(42L, Seed.parse("  42  "));
	}

	/** Vanilla hashes non-numeric seeds, so the same word must give the same world. */
	@Test
	void aWordIsHashedTheWayVanillaDoes() {
		assertEquals("merhaba".hashCode(), Seed.parse("merhaba"));
	}

	@Test
	void aNumberTooBigForALongFallsBackToTheHash() {
		String tooBig = "99999999999999999999999";

		assertEquals(tooBig.hashCode(), Seed.parse(tooBig));
	}

	/** Nothing typed means no seed: the world keeps 0 and regenerates unexplored chunks flat. */
	@Test
	void anEmptyStringIsNoSeed() {
		assertEquals(0L, Seed.parse("   "));
	}

	// --- continuing the same world ---

	@Test
	void aSeedLearntThisTimeReplacesTheOldOne() {
		assertEquals(42L, Seed.keep(42L, java.util.OptionalLong.of(7L)));
	}

	@Test
	void notKnowingItThisTimeKeepsTheOneTheWorldAlreadyHas() {
		assertEquals(7L, Seed.keep(Seed.NONE, java.util.OptionalLong.of(7L)));
	}

	@Test
	void aNewWorldWithNoSeedStaysAtNone() {
		assertEquals(Seed.NONE, Seed.keep(Seed.NONE, java.util.OptionalLong.empty()));
	}
}
