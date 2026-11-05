package com.merci.survivalkit.vein;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.area.Area;
import com.merci.survivalkit.area.WithoutArea;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.merci.survivalkit.text.TestLang;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Damar Kırıcı's two limits: Yapı Modu's area, and the hunger a job costs. */
class VeinBreakerLimitsTest {
	private static final String OAK = "minecraft:oak_log";
	private static final String IRON = "minecraft:iron_ore";

	private FakeVeinSettings settings;
	private FakeVeinContext client;
	private VeinBreaker breaker;

	@BeforeEach
	void setUp() {
		TestLang.install("tr_tr");
		settings = new FakeVeinSettings();
		client = new FakeVeinContext();
		client.commands.add("effect");
		breaker = new VeinBreaker(settings, new Random(42));
	}

	/** An eight-log trunk at x=0 z=0 from y=0, natural leaves on top; looking at its foot. */
	private void tree() {
		for (int y = 0; y < 8; y++) {
			client.put(new Pos(0, y, 0), OAK);
		}

		client.naturalLeaves.add(new Pos(0, 8, 0));
		client.target = new Pos(0, 0, 0);
	}

	/** The lower half of the trunk, y 0 to 3. */
	private static Area lowerHalf() {
		return Area.between(new Pos(-2, 0, -2), new Pos(2, 3, 2));
	}

	private List<Integer> lootedHeights() {
		return client.sent.stream()
			.filter(command -> command.startsWith("loot "))
			.map(command -> Integer.parseInt(command.split(" ")[5]))
			.sorted()
			.toList();
	}

	private void runToTheEnd() {
		for (int i = 0; i < 10_000 && (breaker.tick(client, true) || breaker.busy()); i++) {
			// ticking
		}
	}

	@Test
	void withAnAreaOnlyTheLogsInsideGo() {
		tree();
		client.area = lowerHalf();

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
		runToTheEnd();

		assertEquals(List.of(0, 1, 2, 3), lootedHeights());
		assertTrue(client.sent.contains("fill 0 0 0 0 3 0 minecraft:air replace minecraft:oak_log"), client.sent.toString());
	}

