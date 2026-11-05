package com.merci.survivalkit.module.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.scaffold.SupportMode;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.settings.RegistryListSetting;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.merci.survivalkit.text.TestLang;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** "Only the listed" with the list empty is no limit, and the support list says so under itself. */
class ScaffoldSupportsNoteTest {
	private final ScaffoldModule module = new ScaffoldModule();
	private final EnumSetting<SupportMode> mode = module.settings().<EnumSetting<SupportMode>>byId("support_mode").orElseThrow();
	private final RegistryListSetting supports = module.settings().<RegistryListSetting>byId("supports").orElseThrow();

	@BeforeEach
	void english() {
		TestLang.install("en_us");
	}

	@Test
	void listedWithTheListEmptyTheNoteIsShown() {
		mode.set(SupportMode.LISTED);

		assertEquals(Optional.of(Lang.of(Keys.SCAFFOLD_SUPPORTS_EMPTY)), supports.note());
		assertEquals("List empty: builds against any block", supports.note().orElseThrow());
	}

	@Test
	void withSomethingListedThereIsNoNote() {
		mode.set(SupportMode.LISTED);
		supports.set(List.of("minecraft:stone"));

		assertTrue(supports.note().isEmpty());
	}

	@Test
	void withAnyBlockThereIsNoNote() {
		assertTrue(supports.note().isEmpty());
	}
}
