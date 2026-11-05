package com.merci.survivalkit.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A profile's name is its file's name: whatever no system would take, or one already there, will not do. */
class ProfileNamesTest {
	private static final List<String> TAKEN = List.of("default", "Survival");

	private static Optional<ProfileNames.Problem> check(String name) {
		return ProfileNames.check(name, TAKEN, null);
	}

	@Test
	void anOrdinaryNameIsFine() {
		assertEquals(Optional.empty(), check("Example SkyBlock"));
		assertEquals(Optional.empty(), check("Güvenli-2"));
	}

	@Test
	void blankIsNoName() {
		assertEquals(Optional.of(ProfileNames.Problem.BLANK), check("   "));
		assertEquals(Optional.of(ProfileNames.Problem.BLANK), check(".."));
	}

	@Test
	void tooLong() {
		assertEquals(Optional.of(ProfileNames.Problem.TOO_LONG), check("x".repeat(ProfileNames.MAX_LENGTH + 1)));
	}

	@Test
	void whatAFileNameCannotHold() {
		for (String bad : List.of("a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b", "dot.")) {
			assertEquals(Optional.of(ProfileNames.Problem.BAD_CHARACTER), check(bad), bad);
		}
	}

	/** On Windows "survival" and "Survival" are one file. */
	@Test
	void takenWhateverTheCase() {
		assertEquals(Optional.of(ProfileNames.Problem.TAKEN), check("survival"));
		assertEquals(Optional.of(ProfileNames.Problem.TAKEN), check("DEFAULT"));
	}

	@Test
	void renamedAProfileMayKeepItsOwnNameInAnotherCase() {
		assertEquals(Optional.empty(), ProfileNames.check("SURVIVAL", TAKEN, "Survival"));
		assertEquals(Optional.of(ProfileNames.Problem.TAKEN), ProfileNames.check("default", TAKEN, "Survival"));
	}
}
