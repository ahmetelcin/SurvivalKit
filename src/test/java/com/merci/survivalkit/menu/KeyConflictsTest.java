package com.merci.survivalkit.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.settings.KeybindSetting;
import java.util.List;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.TestNames;
import org.junit.jupiter.api.Test;

class KeyConflictsTest {
	private static final KeyUse ADVANCEMENTS = new KeyUse("vanilla:key.advancements", "İlerlemeler", "key.keyboard.l");
	private static final KeyUse INVENTORY = new KeyUse("vanilla:key.inventory", "Envanter", "key.keyboard.e");
	private static final KeyUse MENU = new KeyUse("survivalkit:menu", "SurvivalKit menüsü", "key.keyboard.right.shift");
	private static final KeyUse WDL = new KeyUse("wdl", "Dünya İndirici", KeybindSetting.UNBOUND);

	private static final List<KeyUse> USES = List.of(ADVANCEMENTS, INVENTORY, MENU, WDL);

	@Test
	void aFreeKeyClashesWithNothing() {
		assertTrue(KeyConflicts.firstFor("key.keyboard.j", "autoeat", USES).isEmpty());
	}

	@Test
	void aVanillaBindingIsNamed() {
		assertEquals("İlerlemeler", KeyConflicts.firstFor("key.keyboard.l", "autoeat", USES).orElseThrow());
	}

	/** The one that would otherwise be unreachable: the key that opens this very menu. */
	@Test
	void theMenuKeyIsReported() {
		assertEquals("SurvivalKit menüsü",
				KeyConflicts.firstFor("key.keyboard.right.shift", "autoeat", USES).orElseThrow());
	}

	@Test
	void anotherModuleIsReported() {
		List<KeyUse> uses = List.of(new KeyUse("wdl", "Dünya İndirici", "key.keyboard.k"));

		assertEquals("Dünya İndirici", KeyConflicts.firstFor("key.keyboard.k", "autoeat", uses).orElseThrow());
	}

	@Test
	void aBindingDoesNotClashWithItself() {
		List<KeyUse> uses = List.of(new KeyUse("autoeat", "Otomatik Yeme", "key.keyboard.k"));

		assertTrue(KeyConflicts.firstFor("key.keyboard.k", "autoeat", uses).isEmpty());
	}

	@Test
	void anUnboundKeyClashesWithNothingEvenWhenOthersAreUnbound() {
		assertTrue(KeyConflicts.firstFor(KeybindSetting.UNBOUND, "autoeat", USES).isEmpty());
		assertTrue(KeyConflicts.firstFor("", "autoeat", USES).isEmpty());
		assertTrue(KeyConflicts.firstFor(null, "autoeat", USES).isEmpty());
	}

	@Test
	void nothingIsUsingAnythingWhenTheListIsEmpty() {
		assertTrue(KeyConflicts.firstFor("key.keyboard.l", "autoeat", List.of()).isEmpty());
	}

	// --- the warning as it is shown ---

	@org.junit.jupiter.api.BeforeEach
	void language() {
		TestNames.install();
		TestNames.put(Keys.MENU_CONFLICT, "%s ile çakışıyor");
	}

	@Test
	void theWarningNamesTheActionItClashesWith() {
		assertEquals("İlerlemeler ile çakışıyor",
				KeyConflicts.warningFor("key.keyboard.l", "autoeat", USES).orElseThrow());
	}

	@Test
	void thereIsNoWarningForAFreeKey() {
		assertTrue(KeyConflicts.warningFor("key.keyboard.j", "autoeat", USES).isEmpty());
	}
}
