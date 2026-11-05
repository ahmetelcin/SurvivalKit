package com.merci.survivalkit.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SearchTextTest {
	private final Locale original = Locale.getDefault();

	@AfterEach
	void restoreLocale() {
		Locale.setDefault(original);
	}

	@Test
	void everyTurkishLetterFoldsToItsPlainCounterpart() {
		assertEquals("cgiosu", SearchText.fold("çğıöşü"));
		assertEquals("cgiosu", SearchText.fold("ÇĞİÖŞÜ"));
	}

	@Test
	void aDotlessCapitalIFoldsToPlainI() {
		assertEquals("i", SearchText.fold("I"));
	}

	@Test
	void plainTextIsJustLowercased() {
		assertEquals("bread", SearchText.fold("BREAD"));
	}

	@Test
	void nothingInMeansNothingOut() {
		assertEquals("", SearchText.fold(null));
		assertEquals("", SearchText.fold(""));
	}

	// --- the cases the menu has to get right ---

	@Test
	void theTurkishSpellingFindsTheModule() {
		assertTrue(SearchText.contains("Dünya İndirici", "indirici"));
	}

	@Test
	void allCapsFindsTheModule() {
		assertTrue(SearchText.contains("Dünya İndirici", "INDIRICI"));
	}

	@Test
	void aKeyboardWithoutTurkishLettersFindsTheModule() {
		assertTrue(SearchText.contains("Dünya İndirici", "dunya"));
	}

	@Test
	void aKeyboardWithoutTurkishLettersFindsTheSetting() {
		assertTrue(SearchText.contains("Açlık eşiği", "esik"));
		assertTrue(SearchText.contains("Can eşiği", "esik"));
	}

	@Test
	void theItemPickerCaseFoldsTheSameWay() {
		assertTrue(SearchText.contains("Pişmiş Biftek", "pismis"));
	}

	@Test
	void somethingThatIsNotThereIsStillNotFound() {
		assertFalse(SearchText.contains("Dünya İndirici", "biftek"));
	}

	/**
	 * The whole reason this class exists: on a Turkish default locale the built-in
	 * lowercasing would fold "I" to "ı" and quietly break the match.
	 */
	@Test
	void aTurkishDefaultLocaleChangesNothing() {
		Locale.setDefault(Locale.of("tr", "TR"));

		assertTrue(SearchText.contains("Dünya İndirici", "INDIRICI"));
		assertTrue(SearchText.contains("Dünya İndirici", "indirici"));
		assertEquals("i", SearchText.fold("I"));
		assertEquals("i", SearchText.fold("İ"));
	}

	@Test
	void anEmptyNeedleMatchesAnything() {
		assertTrue(SearchText.contains("Dünya İndirici", ""));
	}

	/** "eşik" becomes "eşiği" when a suffix lands on it; the root still has to find it. */
	@Test
	void aSoftenedFinalKIsStillFound() {
		assertTrue(SearchText.contains("Açlık eşiği", "esik"));
		assertTrue(SearchText.contains("eşiği", "eşik"));
	}

	@Test
	void anOrdinaryWordStillMatchesItselfFirst() {
		assertTrue(SearchText.contains("Pişmiş Biftek", "biftek"));
	}

	@Test
	void theSofteningDoesNotMatchJustAnything() {
		assertFalse(SearchText.contains("Pişmiş Biftek", "tavuk"));
	}
}
