package com.merci.survivalkit.vein;

import com.merci.survivalkit.area.Area;
import com.merci.survivalkit.area.AreaRule;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Damar Kırıcı's rules. Nothing here breaks a block: the server does, through the commands it
 * has given the player — /loot, /give, /fill, /setblock, whichever it allows. Without /setblock
 * nothing is sent at all.
 *
 * <p>A job starts one of two ways ({@link Trigger}): the player breaks a block the usual way with
 * the trigger key held ({@link #broken}), or presses the trigger key while looking at one. Either
 * way the vein or tree is found, the commands are lined up, and {@link #tick} sends them, never
 * faster than the setting allows.
 *
 * <p>Three rules sit between finding and sending. With a Yapı Modu area in force only the blocks
 * inside it go ({@link AreaRule}); a job costs hunger ({@link HungerCost}), charged with the
 * server's /effect; and the tool in hand wears as it would by hand ({@link ToolWear}), set with
 * /item. Both are checked before anything is sent: a job the bar or the tool cannot pay for does
 * not start at all.
 */
public final class VeinBreaker {
	public enum Outcome {
		/** No world, no player. */
		NOT_READY,
		/** Not this trigger: a block broken without the key held, or in the other mode. */
		NOT_TRIGGERED,
		/** The crosshair is not on a block. */
		NO_TARGET,
		/** The block is not in the list. */
		NOT_LISTED,
		/** The server does not let the player run /setblock. */
		NO_PERMISSION,
		/** A pile of logs with no natural leaves on it: somebody built it. */
		NOT_A_TREE,
		/** No Yapı Modu area in force, and the setting says not to work without one. */
		NO_AREA,
		/** Everything the job would take is outside Yapı Modu's area. */
		OUTSIDE_AREA,
		/** The hunger cost would leave the player hungry. */
		TOO_HUNGRY,
		/** The tool in hand would be left with nothing: it must not break. */
		TOOL_TOO_WORN,
		/** A job is still running, or the hunger it charged is still being drained. */
		BUSY,
		/** A job was running and the trigger stopped it. */
		STOPPED,
		STARTED
	}

	/**
	 * The block a job starts from.
	 *
	 * @param broken the player has already broken it, so it only shows the way: its own drops
	 *               came the usual way, and it is not sent again
	 */
	public record Origin(Pos pos, String id, boolean log, boolean broken) {
	}

	/**
	 * How far a pile of logs is followed to look for leaves. Further than the block limit: a big
	 * jungle tree is more than 128 logs, and its leaves are at the top, past the ones that go.
	 */
	static final int TREE_SCAN_LIMIT = 4096;

	/** The server's replies trail the commands by a round trip; they are hidden this long after the last one. */
	static final int QUIET_TICKS_AFTER = 60;

	/** The outline follows the crosshair at once, and is looked over again this often in case the blocks changed. */
	static final int OUTLINE_REFRESH_TICKS = 10;

	private static final int TICKS_PER_SECOND = 20;

	/** The effect starts a round trip after the command goes; a second's grace before the next job. */
	static final int HUNGER_SETTLE_TICKS = 20;

	private static final String SETBLOCK_SUCCESS = "commands.setblock.success";
	private static final String FILL_SUCCESS = "commands.fill.success";
	private static final String GIVE_SUCCESS = "commands.give.success.";
	private static final String XP_SUCCESS = "commands.experience.add.";
	/** /loot reports as "drop": single, multiple, and either with the table named. */
	private static final String LOOT_SUCCESS = "commands.drop.success.";
	/** The hunger charge's reply. */
	private static final String EFFECT_SUCCESS = "commands.effect.give.success.";
	/** The tool wear's reply. */
	private static final String ITEM_SUCCESS = "commands.item.entity.set.success.";

	private final VeinSettings settings;
	private final RandomGenerator random;
	private final CommandPacer pacer = new CommandPacer();
	private final ArrayDeque<CommandPlan.Step> queue = new ArrayDeque<>();
	private long tick;
	private long quietUntil = Long.MIN_VALUE;
	private boolean warned;
	private boolean warnedEffect;
	private boolean warnedItem;
	private final HungerCost hunger = new HungerCost();
	/** The tick the last hunger charge has been drained by; the next job waits for it. */
	private long hungerUntil = Long.MIN_VALUE;
	private boolean wasHeld;
	private Outcome lastOutcome;
	/** The job being sent: how many blocks it takes, and how many commands it came to all told. */
	private int jobBlocks;
	private int jobSteps;
	private List<Pos> outline = List.of();
	private Pos outlinedFor;
	private long outlinedAt;

	public VeinBreaker(VeinSettings settings) {
		this(settings, new Random());
	}

	/** @param random the dice for Fortune when there is no /loot; a seeded one in tests */
	public VeinBreaker(VeinSettings settings, RandomGenerator random) {
		this.settings = settings;
		this.random = random;
	}

	/**
	 * The player broke a block the usual way. With the trigger key held, in that mode, the rest
	 * of its vein or tree goes after it.
	 */
	public Outcome broken(VeinContext client, Origin origin) {
		if (settings.trigger() != Trigger.HOLD_AND_BREAK || !client.triggerHeld()) {
			return Outcome.NOT_TRIGGERED;
		}

		return remember(start(client, origin));
	}

	/** Starts from the block under the crosshair, that block included. */
	public Outcome lookAndStart(VeinContext client) {
		if (!client.ready()) {
			return remember(Outcome.NOT_READY);
		}

		Optional<Pos> target = client.target();

		if (target.isEmpty()) {
			return remember(Outcome.NO_TARGET);
		}

		Pos pos = target.get();
		return remember(start(client, new Origin(pos, client.blockAt(pos), client.isLog(pos), false)));
	}

	private Outcome start(VeinContext client, Origin origin) {
		if (busy()) {
			return Outcome.BUSY;
		}

		if (!client.ready()) {
			return Outcome.NOT_READY;
		}

		if (!settings.blocks().contains(origin.id())) {
			return Outcome.NOT_LISTED;
		}

		if (!client.canRun("setblock")) {
			// Once per connection: pressing again on the same server would only repeat it.
			if (!warned) {
				warned = true;
				client.say(Lang.of(Keys.VEIN_NO_PERMISSION));
			}

			return Outcome.NO_PERMISSION;
		}

		int perPoint = settings.blocksPerHungerPoint();

		if (perPoint > 0 && !client.canRun("effect")) {
			// Asked up front, not only when a job comes to a whole point: without /effect the
			// small jobs would all go free.
			if (!warnedEffect) {
				warnedEffect = true;
				client.say(Lang.of(Keys.VEIN_NO_EFFECT_PERMISSION));
			}

			return Outcome.NO_PERMISSION;
		}

		Optional<HeldTool> tool = settings.toolWear() ? client.heldTool() : Optional.empty();

		if (tool.isPresent() && !client.canRun("item")) {
			// Like /effect: without /item the tool would never wear, so nothing goes.
			if (!warnedItem) {
				warnedItem = true;
				client.say(Lang.of(Keys.VEIN_NO_ITEM_PERMISSION));
			}

			return Outcome.NO_PERMISSION;
		}

		Optional<Area> area = client.area();

		if (!AreaRule.worksAtAll(area, settings.withoutArea())) {
			client.say(Lang.of(Keys.VEIN_NO_AREA));
			return Outcome.NO_AREA;
		}

		Optional<Found> found = find(client, origin, area);

		if (found.isEmpty()) {
			return Outcome.NOT_A_TREE;
		}

		List<Pos> blocks = new ArrayList<>(found.get().blocks());

		if (origin.broken()) {
			// The player's own break: it has gone already. Not necessarily first any more — the
			// area may have kept it out.
			blocks.remove(origin.pos());
		}

		if (blocks.isEmpty() && found.get().cut()) {
			client.say(Lang.of(Keys.VEIN_OUTSIDE_AREA));
			return Outcome.OUTSIDE_AREA;
		}

		int points = hunger.points(blocks.size(), perPoint);

		if (points > 0 && !HungerCost.affordable(client.foodLevel(), client.saturation(), points)) {
			client.say(Lang.of(Keys.VEIN_TOO_HUNGRY, points));
			return Outcome.TOO_HUNGRY;
		}

		int fortune = client.fortuneLevel();
		CommandPlan.Route route = CommandPlan.Route.of(client.canRun("loot"), client.holdsSilkTouch(), fortune > 0 && OreDrops.of(origin.id()).isPresent());
		CommandPlan.Dice dice = new CommandPlan.Dice(fortune, random, client.canRun("xp"));
		List<CommandPlan.Step> plan = CommandPlan.of(blocks, client::blockAt, settings.drops(), route, client.canRun("fill"), dice);
		// Rolled after the plan, so Fortune's dice come out as they did before there was wear.
		int wear = tool.map(held -> ToolWear.roll(blocks.size(), held, random)).orElse(0);

		int pending = origin.broken() ? tool.map(HeldTool::damagePerBlock).orElse(0) : 0;

		if (tool.isPresent() && !ToolWear.affordable(tool.get(), wear, pending)) {
			client.say(Lang.of(Keys.VEIN_TOOL_TOO_WORN, wear, tool.get().left() - pending));
			return Outcome.TOOL_TOO_WORN;
		}

		hunger.pay(blocks.size(), perPoint);

		if (wear > 0) {
			// The very first command: it names the slot the tool was in, and the sooner it goes the
			// less chance the player has to put something else there.
			queue.add(CommandPlan.Step.unguarded(ToolWear.command(tool.get(), wear)));
		}

		if (points > 0) {
			// First, before any block: the price is paid for the job, not for whatever part of
			// it got sent before a stop.
			HungerCost.Charge charge = HungerCost.charge(points);
			queue.add(CommandPlan.Step.unguarded(charge.command()));
			hungerUntil = tick + charge.seconds() * TICKS_PER_SECOND + HUNGER_SETTLE_TICKS;
		}

		queue.addAll(plan);
		jobBlocks = blocks.size();
		jobSteps = queue.size();
		outline = List.of();
		return Outcome.STARTED;
	}

	/**
	 * What a trigger would take.
	 *
	 * @param blocks nearest first, inside the area if there is one, at most the limit
	 * @param cut    the area kept some of the vein or tree out
	 */
	private record Found(List<Pos> blocks, boolean cut) {
	}

	/**
	 * The vein or tree {@code origin} belongs to, nearest first, at most the limit, only what the
	 * area allows. The search itself crosses the area's edge: half a tree outside does not cut the
	 * other half off from the block the player started at. Empty when it is a pile of logs that is
	 * not a tree — judged by the whole pile, leaves outside the area included.
	 */
	private Optional<Found> find(VeinContext client, Origin origin, Optional<Area> area) {
		List<String> listed = settings.blocks();
		String family = VeinScan.family(origin.id());
		Predicate<Pos> member = pos -> {
			String other = client.blockAt(pos);
			return listed.contains(other) && VeinScan.family(other).equals(family);
		};

		int max = settings.maxBlocks();
		List<Pos> all;

		if (settings.naturalTreesOnly() && origin.log()) {
			all = VeinScan.connected(origin.pos(), member, Math.max(max, TREE_SCAN_LIMIT));

			if (!VeinScan.touchesFoliage(all, client::isNaturalFoliage)) {
				return Optional.empty();
			}
		} else {
			// With an area the limit counts what is inside it, so the search has to go further.
			all = VeinScan.connected(origin.pos(), member, area.isPresent() ? Math.max(max, TREE_SCAN_LIMIT) : max);
		}

		List<Pos> kept = AreaRule.keep(all, area, settings.withoutArea());
		return Optional.of(new Found(kept.subList(0, Math.min(max, kept.size())), kept.size() < all.size()));
	}

	/**
	 * One client tick: reads the trigger, keeps the outline up to date, sends what the rate allows.
	 *
	 * @param listening whether the module is on; off, it only finishes what it was sending
	 * @return whether there is still something to send
	 */
	public boolean tick(VeinContext client, boolean listening) {
		tick++;

		if (!client.ready()) {
			cancel();
			wasHeld = false;
			outline = List.of();
			return false;
		}

		boolean held = listening && client.triggerHeld();

		if (settings.trigger() == Trigger.LOOK_AND_PRESS && held && !wasHeld) {
			if (busy()) {
				cancel();
				remember(Outcome.STOPPED);
			} else {
				lookAndStart(client);
			}
		}

		wasHeld = held;
		refreshOutline(client, listening, held);
		client.outline(outline);

		while (!queue.isEmpty()) {
			CommandPlan.Step step = queue.peek();

			if (step.guarded() && !step.guard().stream().allMatch(pos -> client.blockAt(pos).equals(step.guardId()))) {
				jobSteps += step.otherwise().size() - 1;
				queue.poll();
				// Pushed to the front in order, so the block-by-block work goes where the whole went.
				for (int i = step.otherwise().size() - 1; i >= 0; i--) {
					queue.addFirst(step.otherwise().get(i));
				}

				continue;
			}

			if (!pacer.tryAcquire(tick, settings.commandsPerSecond())) {
				break;
			}

			queue.poll();
			client.send(step.command());
			quietUntil = tick + QUIET_TICKS_AFTER;
		}

		return !queue.isEmpty();
	}

	/**
	 * The outline shows what a trigger would take right now: while the key is held when breaking
	 * is the trigger, whenever the crosshair is on a block when pressing is. Nothing while a job
	 * runs, nothing that would not go (not listed, not a tree, no /setblock).
	 */
	private void refreshOutline(VeinContext client, boolean listening, boolean held) {
		boolean wanted = settings.showOutline() && listening && !busy()
			&& (settings.trigger() == Trigger.LOOK_AND_PRESS || held);
		Optional<Pos> target = wanted ? client.target() : Optional.empty();

		if (target.isEmpty()) {
			outline = List.of();
			outlinedFor = null;
			return;
		}

		Pos pos = target.get();

		if (pos.equals(outlinedFor) && tick - outlinedAt < OUTLINE_REFRESH_TICKS) {
			return;
		}

		outlinedFor = pos;
		outlinedAt = tick;
		String id = client.blockAt(pos);

		Optional<Area> area = client.area();

		if (!settings.blocks().contains(id) || !client.canRun("setblock")
			|| (settings.blocksPerHungerPoint() > 0 && !client.canRun("effect"))
			|| (settings.toolWear() && client.heldTool().isPresent() && !client.canRun("item"))
			|| !AreaRule.worksAtAll(area, settings.withoutArea())) {
			outline = List.of();
			return;
		}

		outline = find(client, new Origin(pos, id, client.isLog(pos), false), area)
			.map(found -> List.copyOf(found.blocks()))
			.orElse(List.of());
	}

	public boolean busy() {
		return !queue.isEmpty() || tick < hungerUntil;
	}

	/** How far the job being sent has got, in whole percent of its commands; empty with none. */
	public OptionalInt progress() {
		if (queue.isEmpty() || jobSteps <= 0) {
			return OptionalInt.empty();
		}

		return OptionalInt.of(Math.clamp(100L * (jobSteps - queue.size()) / jobSteps, 0, 100));
	}

	/** How many blocks the job being sent takes. */
	public int jobBlocks() {
		return jobBlocks;
	}

	/** What is outlined this tick. */
	public List<Pos> outline() {
		return outline;
	}

	/** How the last trigger went, for the log and the tests. */
	public Outcome lastOutcome() {
		return lastOutcome;
	}

	/** Drops whatever is still waiting; what has gone has gone. */
	public void cancel() {
		queue.clear();
	}

	/** A new connection is a new server: it may have different rules, so it gets its own warning. */
	public void forgetWarning() {
		warned = false;
		warnedEffect = false;
		warnedItem = false;
		hunger.reset();
	}

	/** The module was switched off: blocks carried towards the next hunger point are forgotten. */
	public void forgetHunger() {
		hunger.reset();
	}

	/**
	 * Whether a server reply should stay out of chat: one of our commands' success lines, while
	 * a job is sending or shortly after.
	 */
	public boolean hides(String translationKey) {
		if (!settings.hideFeedback() || translationKey == null || (queue.isEmpty() && tick > quietUntil)) {
			return false;
		}

		return translationKey.equals(SETBLOCK_SUCCESS) || translationKey.equals(FILL_SUCCESS)
			|| translationKey.startsWith(LOOT_SUCCESS) || translationKey.startsWith(GIVE_SUCCESS) || translationKey.startsWith(XP_SUCCESS)
			|| translationKey.startsWith(EFFECT_SUCCESS) || translationKey.startsWith(ITEM_SUCCESS);
	}

	private Outcome remember(Outcome outcome) {
		lastOutcome = Objects.requireNonNull(outcome);
		return outcome;
	}
}
