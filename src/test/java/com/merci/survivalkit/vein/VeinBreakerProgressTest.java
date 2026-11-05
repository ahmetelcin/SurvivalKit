package com.merci.survivalkit.vein;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** How far a job has got, for the HUD: in percent of its commands, rising to the end, nothing between jobs. */
class VeinBreakerProgressTest {
	private static final String OAK = "minecraft:oak_log";

	private final FakeVeinSettings settings = new FakeVeinSettings();
	private final FakeVeinContext client = new FakeVeinContext();
	private final VeinBreaker breaker = new VeinBreaker(settings, new Random(42));

	/** A five-log trunk and a branch, with leaves: six logs. */
	private void tree() {
		for (int y = 0; y < 5; y++) {
			client.put(new Pos(0, y, 0), OAK);
		}

		client.put(new Pos(1, 5, 1), OAK);
		client.naturalLeaves.add(new Pos(0, 5, 0));
		client.target = new Pos(0, 1, 0);
	}

	/**
	 * Without /loot the trunk goes as one /fill; a log changed before it is sent splits it into one
	 * command a block, and the job grows by that much: the first command sent already counts.
	 */
	@Test
	void aJobThatSplitsCountsTheCommandsItSplitInto() {
		settings.commandsPerSecond = 20;
		tree();
		client.commands.remove("loot");
		breaker.lookAndStart(client);
		client.put(new Pos(0, 2, 0), "minecraft:stone");

		breaker.tick(client, true);

		assertTrue(client.sent.getFirst().startsWith("setblock "), "/fill bölünmeliydi: " + client.sent);
		assertTrue(breaker.progress().orElseThrow() > 0, "ilk komut gidince ilerleme 0'ın üstünde: " + breaker.progress());
	}

	@Test
	void beforeAnyJobThereIsNone() {
		assertEquals(OptionalInt.empty(), breaker.progress());
	}

	@Test
	void aJobRisesFromNothingAndIsGoneWhenDone() {
		settings.commandsPerSecond = 20;
		tree();
		breaker.lookAndStart(client);

		assertEquals(OptionalInt.of(0), breaker.progress());
		assertEquals(6, breaker.jobBlocks());

		List<Integer> seen = new ArrayList<>();

		while (breaker.tick(client, true)) {
			breaker.progress().ifPresent(seen::add);
		}

		assertEquals(OptionalInt.empty(), breaker.progress());
		assertTrue(seen.size() >= 2, "iş birkaç tick sürmeliydi: " + seen);

		for (int i = 1; i < seen.size(); i++) {
			assertTrue(seen.get(i) >= seen.get(i - 1), "ilerleme hiç geri gitmemeli: " + seen);
		}

		assertTrue(seen.getFirst() > 0 && seen.getLast() < 100, seen.toString());
	}
}
