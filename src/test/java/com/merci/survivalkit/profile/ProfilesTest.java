package com.merci.survivalkit.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.module.Category;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.module.ModuleManager;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.text.TestLang;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Profiles on disk and the switch between them: the one left is saved, every module that was on
 * goes off through its own switch-off, the next is read over a clean slate, and what is on in it
 * comes on with the next tick.
 */
class ProfilesTest {
	@TempDir
	Path dir;

	private ModuleManager modules;
	private Probe first;
	private Probe second;
	private Profiles profiles;

	/** A module that counts its switch-ons and switch-offs, with one number to tell profiles apart. */
	static final class Probe extends Module {
		final IntSetting number = settings().add(new IntSetting("number", "test.number", 1, 0, 100));
		int enabled;
		int disabled;

		Probe(String id) {
			super(id, Category.MISC, KeybindSetting.UNBOUND);
		}

		@Override
		protected void onEnable() {
			enabled++;
		}

		@Override
		protected void onDisable() {
			disabled++;
		}
	}

	@BeforeEach
	void twoModules() {
		modules = new ModuleManager();
		first = new Probe("first");
		second = new Probe("second");
		modules.register(first);
		modules.register(second);
		profiles = new Profiles(store(), modules, null);
		profiles.load();
	}

	private ProfileStore store() {
		return new ProfileStore(dir.resolve("profiles"), dir.resolve("profiles.json"));
	}

	private void tick() {
		modules.tick(false, module -> false);
	}

	@Test
	void atFirstThereAreTheTwoBuiltIn() {
		assertEquals("default", profiles.active());
		assertEquals(List.of("default", "safe"), profiles.list());
	}

	@Test
	void aNewProfileStartsAtTheDefaultsAndIsSwitchedTo() {
		first.number.set(7);
		first.setEnabled(true);

		assertEquals(Optional.empty(), profiles.create("pvp"));

		assertEquals("pvp", profiles.active());
		assertEquals(1, first.number.get());
		assertFalse(first.isEnabled());
		assertEquals(List.of("default", "safe", "pvp"), profiles.list());
	}

	/** The one left keeps what it had: back on it, the number and the module that was on are back. */
	@Test
	void switchingBackBringsBackWhatWasThere() {
		first.number.set(7);
		first.setEnabled(true);
		profiles.create("pvp");
		tick();

		profiles.switchTo("default");
		tick();

		assertEquals(7, first.number.get());
		assertTrue(first.isEnabled());
	}

	/** Every module that was on is switched off through its own hook; one that was off is left alone. */
	@Test
	void aSwitchSwitchesOffWhatWasOnThroughItsOwnHook() {
		first.setEnabled(true);
		int before = first.disabled;

		profiles.create("pvp");

		assertEquals(before + 1, first.disabled);
		assertEquals(0, second.disabled);
	}

	/** On in the next profile and on in this one: still off and on again, so it starts clean. */
	@Test
	void onInBothItIsSwitchedOffAndOnAgain() throws IOException {
		Files.createDirectories(dir.resolve("profiles"));
		Files.writeString(dir.resolve("profiles").resolve("pvp.json"), "{\"first\": {\"enabled\": true}}");
		first.setEnabled(true);
		int on = first.enabled;

		profiles.switchTo("pvp");
		tick();

		assertEquals(1, first.disabled);
		assertEquals(on + 1, first.enabled);
		assertTrue(first.isEnabled());
	}

	@Test
	void theProfileInUseIsRemembered() {
		profiles.create("pvp");

		assertEquals("pvp", new Profiles(store(), modules, null).active());
	}

	@Test
	void aCopyHasWhatTheProfileHadNow() {
		first.number.set(9);

		assertEquals(Optional.empty(), profiles.copy("pvp kopya"));

		assertEquals("pvp kopya", profiles.active());
		assertEquals(9, first.number.get());
		assertTrue(Files.exists(dir.resolve("profiles").resolve("pvp kopya.json")));
	}

	@Test
	void renamedTheFileMovesAndItStaysInUse() {
		profiles.create("pvp");
		first.number.set(4);

		assertEquals(Optional.empty(), profiles.rename("arena"));

		assertEquals("arena", profiles.active());
		assertEquals(List.of("arena", "default", "safe").stream().sorted().toList(), profiles.list().stream().sorted().toList());
		assertFalse(Files.exists(dir.resolve("profiles").resolve("pvp.json")));
		assertEquals("arena", new Profiles(store(), modules, null).active());
		assertEquals(4, first.number.get());
	}

