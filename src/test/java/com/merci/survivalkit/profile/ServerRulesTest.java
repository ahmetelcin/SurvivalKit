package com.merci.survivalkit.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.merci.survivalkit.text.TestLang;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Which profile a world chooses: address rules with wildcards, singleplayer, and nothing matching. */
class ServerRulesTest {
	private static final Set<String> PROFILES = Set.of("default", "safe", "pvp", "skyblock", "tek");

	private static ServerRules rules(ServerRules.Rule... rules) {
		ServerRules made = new ServerRules();
		made.setRules(List.of(rules));
		return made;
	}

	private static Optional<String> chosen(ServerRules rules, String address) {
		return rules.choose(address, PROFILES::contains).map(ServerRules.Choice::profile);
	}

	@Test
	void aWildcardMatchesEverythingUnderTheDomainAndTheDomainItself() {
		assertTrue(ServerRules.matches("*.ornek.net", "mc.ornek.net"));
		assertTrue(ServerRules.matches("*.ornek.net", "a.b.ornek.net"));
		assertTrue(ServerRules.matches("*.ornek.net", "ornek.net"));
		assertFalse(ServerRules.matches("*.ornek.net", "kotuornek.net"));
		assertFalse(ServerRules.matches("*.ornek.net", "ornek.net.tr"));
	}

	@Test
	void anAddressIsAnAddressWhateverItsCaseOrTrailingDot() {
		assertTrue(ServerRules.matches("Play.Ornek.NET", "play.ornek.net."));
		assertTrue(ServerRules.matches(" play.ornek.net ", "PLAY.ornek.net"));
	}

	@Test
	void thePortCountsOnlyIfTheRuleNamesOne() {
		assertTrue(ServerRules.matches("play.ornek.net", "play.ornek.net:25570"));
		assertTrue(ServerRules.matches("play.ornek.net:25570", "play.ornek.net:25570"));
		assertFalse(ServerRules.matches("play.ornek.net:25570", "play.ornek.net"));
		assertTrue(ServerRules.matches("play.ornek.net:25565", "play.ornek.net"));
	}

	@Test
	void aStarAnywhere() {
		assertTrue(ServerRules.matches("local*", "localhost:51234"));
		assertTrue(ServerRules.matches("*", "anything.at.all"));
		assertTrue(ServerRules.matches("mc*.ornek.net", "mc2.ornek.net"));
		assertFalse(ServerRules.matches("mc*.ornek.net", "play.ornek.net"));
	}

	@Test
	void aBlankRuleMatchesNothing() {
		assertFalse(ServerRules.matches("", "ornek.net"));
		assertFalse(ServerRules.matches("  ", "ornek.net"));
	}

	@Test
	void theFirstMatchingRuleWins() {
		ServerRules rules = rules(new ServerRules.Rule("sky.ornek.net", "skyblock"), new ServerRules.Rule("*.ornek.net", "pvp"));

		assertEquals(Optional.of("skyblock"), chosen(rules, "sky.ornek.net"));
		assertEquals(Optional.of("pvp"), chosen(rules, "lobby.ornek.net"));
	}

	@Test
	void aRuleForAProfileThatIsGoneIsPassedOver() {
		ServerRules rules = rules(new ServerRules.Rule("*.ornek.net", "silindi"), new ServerRules.Rule("*", "pvp"));

		assertEquals(Optional.of("pvp"), chosen(rules, "mc.ornek.net"));
	}

	@Test
	void nothingMatchingKeepsTheProfileUnlessToldOtherwise() {
		ServerRules rules = rules(new ServerRules.Rule("*.ornek.net", "pvp"));

		assertEquals(Optional.empty(), chosen(rules, "baska.net"));

		rules.setFallback("safe");
		ServerRules.Choice choice = rules.choose("baska.net", PROFILES::contains).orElseThrow();
		assertEquals("safe", choice.profile());
		assertEquals(ServerRules.Reason.FALLBACK, choice.reason());
	}

	/** Singleplayer has its own; without one it keeps the profile — the fallback is for servers. */
	@Test
	void singleplayerHasItsOwn() {
		ServerRules rules = rules(new ServerRules.Rule("*", "pvp"));
		rules.setFallback("safe");

		assertEquals(Optional.empty(), chosen(rules, null));

		rules.setSingleplayer("tek");
		assertEquals(Optional.of("tek"), chosen(rules, null));
		assertEquals(ServerRules.Reason.SINGLEPLAYER, rules.choose(null, PROFILES::contains).orElseThrow().reason());
	}

	/** Out of the box there are no rules at all: no server is chosen for. */
	@Test
	void outOfTheBoxNothingIsChosen() {
		ServerRules rules = ServerRules.read(new JsonObject());

		assertEquals(List.of(), rules.rules());
		assertEquals(Optional.empty(), chosen(rules, "play.example.net"));
		assertEquals(Optional.empty(), chosen(rules, null));
	}

	@Test
	void writtenAndReadBackTheyAreTheSame() {
		ServerRules rules = rules(new ServerRules.Rule("*.ornek.net", "pvp"), new ServerRules.Rule("sky.net", "skyblock"));
		rules.setSingleplayer("tek");
		JsonObject state = new JsonObject();
		state.addProperty("active", "pvp");

		rules.write(state);
		ServerRules read = ServerRules.read(state);

		assertEquals(rules.rules(), read.rules());
		assertEquals("tek", read.singleplayer());
		assertEquals(null, read.fallback());
		assertEquals("pvp", state.get("active").getAsString());
	}

	@Test
	void renamedAndDeletedProfilesAreFollowed() {
		ServerRules rules = rules(new ServerRules.Rule("*.ornek.net", "pvp"), new ServerRules.Rule("sky.net", "skyblock"));
		rules.setSingleplayer("pvp");
		rules.setFallback("skyblock");

		rules.renamed("pvp", "arena");
		rules.deleted("skyblock");

		assertEquals(List.of(new ServerRules.Rule("*.ornek.net", "arena")), rules.rules());
		assertEquals("arena", rules.singleplayer());
		assertEquals(null, rules.fallback());
	}

	@Test
	void theNoticeSaysWhichAndWhy() {
		TestLang.install("tr_tr");

		assertEquals(List.of("Profil: Güvenli", "Kural: *.ornek.net"),
			ServerRules.notice(new ServerRules.Choice("safe", ServerRules.Reason.RULE, "*.ornek.net")));
		assertEquals(List.of("Profil: tek", "Tek oyunculu dünya"),
			ServerRules.notice(new ServerRules.Choice("tek", ServerRules.Reason.SINGLEPLAYER, null)));
	}
}
