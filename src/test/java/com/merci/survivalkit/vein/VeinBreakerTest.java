package com.merci.survivalkit.vein;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VeinBreakerTest {
	private static final String OAK = "minecraft:oak_log";
	private static final String BIRCH = "minecraft:birch_log";
	private static final String IRON = "minecraft:iron_ore";
	private static final String DEEP_IRON = "minecraft:deepslate_iron_ore";
	private static final String COAL = "minecraft:coal_ore";

	private FakeVeinSettings settings;
	private FakeVeinContext client;
	private VeinBreaker breaker;

	@BeforeEach
	void setUp() {
		settings = new FakeVeinSettings();
		client = new FakeVeinContext();
		breaker = new VeinBreaker(settings, new Random(42));
	}

	/** A five-log trunk at x=0 z=0 from y=0, a branch going off diagonally, and leaves on top. */
	private void tree() {
		for (int y = 0; y < 5; y++) {
			client.put(new Pos(0, y, 0), OAK);
		}

		client.put(new Pos(1, 5, 1), OAK);
		client.naturalLeaves.add(new Pos(0, 5, 0));
		client.target = new Pos(0, 1, 0);
	}

	@Test
	void aTreeIsLootedLogByLogAndClearedAColumnAtATime() {
		tree();

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
		runToTheEnd();

		assertEquals(6, looted().size(), "her kütük için bir /loot: " + client.sent);
		assertTrue(looted().containsAll(List.of(
			"loot give @s mine 0 0 0 mainhand", "loot give @s mine 0 4 0 mainhand", "loot give @s mine 1 5 1 mainhand")));
		assertEquals(List.of(
			"fill 0 0 0 0 4 0 minecraft:air replace minecraft:oak_log",
			"fill 1 5 1 1 5 1 minecraft:air replace minecraft:oak_log"), cleared());
	}

	@Test
	void everyBlockIsLootedBeforeItIsCleared() {
		tree();
		breaker.lookAndStart(client);
		runToTheEnd();

		int lastLootOfTrunk = client.sent.indexOf("loot give @s mine 0 4 0 mainhand");
		int trunkFill = client.sent.indexOf("fill 0 0 0 0 4 0 minecraft:air replace minecraft:oak_log");
		assertTrue(lastLootOfTrunk >= 0 && lastLootOfTrunk < trunkFill, "önce /loot, sonra /fill: " + client.sent);
	}

	@Test
	void aPileOfLogsWithNoNaturalLeavesIsLeftAlone() {
		tree();
		client.naturalLeaves.clear();

		assertEquals(VeinBreaker.Outcome.NOT_A_TREE, breaker.lookAndStart(client));
		runToTheEnd();
		assertEquals(List.of(), client.sent);
	}

	@Test
	void leavesTwoBlocksAwayDoNotMakeATree() {
		tree();
		client.naturalLeaves.clear();
		client.naturalLeaves.add(new Pos(0, -2, 0));

		assertEquals(VeinBreaker.Outcome.NOT_A_TREE, breaker.lookAndStart(client));
	}

	@Test
	void withNaturalTreesOnlyOffTheBarePileGoes() {
		tree();
		client.naturalLeaves.clear();
		settings.naturalTreesOnly = false;

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
		runToTheEnd();
		assertEquals(6, looted().size());
	}

	@Test
	void leavesPastTheBlockLimitStillMakeItATree() {
		for (int y = 0; y < 10; y++) {
			client.put(new Pos(0, y, 0), OAK);
		}

		client.naturalLeaves.add(new Pos(0, 10, 0));
		client.target = new Pos(0, 0, 0);
		settings.maxBlocks = 3;

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
		runToTheEnd();
		assertEquals(List.of(
			"loot give @s mine 0 0 0 mainhand", "loot give @s mine 0 1 0 mainhand", "loot give @s mine 0 2 0 mainhand"), looted());
	}

	@Test
	void anotherKindOfLogIsNotFollowed() {
		tree();
		client.put(new Pos(1, 1, 0), BIRCH);

		breaker.lookAndStart(client);
		runToTheEnd();
		assertFalse(client.sent.stream().anyMatch(command -> command.contains("mine 1 1 0")), "huş kütüğü gitmemeliydi: " + client.sent);
	}

	@Test
	void anOreAndItsDeepslateTwinAreOneVein() {
		client.put(new Pos(0, 0, 0), IRON).put(new Pos(1, -1, 1), DEEP_IRON).put(new Pos(2, -2, 2), DEEP_IRON);
		client.put(new Pos(-1, 0, 0), COAL);
		client.target = new Pos(0, 0, 0);

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
		runToTheEnd();
		assertEquals(Set.of("loot give @s mine 0 0 0 mainhand", "loot give @s mine 1 -1 1 mainhand", "loot give @s mine 2 -2 2 mainhand"),
			Set.copyOf(looted()));
	}

	@Test
	void oresAreNotAskedToBeATree() {
		client.put(new Pos(0, 0, 0), IRON);
		client.target = new Pos(0, 0, 0);

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
	}

	@Test
	void theLimitTakesTheNearestFirst() {
		for (int y = 0; y < 10; y++) {
			client.put(new Pos(0, y, 0), IRON);
		}

		client.target = new Pos(0, 5, 0);
		settings.maxBlocks = 3;
		breaker.lookAndStart(client);
		runToTheEnd();

		assertEquals(Set.of("loot give @s mine 0 4 0 mainhand", "loot give @s mine 0 5 0 mainhand", "loot give @s mine 0 6 0 mainhand"),
			Set.copyOf(looted()));
	}

	@Test
	void aBlockNotInTheListStartsNothing() {
		client.put(new Pos(0, 0, 0), "minecraft:stone");
		client.target = new Pos(0, 0, 0);

		assertEquals(VeinBreaker.Outcome.NOT_LISTED, breaker.lookAndStart(client));
	}

	@Test
	void lookingAtNothingStartsNothing() {
		assertEquals(VeinBreaker.Outcome.NO_TARGET, breaker.lookAndStart(client));
	}

	@Test
	void withoutSetblockNothingIsSentAndTheWarningComesOnce() {
		tree();
		client.commands.remove("setblock");

		assertEquals(VeinBreaker.Outcome.NO_PERMISSION, breaker.lookAndStart(client));
		assertEquals(VeinBreaker.Outcome.NO_PERMISSION, breaker.lookAndStart(client));
		runToTheEnd();

		assertEquals(List.of(), client.sent);
		assertEquals(List.of(Lang.of(Keys.VEIN_NO_PERMISSION)), client.said);
	}

	@Test
	void withoutLootTheBlocksAreDestroyedAColumnAtATime() {
		tree();
		client.commands.remove("loot");

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
		runToTheEnd();

		assertEquals(List.of(
			"fill 0 0 0 0 4 0 minecraft:air destroy",
			"setblock 1 5 1 minecraft:air destroy"), client.sent);
		assertEquals(List.of(), client.said, "/loot yoksa uyarı yok; /setblock yeter");
	}

	@Test
	void withoutLootOrFillEveryBlockIsDestroyedWithSetblock() {
		tree();
		client.commands.removeAll(Set.of("loot", "fill"));

		breaker.lookAndStart(client);
		runToTheEnd();

		assertEquals(6, client.sent.size());
		assertTrue(client.sent.stream().allMatch(command -> command.startsWith("setblock ") && command.endsWith(" minecraft:air destroy")), client.sent.toString());
	}

	@Test
	void withoutLootSilkTouchHandsTheBlocksOverThemselves() {
		tree();
		client.commands.remove("loot");
		client.silkTouch = true;

		breaker.lookAndStart(client);
		runToTheEnd();

		assertEquals(List.of(
			"give @s minecraft:oak_log 5",
			"fill 0 0 0 0 4 0 minecraft:air replace minecraft:oak_log",
			"give @s minecraft:oak_log 1",
			"fill 1 5 1 1 5 1 minecraft:air replace minecraft:oak_log"), client.sent);
	}

	/** Three coal ores in a column and one off it, a Fortune III pickaxe, no /loot. */
	private void coalWithFortuneAndNoLoot() {
		client.put(new Pos(0, 0, 0), COAL).put(new Pos(0, 1, 0), COAL).put(new Pos(0, 2, 0), COAL).put(new Pos(1, 3, 1), COAL);
		client.target = new Pos(0, 0, 0);
		client.commands.remove("loot");
		client.commands.add("xp");
		client.fortune = 3;
	}

	@Test
	void withoutLootFortuneIsRolledHereAndGiven() {
		coalWithFortuneAndNoLoot();

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
		runToTheEnd();

		List<String> gives = client.sent.stream().filter(command -> command.startsWith("give ")).toList();
		assertEquals(2, gives.size(), client.sent.toString());
		assertTrue(gives.getFirst().startsWith("give @s minecraft:coal "), gives.toString());

		int column = Integer.parseInt(gives.getFirst().substring("give @s minecraft:coal ".length()));
		int single = Integer.parseInt(gives.get(1).substring("give @s minecraft:coal ".length()));
		assertTrue(column >= 3 && column <= 12, "üç cevherde 3–12 kömür: " + column);
		assertTrue(single >= 1 && single <= 4, "bir cevherde 1–4 kömür: " + single);

		assertTrue(client.sent.contains("fill 0 0 0 0 2 0 minecraft:air replace minecraft:coal_ore"), client.sent.toString());
		assertTrue(client.sent.stream().noneMatch(command -> command.contains("destroy") || command.startsWith("loot ")), client.sent.toString());
		assertTrue(client.sent.getLast().matches("xp add @s \\d+ points"), "XP en sonda tek komutla: " + client.sent);
	}

	@Test
	void withoutXpTheExperienceIsLeftOut() {
		coalWithFortuneAndNoLoot();
		client.commands.remove("xp");

		breaker.lookAndStart(client);
		runToTheEnd();

		assertTrue(client.sent.stream().noneMatch(command -> command.startsWith("xp ")), client.sent.toString());
	}

	@Test
	void withLootFortuneIsLeftToLoot() {
		coalWithFortuneAndNoLoot();
		client.commands.add("loot");

		breaker.lookAndStart(client);
		runToTheEnd();

		assertEquals(4, looted().size());
		assertTrue(client.sent.stream().noneMatch(command -> command.startsWith("give ") || command.startsWith("xp ")), client.sent.toString());
	}

	@Test
	void silkTouchWinsOverFortune() {
		coalWithFortuneAndNoLoot();
		client.silkTouch = true;

		breaker.lookAndStart(client);
		runToTheEnd();

		assertTrue(client.sent.contains("give @s minecraft:coal_ore 3"), client.sent.toString());
	}

	@Test
	void fortuneOnLogsIsLeftToTheServer() {
		tree();
		client.commands.remove("loot");
		client.fortune = 3;

		breaker.lookAndStart(client);
		runToTheEnd();

		assertTrue(client.sent.stream().allMatch(command -> command.endsWith(" destroy")), client.sent.toString());
	}

	@Test
	void withoutFortuneOresAreDestroyedByTheServer() {
		coalWithFortuneAndNoLoot();
		client.fortune = 0;

		breaker.lookAndStart(client);
		runToTheEnd();

		assertTrue(client.sent.stream().allMatch(command -> command.endsWith(" destroy")), client.sent.toString());
	}

	@Test
	void xpRepliesAreHiddenToo() {
		tree();
		breaker.lookAndStart(client);
		breaker.tick(client, true);

		assertTrue(breaker.hides("commands.experience.add.points.success.single"));
	}

	@Test
	void withLootSilkTouchIsLeftToLoot() {
		tree();
		client.silkTouch = true;

		breaker.lookAndStart(client);
		runToTheEnd();

		assertTrue(client.sent.stream().noneMatch(command -> command.startsWith("give ")), client.sent.toString());
		assertEquals(6, looted().size());
	}

	@Test
	void aRunWithAChangedBlockIsDestroyedBlockByBlock() {
		tree();
		client.commands.remove("loot");
		settings.commandsPerSecond = 1;
		breaker.lookAndStart(client);
		client.put(new Pos(0, 3, 0), "minecraft:stone");
		runToTheEnd();

		assertEquals(List.of(
			"setblock 0 0 0 minecraft:air destroy", "setblock 0 1 0 minecraft:air destroy", "setblock 0 2 0 minecraft:air destroy",
			"setblock 0 4 0 minecraft:air destroy",
			"setblock 1 5 1 minecraft:air destroy"), client.sent, "taşa dönen blok yok edilmemeli");
	}

	@Test
	void aBrokenBlockWithTheKeyHeldTakesTheRestButNotItself() {
		tree();
		settings.trigger = Trigger.HOLD_AND_BREAK;
		client.triggerHeld = true;
		Pos broken = new Pos(0, 1, 0);
		client.blocks.remove(broken);

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.broken(client, new VeinBreaker.Origin(broken, OAK, true, true)));
		runToTheEnd();

		assertEquals(5, looted().size(), client.sent.toString());
		assertFalse(client.sent.contains("loot give @s mine 0 1 0 mainhand"), "kırılan blok tekrar gönderilmez");
		assertTrue(client.sent.contains("loot give @s mine 0 0 0 mainhand"), "kırılanın altındaki kütük de gider: " + client.sent);
	}

	@Test
	void aBrokenBlockWithoutTheKeyDoesNothing() {
		tree();
		settings.trigger = Trigger.HOLD_AND_BREAK;
		client.blocks.remove(new Pos(0, 1, 0));

		assertEquals(VeinBreaker.Outcome.NOT_TRIGGERED, breaker.broken(client, new VeinBreaker.Origin(new Pos(0, 1, 0), OAK, true, true)));
		runToTheEnd();
		assertEquals(List.of(), client.sent);
	}

	@Test
	void inLookAndPressModeABreakDoesNothing() {
		tree();
		client.triggerHeld = true;

		assertEquals(VeinBreaker.Outcome.NOT_TRIGGERED, breaker.broken(client, new VeinBreaker.Origin(new Pos(0, 1, 0), OAK, true, true)));
	}

	@Test
	void aBrokenLogOfABarePileIsNotFollowed() {
		tree();
		client.naturalLeaves.clear();
		settings.trigger = Trigger.HOLD_AND_BREAK;
		client.triggerHeld = true;
		client.blocks.remove(new Pos(0, 1, 0));

		assertEquals(VeinBreaker.Outcome.NOT_A_TREE, breaker.broken(client, new VeinBreaker.Origin(new Pos(0, 1, 0), OAK, true, true)));
	}

	@Test
	void inLookAndPressModeTheKeyStartsOnItsDownstrokeOnly() {
		tree();
		settings.commandsPerSecond = 1;
		client.triggerHeld = true;

		breaker.tick(client, true);
		assertTrue(breaker.busy());
		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lastOutcome());

		runToTheEnd();
		int sent = client.sent.size();
		breaker.tick(client, true);
		assertEquals(sent, client.sent.size(), "basılı kalan tuş ikinci kez başlatmaz");
	}

	@Test
	void pressingAgainStopsHalfway() {
		tree();
		settings.commandsPerSecond = 1;
		client.triggerHeld = true;
		breaker.tick(client, true);
		client.triggerHeld = false;
		breaker.tick(client, true);
		client.triggerHeld = true;
		breaker.tick(client, true);

		assertFalse(breaker.busy());
		assertEquals(VeinBreaker.Outcome.STOPPED, breaker.lastOutcome());
		assertEquals(1, client.sent.size());
	}

	@Test
	void withTheModuleOffTheKeyDoesNothing() {
		tree();
		client.triggerHeld = true;

		breaker.tick(client, false);
		assertFalse(breaker.busy());
	}

	@Test
	void holdingTheKeyOutlinesWhatWouldGo() {
		tree();
		settings.trigger = Trigger.HOLD_AND_BREAK;

		breaker.tick(client, true);
		assertEquals(List.of(), client.outlined, "tuş basılı değilken çerçeve yok");

		client.triggerHeld = true;
		breaker.tick(client, true);
		assertEquals(6, client.outlined.size());
		assertEquals(Set.copyOf(client.blocks.keySet()), Set.copyOf(client.outlined));
	}

	@Test
	void inLookAndPressModeLookingIsEnoughToOutline() {
		tree();

		breaker.tick(client, true);
		assertEquals(6, client.outlined.size());
	}

	@Test
	void nothingIsOutlinedThatWouldNotGo() {
		tree();
		settings.trigger = Trigger.HOLD_AND_BREAK;
		client.triggerHeld = true;

		client.naturalLeaves.clear();
		breaker.tick(client, true);
		assertEquals(List.of(), client.outlined, "doğal ağaç değil");

		tree();
		client.commands.remove("setblock");

		for (int i = 0; i < VeinBreaker.OUTLINE_REFRESH_TICKS; i++) {
			breaker.tick(client, true);
		}

		assertEquals(List.of(), client.outlined, "/setblock yok");

		client.commands.add("setblock");
		client.target = null;
		breaker.tick(client, true);
		assertEquals(List.of(), client.outlined, "bir bloğa bakmıyor");
	}

	@Test
	void theOutlineIsOffWithTheSettingOffOrTheModuleOff() {
		tree();
		settings.showOutline = false;
		breaker.tick(client, true);
		assertEquals(List.of(), client.outlined);

		settings.showOutline = true;
		breaker.tick(client, false);
		assertEquals(List.of(), client.outlined);
	}

	@Test
	void theOutlineFollowsTheBlocksWithinAFewTicks() {
		tree();
		breaker.tick(client, true);
		assertEquals(6, client.outlined.size());

		client.blocks.remove(new Pos(1, 5, 1));

		for (int i = 0; i < VeinBreaker.OUTLINE_REFRESH_TICKS; i++) {
			breaker.tick(client, true);
		}

		assertEquals(5, client.outlined.size());
	}

	@Test
	void giveRepliesAreHiddenToo() {
		tree();
		breaker.lookAndStart(client);
		breaker.tick(client, true);

		assertTrue(breaker.hides("commands.give.success.single"));
	}

	@Test
	void aNewConnectionGetsItsOwnWarning() {
		tree();
		client.commands.clear();

		breaker.lookAndStart(client);
		breaker.forgetWarning();
		breaker.lookAndStart(client);

		assertEquals(2, client.said.size());
	}

	@Test
	void withoutFillEveryBlockIsClearedWithSetblock() {
		tree();
		client.commands.remove("fill");

		breaker.lookAndStart(client);
		runToTheEnd();

		assertEquals(6, cleared().size());
		assertTrue(cleared().stream().allMatch(command -> command.startsWith("setblock ") && command.endsWith(" minecraft:air")), cleared().toString());
	}

	@Test
	void theOtherDropsSettingSpawnsTheLootWhereTheBlockIs() {
		client.put(new Pos(-3, 10, 4), IRON);
		client.target = new Pos(-3, 10, 4);
		settings.drops = Drops.AT_BLOCK;

		breaker.lookAndStart(client);
		runToTheEnd();

		assertEquals(List.of("loot spawn -2.5 10.5 4.5 mine -3 10 4 mainhand"), looted());
	}

	@Test
	void aBlockThatChangedBeforeItsTurnIsNotLooted() {
		tree();
		settings.commandsPerSecond = 1;
		breaker.lookAndStart(client);
		breaker.tick(client, true);
		client.put(new Pos(0, 4, 0), "minecraft:stone");
		runToTheEnd();

		assertFalse(client.sent.contains("loot give @s mine 0 4 0 mainhand"), client.sent.toString());
	}

	@Test
	void theRateIsKeptInEverySecond() {
		tree();
		settings.commandsPerSecond = 3;
		breaker.lookAndStart(client);

		List<Integer> perTick = new ArrayList<>();

		for (int i = 0; i < 200 && breaker.busy(); i++) {
			int before = client.sent.size();
			breaker.tick(client, true);
			perTick.add(client.sent.size() - before);
		}

		assertEquals(8, client.sent.size(), "6 /loot + 2 /fill");

		for (int start = 0; start < perTick.size(); start++) {
			int inSecond = perTick.subList(start, Math.min(start + 20, perTick.size())).stream().mapToInt(Integer::intValue).sum();
			assertTrue(inSecond <= 3, "bir saniyede " + inSecond + " komut: " + perTick);
		}
	}

	@Test
	void cancelStopsWhatIsStillWaiting() {
		tree();
		settings.commandsPerSecond = 1;
		breaker.lookAndStart(client);
		breaker.tick(client, true);
		breaker.cancel();
		runToTheEnd();

		assertEquals(1, client.sent.size());
	}

	@Test
	void notReadyStopsTheJob() {
		tree();
		breaker.lookAndStart(client);
		client.ready = false;

		assertFalse(breaker.tick(client, true));
		assertEquals(List.of(), client.sent);
	}

	@Test
	void successRepliesAreHiddenWhileWorkingAndShortlyAfter() {
		tree();
		breaker.lookAndStart(client);
		breaker.tick(client, true);

		assertTrue(breaker.hides("commands.fill.success"));
		assertTrue(breaker.hides("commands.setblock.success"));
		assertTrue(breaker.hides("commands.drop.success.single"));
		assertTrue(breaker.hides("commands.drop.success.multiple_with_table"));
		assertFalse(breaker.hides("commands.fill.failed"), "hata mesajı gizlenmez");
		assertFalse(breaker.hides("chat.type.text"));

		runToTheEnd();
		assertTrue(breaker.hides("commands.fill.success"), "yanıtlar geç gelir; iş bitince de bir süre gizlenir");

		for (int i = 0; i < VeinBreaker.QUIET_TICKS_AFTER + 1; i++) {
			breaker.tick(client, true);
		}

		assertFalse(breaker.hides("commands.fill.success"), "iş bittikten sonra kendi komutların yine görünür");
	}

	@Test
	void nothingIsHiddenWithTheSettingOff() {
		tree();
		settings.hideFeedback = false;
		breaker.lookAndStart(client);
		breaker.tick(client, true);

		assertFalse(breaker.hides("commands.fill.success"));
	}

	@Test
	void nothingIsHiddenBeforeAnyJob() {
		assertFalse(breaker.hides("commands.setblock.success"));
	}

	private void runToTheEnd() {
		for (int i = 0; i < 10_000 && breaker.tick(client, true); i++) {
			// ticking
		}
	}

	private List<String> looted() {
		return client.sent.stream().filter(command -> command.startsWith("loot ")).toList();
	}

	private List<String> cleared() {
		return client.sent.stream().filter(command -> !command.startsWith("loot ")).toList();
	}
}