	@Test
	void deletedItIsGoneAndDefaultIsInUse() {
		profiles.create("pvp");
		first.setEnabled(true);

		assertTrue(profiles.delete());

		assertEquals("default", profiles.active());
		assertEquals(List.of("default", "safe"), profiles.list());
		assertEquals(1, first.disabled);
	}

	@Test
	void defaultCannotBeDeletedOrRenamed() {
		assertFalse(profiles.delete());
		assertEquals(Optional.of(ProfileNames.Problem.TAKEN), profiles.rename("other"));
		assertEquals("default", profiles.active());
	}

	@Test
	void aNameAlreadyThereIsRefusedAndNothingChanges() {
		profiles.create("pvp");
		profiles.switchTo("default");

		assertEquals(Optional.of(ProfileNames.Problem.TAKEN), profiles.create("PVP"));
		assertEquals("default", profiles.active());
	}

	/** In "safe" everything is off — even what its own file says is on, and even at the start. */
	@Test
	void inSafeEveryModuleIsOff() throws IOException {
		Files.createDirectories(dir.resolve("profiles"));
		Files.writeString(dir.resolve("profiles").resolve("safe.json"), "{\"first\": {\"enabled\": true, \"number\": 3}}");
		first.setEnabled(true);
		second.setEnabled(true);

		profiles.switchTo("safe");
		tick();

		assertFalse(first.isEnabled());
		assertFalse(second.isEnabled());
		assertEquals(3, first.number.get());

		Profiles restarted = new Profiles(store(), modules, null);
		first.setEnabled(true);
		restarted.load();
		tick();
		assertFalse(first.isEnabled());
	}

	@Test
	void safeCannotBeRenamedOrDeleted() {
		profiles.switchTo("safe");

		assertFalse(profiles.delete());
		assertEquals(Optional.of(ProfileNames.Problem.TAKEN), profiles.rename("other"));
		assertEquals("safe", profiles.active());
	}

	/** Neither its file name nor the name it is shown under can be taken by another. */
	@Test
	void safeIsTakenByEitherName() {
		TestLang.install("tr_tr");

		assertEquals(Optional.of(ProfileNames.Problem.TAKEN), profiles.create("SAFE"));
		assertEquals(Optional.of(ProfileNames.Problem.TAKEN), profiles.create("güvenli"));
		assertEquals("Güvenli", Profiles.label("safe"));
		assertEquals("pvp", Profiles.label("pvp"));
	}

	@Test
	void joiningAWorldSwitchesByTheRulesAndSaysWhy() {
		profiles.create("pvp");
		profiles.switchTo("default");
		ServerRules rules = new ServerRules();
		rules.setRules(List.of(new ServerRules.Rule("*.ornek.net", "pvp")));
		profiles.setRules(rules);

		ServerRules.Choice choice = profiles.joined("mc.ornek.net").orElseThrow();

		assertEquals("pvp", profiles.active());
		assertEquals(ServerRules.Reason.RULE, choice.reason());
		assertEquals("*.ornek.net", choice.rule());
		assertEquals(Optional.empty(), profiles.joined("lobby.ornek.net"), "zaten o profildeyken bir şey değişmez");
		assertEquals(Optional.empty(), profiles.joined("baska.net"));
		assertEquals("pvp", profiles.active());
	}

	@Test
	void theRulesFollowARenameAndForgetADelete() {
		profiles.create("pvp");
		ServerRules rules = new ServerRules();
		rules.setRules(List.of(new ServerRules.Rule("*.ornek.net", "pvp")));
		rules.setSingleplayer("pvp");
		profiles.setRules(rules);

		profiles.rename("arena");
		assertEquals(List.of(new ServerRules.Rule("*.ornek.net", "arena")), profiles.rules().rules());
		assertEquals("arena", profiles.rules().singleplayer());
		assertEquals("arena", new Profiles(store(), modules, null).active());

		profiles.delete();
		assertEquals(List.of(), profiles.rules().rules());
		assertEquals(null, profiles.rules().singleplayer());
	}

	/** A file that went missing is no profile to be in: the next start is in "default". */
	@Test
	void aMissingProfileFallsBackToDefault() throws IOException {
		profiles.create("pvp");
		Files.delete(dir.resolve("profiles").resolve("pvp.json"));

		assertEquals("default", new Profiles(store(), modules, null).active());
	}
}