	@Test
	void negativeControlWithoutAnAreaTheWholeTreeGoes() {
		tree();

		breaker.lookAndStart(client);
		runToTheEnd();

		assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), lootedHeights());
	}

	@Test
	void theLeavesOutsideTheAreaStillMakeItATree() {
		tree();
		// Only the bottom two logs are in it, far from the leaves at y=8.
		client.area = Area.between(new Pos(0, 0, 0), new Pos(0, 1, 0));

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
		runToTheEnd();
		assertEquals(List.of(0, 1), lootedHeights());
	}

	@Test
	void theLimitCountsOnlyWhatIsInside() {
		// A long vein of ore along x; the area is its far end, past the limit from the start.
		for (int x = 0; x < 20; x++) {
			client.put(new Pos(x, 0, 0), IRON);
		}

		client.target = new Pos(0, 0, 0);
		settings.maxBlocks = 5;
		client.area = Area.between(new Pos(10, 0, 0), new Pos(19, 0, 0));

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
		runToTheEnd();

		assertEquals(5, client.sent.stream().filter(command -> command.startsWith("loot ")).count(), client.sent.toString());
		assertTrue(client.sent.stream().filter(command -> command.startsWith("loot ")).allMatch(command -> Integer.parseInt(command.split(" ")[4]) >= 10),
			client.sent.toString());
	}

	@Test
	void aTreeWhollyOutsideTheAreaIsLeftAndSaysWhy() {
		tree();
		client.area = Area.between(new Pos(10, 0, 10), new Pos(12, 5, 12));

		assertEquals(VeinBreaker.Outcome.OUTSIDE_AREA, breaker.lookAndStart(client));
		runToTheEnd();

		assertEquals(List.of(), client.sent);
		assertEquals(List.of(Lang.of(Keys.VEIN_OUTSIDE_AREA)), client.said);
	}

	@Test
	void holdAndBreakOutsideTheAreaTakesOnlyTheInside() {
		tree();
		settings.trigger = Trigger.HOLD_AND_BREAK;
		client.triggerHeld = true;
		Pos broken = new Pos(0, 5, 0);
		client.blocks.remove(broken);
		client.area = lowerHalf();

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.broken(client, new VeinBreaker.Origin(broken, OAK, true, true)));
		runToTheEnd();

		assertEquals(List.of(0, 1, 2, 3), lootedHeights());
	}

	@Test
	void withNoAreaAndNowhereNothingGoes() {
		tree();
		settings.withoutArea = WithoutArea.NOWHERE;

		assertEquals(VeinBreaker.Outcome.NO_AREA, breaker.lookAndStart(client));
		runToTheEnd();

		assertEquals(List.of(), client.sent);
		assertEquals(List.of(Lang.of(Keys.VEIN_NO_AREA)), client.said);
	}

	@Test
	void nowhereStillWorksInsideAnArea() {
		tree();
		settings.withoutArea = WithoutArea.NOWHERE;
		client.area = lowerHalf();

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
	}

	@Test
	void theOutlineShowsOnlyWhatIsInside() {
		tree();
		client.area = lowerHalf();

		breaker.tick(client, true);

		assertEquals(4, client.outlined.size(), client.outlined.toString());
		assertTrue(client.outlined.stream().allMatch(pos -> pos.y() <= 3));
	}

	@Test
	void theHungerIsChargedFirstWithTheEffect() {
		tree();
		settings.blocksPerHungerPoint = 4;

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
		runToTheEnd();

		assertEquals(HungerCost.charge(2).command(), client.sent.getFirst());
		assertEquals(1, client.sent.stream().filter(command -> command.startsWith("effect ")).count());
	}

	@Test
	void theCostIsForWhatIsInsideTheArea() {
		tree();
		settings.blocksPerHungerPoint = 4;
		client.area = lowerHalf();

		breaker.lookAndStart(client);
		runToTheEnd();

		assertEquals(HungerCost.charge(1).command(), client.sent.getFirst());
	}

	@Test
	void aJobUnderAWholePointSendsNoEffectAndCarriesTheBlocks() {
		tree();
		settings.blocksPerHungerPoint = 10;

		breaker.lookAndStart(client);
		runToTheEnd();
		assertFalse(client.sent.stream().anyMatch(command -> command.startsWith("effect ")), client.sent.toString());

		tree();
		client.sent.clear();
		breaker.lookAndStart(client);
		runToTheEnd();
		assertEquals(HungerCost.charge(1).command(), client.sent.getFirst(), "8 + 8 = 16: bir puan");
	}

	@Test
	void tooHungryNothingGoesAndItSaysWhy() {
		tree();
		settings.blocksPerHungerPoint = 4;
		client.food = 8;
		client.saturation = 0;

		assertEquals(VeinBreaker.Outcome.TOO_HUNGRY, breaker.lookAndStart(client));
		runToTheEnd();

		assertEquals(List.of(), client.sent);
		assertEquals(List.of(Lang.of(Keys.VEIN_TOO_HUNGRY, 2)), client.said);
	}

	@Test
	void negativeControlOnePointMoreOfFoodAndItGoes() {
		tree();
		settings.blocksPerHungerPoint = 4;
		client.food = 9;
		client.saturation = 0;

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
	}

	@Test
	void withoutTheEffectCommandNothingGoesAndTheWarningComesOnce() {
		tree();
		settings.blocksPerHungerPoint = 10;
		client.commands.remove("effect");

		assertEquals(VeinBreaker.Outcome.NO_PERMISSION, breaker.lookAndStart(client));
		assertEquals(VeinBreaker.Outcome.NO_PERMISSION, breaker.lookAndStart(client));
		runToTheEnd();

		assertEquals(List.of(), client.sent);
		assertEquals(List.of(Lang.of(Keys.VEIN_NO_EFFECT_PERMISSION)), client.said);
	}

	@Test
	void freeItNeedsNoEffectCommand() {
		tree();
		client.commands.remove("effect");

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
	}

	@Test
	void theNextJobWaitsForTheHungerToDrain() {
		tree();
		settings.blocksPerHungerPoint = 4;
		breaker.lookAndStart(client);

		while (breaker.tick(client, true)) {
			// sending
		}

		assertTrue(breaker.busy(), "açlık etkisi bitene kadar meşgul");
		tree();
		assertEquals(VeinBreaker.Outcome.BUSY, breaker.lookAndStart(client));

		runToTheEnd();
		assertFalse(breaker.busy());
	}

	@Test
	void theEffectsReplyIsHiddenLikeTheOthers() {
		tree();
		settings.blocksPerHungerPoint = 4;
		breaker.lookAndStart(client);
		breaker.tick(client, true);

		assertTrue(breaker.hides("commands.effect.give.success.single"));
	}

	@Test
	void switchingOffForgetsTheCarry() {
		tree();
		settings.blocksPerHungerPoint = 10;
		breaker.lookAndStart(client);
		runToTheEnd();
		breaker.forgetHunger();

		tree();
		client.sent.clear();
		breaker.lookAndStart(client);
		runToTheEnd();

		assertFalse(client.sent.stream().anyMatch(command -> command.startsWith("effect ")), client.sent.toString());
	}

	/** An iron axe in hotbar slot 3 with this much left. */
	private void axeWithLeft(int left) {
		settings.toolWear = true;
		client.commands.add("item");
		client.tool = new HeldTool(3, 250 - left, 250, 1, 0);
	}

	@Test
	void theToolWearsABlockAPointAndThatGoesFirst() {
		tree();
		axeWithLeft(100);

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
		runToTheEnd();

		assertEquals(ToolWear.command(client.tool, 8), client.sent.getFirst());
	}

	@Test
	void theWearIsForWhatIsInsideTheArea() {
		tree();
		axeWithLeft(100);
		client.area = lowerHalf();

		breaker.lookAndStart(client);
		runToTheEnd();

		assertEquals(ToolWear.command(client.tool, 4), client.sent.getFirst());
	}

	@Test
	void aToolThatWouldBreakStartsNothingAndSaysWhy() {
		tree();
		axeWithLeft(8);

		assertEquals(VeinBreaker.Outcome.TOOL_TOO_WORN, breaker.lookAndStart(client));
		runToTheEnd();

		assertEquals(List.of(), client.sent);
		assertEquals(List.of(Lang.of(Keys.VEIN_TOOL_TOO_WORN, 8, 8)), client.said);
	}

	@Test
	void negativeControlOnePointMoreAndItGoesDownToOne() {
		tree();
		axeWithLeft(9);

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
		runToTheEnd();
		assertEquals(ToolWear.command(client.tool, 8), client.sent.getFirst());
	}

	@Test
	void aRefusedToolCostsNoHungerEither() {
		tree();
		axeWithLeft(8);
		settings.blocksPerHungerPoint = 4;

		breaker.lookAndStart(client);
		runToTheEnd();

		tree();
		client.tool = new HeldTool(3, 0, 250, 1, 0);
		breaker.lookAndStart(client);
		runToTheEnd();
		assertEquals(HungerCost.charge(2).command(), client.sent.get(1), "reddedilen iş taşınmamalı: " + client.sent);
	}

	@Test
	void withoutTheItemCommandNothingGoesAndTheWarningComesOnce() {
		tree();
		axeWithLeft(100);
		client.commands.remove("item");

		assertEquals(VeinBreaker.Outcome.NO_PERMISSION, breaker.lookAndStart(client));
		assertEquals(VeinBreaker.Outcome.NO_PERMISSION, breaker.lookAndStart(client));
		runToTheEnd();

		assertEquals(List.of(), client.sent);
		assertEquals(List.of(Lang.of(Keys.VEIN_NO_ITEM_PERMISSION)), client.said);
	}

	@Test
	void withNoToolThatWearsNothingIsAskedOrSent() {
		tree();
		settings.toolWear = true;
		client.tool = null;

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
		runToTheEnd();
		assertFalse(client.sent.stream().anyMatch(command -> command.startsWith("item ")), client.sent.toString());
	}

	@Test
	void withWearOffTheToolIsLeftAlone() {
		tree();
		axeWithLeft(1);
		settings.toolWear = false;

		assertEquals(VeinBreaker.Outcome.STARTED, breaker.lookAndStart(client));
		runToTheEnd();
		assertFalse(client.sent.stream().anyMatch(command -> command.startsWith("item ")), client.sent.toString());
	}

	@Test
	void theItemCommandsReplyIsHidden() {
		tree();
		axeWithLeft(100);
		breaker.lookAndStart(client);
		breaker.tick(client, true);

		assertTrue(breaker.hides("commands.item.entity.set.success.single"));
	}

	/**
	 * Breaking by hand sets it off: the server is about to wear the tool a point for that block on its
	 * own. Eight logs, one broken by hand, seven by command: with 8 left the tool would end at 0, so
	 * nothing goes; with 9 it does.
	 */
	@Test
	void theBlockBrokenByHandIsCountedAgainstTheTool() {
		tree();
		settings.trigger = Trigger.HOLD_AND_BREAK;
		client.triggerHeld = true;
		Pos broken = new Pos(0, 0, 0);
		client.blocks.remove(broken);
		axeWithLeft(8);

		assertEquals(VeinBreaker.Outcome.TOOL_TOO_WORN, breaker.broken(client, new VeinBreaker.Origin(broken, OAK, true, true)));
		assertEquals(List.of(Lang.of(Keys.VEIN_TOOL_TOO_WORN, 7, 7)), client.said);

		axeWithLeft(9);
		assertEquals(VeinBreaker.Outcome.STARTED, breaker.broken(client, new VeinBreaker.Origin(broken, OAK, true, true)));
	}
}
