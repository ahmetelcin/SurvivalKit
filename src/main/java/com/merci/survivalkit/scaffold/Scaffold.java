package com.merci.survivalkit.scaffold;

import com.merci.survivalkit.area.Area;
import com.merci.survivalkit.area.AreaRule;
import com.merci.survivalkit.area.WithoutArea;
import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.hud.HudLine;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.merci.survivalkit.vein.Pos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntFunction;

/**
 * Puts a block under the next step when there is nothing there. The view is never turned: the
 * block goes down through the click a player would make on a neighbour's face
 * ({@link PlacementRule}), with the stack {@link BlockChoice} picks in hand.
 *
 * <p>Like {@link com.merci.survivalkit.autotool.AutoTool}, a run of blocks is one switch out and
 * one switch back: the hand stays on the blocks while they keep going down, and what was in it
 * comes back once nothing has needed a block for {@link #IDLE_TICKS}.
 */
public final class Scaffold implements Hands.Owner {
	/** How long nothing may need a block before the hand goes back. */
	public static final int IDLE_TICKS = 10;

	/** How far from the feet a block İskele put down is still remembered as its own, in blocks. */
	static final double LAID_REACH = 16;

	/**
	 * How many ticks of walking ahead the next step is looked for. The body is 0.6 wide, so it
	 * stands on an edge until its middle is 0.3 past it — a tick and a half of walking — and two
	 * ticks ahead the block is already down when the middle gets there.
	 */
	static final int LOOKAHEAD_TICKS = 2;

	/** Slower than this, in blocks a tick, the player is standing still and there is no "ahead". */
	private static final double STILL = 0.01;

	/**
	 * How far ahead of the middle of the feet SafeWalk looks for an edge: the body reaches 0.3 past
	 * it, and a tick of walking another 0.2 or so.
	 */
	static final double EDGE_REACH = 0.6;

	/** A fall longer than a jump: vanilla jumps about 1.25 blocks high. */
	static final double FALLING = 1.5;

	/**
	 * Slower than this, in blocks a tick, the body is no longer sliding anywhere: what speed is left
	 * dies away within a hundredth of a block.
	 */
	static final double SLIDING = 0.003;

	/** How often the count is said again though it has not changed: the game fades it after three seconds. */
	static final int REFRESH_TICKS = 20;

	/** How long something said stays on the HUD: three seconds, as long as the game keeps the action bar. */
	static final int MESSAGE_TICKS = 60;

	/** How many steps of a ramp past the feet's column are built, whatever the keys: a jump's length. */
	static final int RAMP_AHEAD = 2;

	/** Half the body's width, and its height: a block may not go where it would be inside the player. */
	private static final double HALF_WIDTH = 0.3;
	private static final double HEIGHT = 1.8;

	private static final int NOT_SHOWN = -1;

	private static final int NO_SWAP = -1;
	private static final int HOTBAR_SIZE = 9;

	/** One block to fill, and whether it is on the way itself — the line walked, corners too — rather than beside it. */
	record Target(Pos pos, boolean path) {
	}

	private final ScaffoldSettings settings;
	private final Hands hands;

	private boolean out;
	private int previousSlot;
	private int swappedFrom = NO_SWAP;
	private int swappedTo;
	private int idle;
	private int cooldown;
	/** The y the blocks go in, unless towering or on a ramp; see {@link #track}. */
	private int level;
	private boolean levelKnown;
	/** The y {@link YLock#START} holds, once taken. */
	private int startY;
	private boolean startKnown;
	/** For the lock key: a press is the tick it goes down. */
	private boolean lockKeyWasDown;
	/** How many blocks sneaking has taken the locked y down, and for which kind of lock. */
	private int lowered;
	private YLock loweredFor = YLock.OFF;
	private boolean sneakKeyWasDown;
	/** Ticks the lock key's line stays on the action bar before the count may take it back. */
	private int barHeld;
	/** The way last walked, along one axis: one of the two is ±1, or both 0 before any walking. */
	private int walkX;
	private int walkZ;
	/**
	 * The ramp: the column it started in, the way it goes from there (both 0 until the first step
	 * shows it), and its y in that column. Every block on along that way is a block up or down.
	 */
	private boolean rampKnown;
	private Ramp rampFor = Ramp.OFF;
	private int rampFromX;
	private int rampFromZ;
	private int rampX;
	private int rampZ;
	private int rampBase;
	/** The block last stood on, for "same block". */
	private Optional<String> standingOn = Optional.empty();
	/** Blocks of bridge since the last torch; once due, the next one waits for somewhere to go. */
	private int sinceTorch;
	private boolean torchDue;
	/** The last block put down on the way, which the next torch goes beside. */
	private Pos lastBridge;
	/** Running out of torches has been said; said again only after there were some. */
	private boolean torchWarned;
	/** The support list refusing everything there has been said; said again only after a block went down. */
	private boolean supportWarned;
	/**
	 * The blocks of this bridge İskele put down itself, with what each was: supports whatever the
	 * list says, while they are still that block and the player is still near them.
	 */
	private final Map<Pos, String> laid = new HashMap<>();
	/** What the HUD shows, and the last thing said with how long it has left there. */
	private List<HudLine> hud = List.of();
	private HudLine message;
	private int messageTicks;
	/** Whether this module is holding sneak down. */
	private boolean sneaking;
	/** A block was wanted this tick and there was none to put down. */
	private boolean starved;
	/** The count last put on the action bar, and how long ago. */
	private int shown = NOT_SHOWN;
	private int shownLevel;
	private int sinceShown;
	/** Kept for the interruption, which arrives while another module is ticking. */
	private ScaffoldContext current;

	public Scaffold(ScaffoldSettings settings, Hands hands) {
		this.settings = Objects.requireNonNull(settings, "settings");
		this.hands = Objects.requireNonNull(hands, "hands");
	}

	public void tick(ScaffoldContext client) {
		current = client;

		if (!client.ready()) {
			return;
		}

		lockKey(client);
		lowerKey(client);

		if (paused(client)) {
			sneak(client, false);
			return;
		}

		track(client);

		if (cooldown > 0) {
			cooldown--;
		} else {
			put(client);
		}

		sneak(client, wantsToSneak(client));
		info(client);
	}

	/** One tick's worth of blocks, then the hand goes back if nothing has needed one for a while. */
	private void put(ScaffoldContext client) {
		starved = false;
		Optional<Area> area = client.area();
		List<Target> plan = plan(client);
		forgetFarLaid(client);
		int placed = 0;
		boolean refused = false;

		for (Target target : plan) {
			if (placed >= settings.perTick()) {
				break;
			}

			Pos pos = target.pos();

			if (!fills(client.gap(pos)) || !AreaRule.allows(pos, area, WithoutArea.EVERYWHERE)) {
				continue;
			}

			Optional<Placement> placement = find(client, pos);

			// A step of a ramp has nothing beside it to go against until the block under it, or
			// under the step before, is down.
			if (placement.isEmpty() && rampKnown) {
				Optional<Placement> step = rampSupport(client, pos, area);

				if (step.isPresent()) {
					if (!blocksInHand(client)) {
						break;
					}

					if (lay(client, step.get())) {
						placed++;
					}

					placement = placed < settings.perTick() ? find(client, pos) : Optional.empty();
				}
			}

			if (placement.isEmpty()) {
				refused |= refusedByTheList(client, pos);
				continue;
			}

			if (!blocksInHand(client)) {
				break;
			}

			if (lay(client, placement.get())) {
				placed++;

				if (target.path()) {
					bridged(pos);
				}
			}
		}

		if (settings.torches() && torchDue && placed < settings.perTick()) {
			placed += torch(client, plan, area);
		}

		if (placed > 0) {
			idle = 0;
			cooldown = settings.delay();
			supportWarned = false;
		} else {
			if (refused && !supportWarned) {
				supportWarned = true;
				say(client, Lang.of(Keys.SCAFFOLD_NO_SUPPORT), true);
			}

			if (out && ++idle >= IDLE_TICKS) {
				giveBack(client);
			}
		}
	}

	private Optional<Placement> find(ScaffoldContext client, Pos pos) {
		return PlacementRule.find(pos, client, client.eyes(), client.reach(), settings.supportMode(), settings.supports(),
			support -> laidHere(client, support));
	}

	/** Whether {@code pos} has something in reach to build against that only the support list keeps it off. */
	private boolean refusedByTheList(ScaffoldContext client, Pos pos) {
		return settings.supportMode() == SupportMode.LISTED
			&& PlacementRule.find(pos, client, client.eyes(), client.reach(), SupportMode.ALL, List.of(), support -> false).isPresent();
	}

	/** A block down, and remembered as İskele's own as what the world now says it is. */
	private boolean lay(ScaffoldContext client, Placement placement) {
		if (!client.place(placement)) {
			return false;
		}

		client.clickable(placement.target()).ifPresent(id -> laid.put(placement.target(), id));
		return true;
	}

	/** Whether İskele put the block at {@code pos} down itself and it is still that block. */
	private boolean laidHere(ScaffoldContext client, Pos pos) {
		String id = laid.get(pos);
		return id != null && client.clickable(pos).filter(id::equals).isPresent();
	}

	/** A bridge walked away from is no longer this one. */
	private void forgetFarLaid(ScaffoldContext client) {
		Vec feet = client.feet();
		laid.keySet().removeIf(pos -> new Vec(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5).distanceTo(feet) > LAID_REACH);
	}

	/** Whether {@link #put} could fill {@code pos}: something to build against, or a ramp step's own support. */
	private boolean placeable(ScaffoldContext client, Pos pos, Optional<Area> area) {
		return find(client, pos).isPresent() || rampKnown && rampSupport(client, pos, area).isPresent();
	}

	/**
	 * What a ramp's step stands on: going up, the block under it; going down, the block under the
	 * step before it — neither in the way of walking.
	 */
	private Optional<Placement> rampSupport(ScaffoldContext client, Pos step, Optional<Area> area) {
		Pos support = settings.ramp() == Ramp.UP
			? new Pos(step.x(), step.y() - 1, step.z())
			: new Pos(step.x() - rampX, step.y(), step.z() - rampZ);

		if (!fills(client.gap(support)) || !AreaRule.allows(support, area, WithoutArea.EVERYWHERE)
			|| !clearOfBody(support, client.feet())) {
			return Optional.empty();
		}

		return find(client, support);
	}

	/** Another block of bridge down on the way: a torch is due every {@link ScaffoldSettings#torchInterval}. */
	private void bridged(Pos pos) {
		lastBridge = pos;

		if (settings.torches() && !torchDue && ++sinceTorch >= settings.torchInterval()) {
			torchDue = true;
		}
	}

	/**
	 * A wall torch on the side of the last block of bridge, below the walkway and out of its way.
	 * With nowhere for it there — the side is taken, out of reach, a bridge block still to come —
	 * it waits for the next block of bridge rather than being skipped. With no torches left, that
	 * is said once.
	 *
	 * @return how many went down: 0 or 1
	 */
	private int torch(ScaffoldContext client, List<Target> plan, Optional<Area> area) {
		if (BlockChoice.torch(client.blocks(), client.selectedSlot()).isEmpty()) {
			if (!torchWarned) {
				torchWarned = true;
				say(client, Lang.of(Keys.SCAFFOLD_NO_TORCHES), true);
			}

			return 0;
		}

		torchWarned = false;
		Optional<Placement> spot = torchSpot(client, plan, area);

		if (spot.isEmpty() || !torchInHand(client) || !client.place(spot.get())) {
			return 0;
		}

		torchDue = false;
		sinceTorch = 0;
		return 1;
	}

	/**
	 * Beside {@link #lastBridge}, square to the way walked, past any bridge already there: the
	 * first air on either side, on the bridge's own y, clicked on the bridge block's face.
	 */
	private Optional<Placement> torchSpot(ScaffoldContext client, List<Target> plan, Optional<Area> area) {
		if (lastBridge == null || walkX == 0 && walkZ == 0) {
			return Optional.empty();
		}

		for (int sign : new int[] {1, -1}) {
			int dx = Math.abs(walkZ) * sign;
			int dz = Math.abs(walkX) * sign;
			Pos support = lastBridge;
			Pos cell = new Pos(support.x() + dx, support.y(), support.z() + dz);

			for (int across = 1; across < settings.width() && client.gap(cell) == Gap.NONE; across++) {
				support = cell;
				cell = new Pos(cell.x() + dx, cell.y(), cell.z() + dz);
			}

			Pos torchAt = cell;
			boolean planned = plan.stream().anyMatch(target -> target.pos().equals(torchAt));

			if (client.gap(cell) != Gap.AIR || planned || client.clickable(support).isEmpty()
				|| !AreaRule.allows(cell, area, WithoutArea.EVERYWHERE)) {
				continue;
			}

			Vec hit = new Vec(support.x() + 0.5 + dx * 0.5, support.y() + 0.5, support.z() + 0.5 + dz * 0.5);

			if (hit.distanceTo(client.eyes()) <= client.reach()) {
				return Optional.of(new Placement(cell, support, face(dx, dz), hit));
			}
		}

		return Optional.empty();
	}

	private static Face face(int dx, int dz) {
		if (dx != 0) {
			return dx > 0 ? Face.EAST : Face.WEST;
		}

		return dz > 0 ? Face.SOUTH : Face.NORTH;
	}

	/**
	 * What the player is shown: the box round the next block to go down; on the HUD, how many
	 * blocks are left and the locked y, the whole time, with the last thing said above them for
	 * {@link #MESSAGE_TICKS}; and with "show on the action bar too", while blocks are going down —
	 * or one was wanted and there was none — the count on the action bar, said again when it
	 * changes and once a second besides, before the game fades it.
	 */
	private void info(ScaffoldContext client) {
		if (settings.showTarget()) {
			next(client).ifPresent(client::outline);
		}

		hud = hudLines(client);

		if (!settings.actionBar()) {
			shown = NOT_SHOWN;
			barHeld = 0;
			return;
		}

		if (barHeld > 0) {
			barHeld--;
			return;
		}

		boolean locked = settings.yLock() != YLock.OFF;

		if (!settings.showCount() && !locked || !out && !starved) {
			shown = NOT_SHOWN;
			return;
		}

		int left = settings.showCount() ? remaining(client) : 0;
		int y = here(client);

		if (left == shown && y == shownLevel && ++sinceShown < REFRESH_TICKS) {
			return;
		}

		shown = left;
		shownLevel = y;
		sinceShown = 0;

		if (!settings.showCount()) {
			client.actionBar(Lang.of(Keys.SCAFFOLD_Y, y), false);
			return;
		}

		boolean low = left == 0 || left < settings.lowWarning();
		String text = left == 0 ? Lang.of(Keys.SCAFFOLD_EMPTY) : Lang.of(low ? Keys.SCAFFOLD_LOW : Keys.SCAFFOLD_COUNT, left);
		client.actionBar(locked ? Lang.of(Keys.SCAFFOLD_WITH_Y, text, y) : text, low);
	}

	/** The HUD's lines this tick: the last thing said, while it lasts, then the count and the locked y. */
	private List<HudLine> hudLines(ScaffoldContext client) {
		List<HudLine> lines = new ArrayList<>(2);

		if (messageTicks > 0) {
			messageTicks--;
			lines.add(message);
		}

		boolean locked = settings.yLock() != YLock.OFF;
		int y = here(client);

		if (settings.showCount()) {
			int left = remaining(client);
			boolean low = left == 0 || left < settings.lowWarning();
			String text = left == 0 ? Lang.of(Keys.SCAFFOLD_EMPTY) : Lang.of(low ? Keys.SCAFFOLD_LOW : Keys.SCAFFOLD_COUNT, left);
			lines.add(new HudLine(locked ? Lang.of(Keys.SCAFFOLD_WITH_Y, text, y) : text, low));
		} else if (locked) {
			lines.add(HudLine.of(Lang.of(Keys.SCAFFOLD_Y, y)));
		}

		return List.copyOf(lines);
	}

	/** What the HUD shows for Scaffold, as of the last tick. */
	public List<HudLine> hudLines() {
		return hud;
	}

	/** Something to tell the player: on the HUD for a while, and on the action bar too if asked. */
	private void say(ScaffoldContext client, String text, boolean warning) {
		message = new HudLine(text, warning);
		messageTicks = MESSAGE_TICKS;

		if (settings.actionBar()) {
			client.actionBar(text, warning);
			barHeld = REFRESH_TICKS;
		}
	}

	/** The bridge's y in the column the feet are in: the level, or where the ramp has got to. */
	private int here(ScaffoldContext client) {
		Vec feet = client.feet();
		return yAt((int) Math.floor(feet.x()), (int) Math.floor(feet.z()));
	}

	/**
	 * The lock key, on the tick it goes down: the lock takes the y under the feet — kept for this
	 * session with {@link YLock#START}, written into the number with {@link YLock#FIXED} — and the
	 * action bar says where it now is. With the lock off it says only that. A ramp starts afresh
	 * from there.
	 */
	private void lockKey(ScaffoldContext client) {
		boolean down = client.lockKeyHeld();
		boolean pressed = down && !lockKeyWasDown;
		lockKeyWasDown = down;

		if (!pressed) {
			return;
		}

		int y = underFeet(client.feet());
		String text = switch (settings.yLock()) {
			case OFF -> Lang.of(Keys.SCAFFOLD_Y_LOCK_OFF);
			case START -> {
				startY = y;
				startKnown = true;
				yield Lang.of(Keys.SCAFFOLD_Y_LOCKED, y);
			}
			case FIXED -> {
				settings.fixedY(y);
				yield Lang.of(Keys.SCAFFOLD_Y_LOCKED, settings.fixedY());
			}
		};

		if (settings.yLock() != YLock.OFF) {
			lowered = 0;
			rampKnown = false;
		}

		say(client, text, false);
	}

	/**
	 * Sneak, on the tick it goes down, with "descending bridge" on and a Y lock to take down: the
	 * locked y goes a block lower, and the action bar says where it now is. SafeWalk's own sneak is
	 * not a press — only the real key is.
	 */
	private void lowerKey(ScaffoldContext client) {
		boolean down = client.sneakKeyHeld();
		boolean pressed = down && !sneakKeyWasDown;
		sneakKeyWasDown = down;

		if (!pressed || !settings.descend()) {
			return;
		}

		OptionalInt locked = lockedY(client);

		if (locked.isEmpty()) {
			return;
		}

		lowered++;
		say(client, Lang.of(Keys.SCAFFOLD_Y_LOCKED, locked.getAsInt() - lowered), false);
	}

	/**
	 * The y the lock holds the blocks at, empty with it off. {@link YLock#START} takes the y under
	 * the feet the first time it is asked — Scaffold switched on, or the lock just set to it.
	 * Sneaking down is not in it: {@link #lowered} is, and goes when the kind of lock changes.
	 */
	private OptionalInt lockedY(ScaffoldContext client) {
		if (settings.yLock() != loweredFor) {
			loweredFor = settings.yLock();
			lowered = 0;
		}

		return switch (settings.yLock()) {
			case OFF -> {
				startKnown = false;
				yield OptionalInt.empty();
			}
			case START -> {
				if (!startKnown) {
					startY = underFeet(client.feet());
					startKnown = true;
				}

				yield OptionalInt.of(startY);
			}
			case FIXED -> {
				startKnown = false;
				yield OptionalInt.of(settings.fixedY());
			}
		};
	}

	/** The first block this tick would still fill, had it a block and something to put it against. */
	private Optional<Pos> next(ScaffoldContext client) {
		Optional<Area> area = client.area();
		return targets(client).stream()
			.filter(target -> fills(client.gap(target)) && AreaRule.allows(target, area, WithoutArea.EVERYWHERE)
				&& placeable(client, target, area))
			.findFirst();
	}

	/** How many blocks are left to build with: what the choice may take, the list's alone with no fallback. */
	int remaining(ScaffoldContext client) {
		return BlockChoice.usable(client.blocks(), settings.blocked()).stream()
			.filter(stack -> settings.fallback() != BlockChoice.Fallback.NOTHING || settings.preferred().contains(stack.id()))
			.mapToInt(BlockStack::count)
			.sum();
	}

	/** Whether to keep its hands off for now: a screen open, falling, in lava — each as the user asked. */
	private boolean paused(ScaffoldContext client) {
		return settings.pauseWithScreen() && client.screenOpen()
			|| settings.pauseWhileFalling() && !client.onGround() && client.fallDistance() > FALLING
			|| settings.pauseInLava() && client.inLava();
	}

	/**
	 * SafeWalk is the game's own sneak, which stops a player at an edge: always, or only on the
	 * ground at an edge with nothing yet beyond it — no block could go there (none left, outside
	 * the area, a gap kind left alone) or none has yet. "At an edge" is any of three: the middle
	 * of the feet already over the gap, the keys heading for it, or the body still sliding for it.
	 * The game's sneak holds the body back but leaves its speed alone, so letting go the moment
	 * the keys do would let that speed carry it off.
	 */
	private boolean wantsToSneak(ScaffoldContext client) {
		return switch (settings.safeWalk()) {
			case OFF -> false;
			case ALWAYS -> true;
			case AT_EDGE -> client.onGround() && edge(client).stream().anyMatch(pos -> client.gap(pos) != Gap.NONE && !stepDown(client, pos));
		};
	}

	/**
	 * With a lock or a ramp, a column whose bridge is already down a block lower is a step to walk
	 * down, not an edge to stop at: the lock taken down by sneaking, or a ramp going down.
	 */
	private boolean stepDown(ScaffoldContext client, Pos pos) {
		Pos below = new Pos(pos.x(), pos.y() - 1, pos.z());
		return (settings.yLock() != YLock.OFF || rampKnown) && yAt(pos.x(), pos.z()) == below.y()
			&& client.gap(below) == Gap.NONE;
	}

	/** The blocks under the middle of the feet, under where the keys take them, and under where they are sliding. */
	private List<Pos> edge(ScaffoldContext client) {
		Vec feet = client.feet();
		int y = underFeet(feet);
		List<Pos> edge = new ArrayList<>(3);
		edge.add(new Pos((int) Math.floor(feet.x()), y, (int) Math.floor(feet.z())));

		for (Vec way : new Vec[] {client.heading(), client.motion()}) {
			double length = Math.hypot(way.x(), way.z());

			if (length >= SLIDING) {
				edge.add(new Pos(
					(int) Math.floor(feet.x() + way.x() / length * EDGE_REACH), y,
					(int) Math.floor(feet.z() + way.z() / length * EDGE_REACH)));
			}
		}

		return edge;
	}

	/** Holds or lets go of sneak — only ever letting go of a sneak this module started. */
	private void sneak(ScaffoldContext client, boolean down) {
		if (down != sneaking) {
			sneaking = down;
			client.sneak(down);
		}
	}

	/** Called when the module is switched off. Anything taken out goes back. */
	public void stop(ScaffoldContext client) {
		giveBack(client);

		if (client.ready()) {
			sneak(client, false);
		}

		sneaking = false;
		levelKnown = false;
		startKnown = false;
		lockKeyWasDown = false;
		lowered = 0;
		sneakKeyWasDown = false;
		barHeld = 0;
		walkX = 0;
		walkZ = 0;
		rampKnown = false;
		standingOn = Optional.empty();
		sinceTorch = 0;
		torchDue = false;
		lastBridge = null;
		torchWarned = false;
		supportWarned = false;
		laid.clear();
		hud = List.of();
		messageTicks = 0;
	}

	@Override
	public void handsTaken() {
		if (current != null) {
			undo(current);
		}
	}

	/** Where the blocks go this tick, nearest first. */
	List<Pos> targets(ScaffoldContext client) {
		return plan(client).stream().map(Target::pos).toList();
	}

	/**
	 * Where the blocks go this tick, nearest first.
	 *
	 * <p>Towering — on, the jump key held if that is asked for, and the feet risen a whole block
	 * off the level — it is only the block under the feet, and the level rises with it. Otherwise
	 * the way: the column under the feet, the one under where the feet will be
	 * {@link #LOOKAHEAD_TICKS} from now, on a ramp its next {@link #RAMP_AHEAD} steps, and up to
	 * {@link ScaffoldSettings#extend} more straight on in the direction of walking — with "diagonal", the corner between any two of them that
	 * touch only at an edge. Then, for a wider bridge, the columns beside the way. Each goes in
	 * the bridge's y for its column ({@link #yAt}).
	 *
	 * <p>None whose top would be above the feet: a block there would be inside the player and the
	 * game would refuse it. On a ramp, where the next step is meant to be at the feet, that is
	 * asked of each block: not inside the body, and no more than a step up.
	 */
	List<Target> plan(ScaffoldContext client) {
		Vec feet = client.feet();
		int x = (int) Math.floor(feet.x());
		int z = (int) Math.floor(feet.z());
		int under = underFeet(feet);

		if (towering(client, under)) {
			level = under;
			return List.of(new Target(new Pos(x, under, z), true));
		}

		if (!rampKnown && level + 1 > feet.y() + 1e-6) {
			return List.of();
		}

		Vec motion = client.motion();
		List<Pos> columns = new ArrayList<>();
		column(columns, x, z);
		column(columns, feet.x() + motion.x() * LOOKAHEAD_TICKS, feet.z() + motion.z() * LOOKAHEAD_TICKS);

		// Where the keys are taking the feet: SafeWalk may have stopped them at this very edge, and
		// standing still they have no motion to look ahead by.
		Vec heading = client.heading();

		if (Math.hypot(heading.x(), heading.z()) >= STILL) {
			column(columns, feet.x() + heading.x() * EDGE_REACH, feet.z() + heading.z() * EDGE_REACH);
		}

		// A ramp's step has to be down before the body gets to its column: a jump carries the
		// player a block and a half, and a step under the body can no longer go in.
		if (rampKnown) {
			for (int step = 1; step <= RAMP_AHEAD; step++) {
				column(columns, x + rampX * step, z + rampZ * step);
			}
		}

		double speed = Math.hypot(motion.x(), motion.z());

		if (speed > STILL) {
			for (int step = 1; step <= settings.extend(); step++) {
				column(columns, feet.x() + motion.x() / speed * step, feet.z() + motion.z() / speed * step);
			}
		}

		if (settings.diagonal()) {
			columns = withCorners(columns, feet, speed > STILL ? motion : heading);
		}

		List<Target> plan = new ArrayList<>();

		for (Pos column : columns) {
			add(plan, new Target(new Pos(column.x(), yAt(column.x(), column.z()), column.z()), true));
		}

		for (int across : sides(feet)) {
			int dx = Math.abs(walkZ) * across;
			int dz = Math.abs(walkX) * across;

			for (Pos column : columns) {
				int sx = column.x() + dx;
				int sz = column.z() + dz;
				add(plan, new Target(new Pos(sx, yAt(sx, sz), sz), false));
			}
		}

		if (rampKnown) {
			plan.removeIf(target -> !clearOfBody(target.pos(), feet));
		}

		return plan;
	}

	/**
	 * How far to each side of the way the bridge reaches, in blocks square to the way walked: none
	 * at width 1, the side the feet are nearer at 2, both at 3.
	 */
	private int[] sides(Vec feet) {
		if (settings.width() <= 1 || walkX == 0 && walkZ == 0) {
			return new int[0];
		}

		if (settings.width() >= 3) {
			return new int[] {-1, 1};
		}

		double across = walkX != 0 ? feet.z() : feet.x();
		return new int[] {across - Math.floor(across) < 0.5 ? -1 : 1};
	}

	/**
	 * The columns with a corner put between any two in a row that touch only at an edge — walking
	 * slantwise, the floor would otherwise be a chain of blocks joined at their corners. Of the
	 * two corners, the one nearer the line walked.
	 */
	private static List<Pos> withCorners(List<Pos> columns, Vec feet, Vec way) {
		List<Pos> joined = new ArrayList<>(columns.size() * 2);

		for (int i = 0; i < columns.size(); i++) {
			Pos to = columns.get(i);

			if (i > 0) {
				Pos from = columns.get(i - 1);

				if (Math.abs(to.x() - from.x()) == 1 && Math.abs(to.z() - from.z()) == 1) {
					Pos a = new Pos(to.x(), 0, from.z());
					Pos b = new Pos(from.x(), 0, to.z());
					Pos corner = offLine(b, feet, way) < offLine(a, feet, way) ? b : a;

					if (!joined.contains(corner)) {
						joined.add(corner);
					}
				}
			}

			if (!joined.contains(to)) {
				joined.add(to);
			}
		}

		return joined;
	}

	/** How far the middle of a column is from the line walked; from the feet, with no way to walk. */
	private static double offLine(Pos column, Vec feet, Vec way) {
		double cx = column.x() + 0.5 - feet.x();
		double cz = column.z() + 0.5 - feet.z();
		double length = Math.hypot(way.x(), way.z());

		if (length < 1e-9) {
			return Math.hypot(cx, cz);
		}

		return Math.abs(cx * way.z() - cz * way.x()) / length;
	}

	private static void column(List<Pos> columns, double x, double z) {
		column(columns, (int) Math.floor(x), (int) Math.floor(z));
	}

	private static void column(List<Pos> columns, int x, int z) {
		Pos column = new Pos(x, 0, z);

		if (!columns.contains(column)) {
			columns.add(column);
		}
	}

	/** A block there would not be inside the body, nor more than a step above the feet. */
	private static boolean clearOfBody(Pos pos, Vec feet) {
		double e = 1e-6;
		boolean inside = pos.x() < feet.x() + HALF_WIDTH - e && pos.x() + 1 > feet.x() - HALF_WIDTH + e
			&& pos.z() < feet.z() + HALF_WIDTH - e && pos.z() + 1 > feet.z() - HALF_WIDTH + e
			&& pos.y() < feet.y() + HEIGHT - e && pos.y() + 1 > feet.y() + e;
		return !inside && pos.y() <= feet.y() + e;
	}

	/**
	 * The bridge's y in a column: the level, or on a ramp, its y where it started stepped once for
	 * every block on along its way — behind the start as much as ahead.
	 */
	private int yAt(int x, int z) {
		if (!rampKnown) {
			return level;
		}

		int on = (x - rampFromX) * rampX + (z - rampFromZ) * rampZ;
		return rampBase + (settings.ramp() == Ramp.UP ? on : -on);
	}

	/**
	 * Keeps {@link #level} where the feet last stood. With "keep Y" it stays put for the whole run
	 * of blocks — stepping up onto something on the way does not lift the bridge — and is taken
	 * afresh only once the run is over. With a Y lock it is the locked y, whatever the feet do:
	 * jumping, running and landing higher alike leave the bridge flat. Also the way walked, the
	 * block stood on, and the ramp.
	 */
	private void track(ScaffoldContext client) {
		Vec feet = client.feet();
		int x = (int) Math.floor(feet.x());
		int z = (int) Math.floor(feet.z());
		int under = underFeet(feet);
		OptionalInt locked = lockedY(client);

		if (locked.isPresent()) {
			level = locked.getAsInt() - lowered;
			levelKnown = true;
		} else if (!levelKnown) {
			level = under;
			levelKnown = true;
		} else if (client.onGround() && (!settings.keepY() || !out)) {
			level = under;
		}

		boolean walking = walk(client);

		if (client.onGround()) {
			client.clickable(new Pos(x, under, z)).ifPresent(id -> standingOn = Optional.of(id));
		}

		if (settings.ramp() == Ramp.OFF || settings.ramp() != rampFor) {
			rampFor = settings.ramp();
			rampKnown = false;
		}

		if (settings.ramp() == Ramp.OFF) {
			return;
		}

		if (!rampKnown) {
			rampKnown = true;
			rampFromX = x;
			rampFromZ = z;
			rampX = 0;
			rampZ = 0;
			rampBase = level;
		}

		// With a lock, the lock is the ramp's start: the lock key and sneaking move the whole ramp.
		if (locked.isPresent()) {
			rampBase = level;
		}

		if (rampX == 0 && rampZ == 0 && walking) {
			rampX = walkX;
			rampZ = walkZ;
		}
	}

	/** Takes the way walked from the motion, or the keys while standing; false while neither says. */
	private boolean walk(ScaffoldContext client) {
		Vec motion = client.motion();
		Vec way = Math.hypot(motion.x(), motion.z()) > STILL ? motion : client.heading();

		if (Math.hypot(way.x(), way.z()) <= STILL) {
			return false;
		}

		if (Math.abs(way.x()) >= Math.abs(way.z())) {
			walkX = way.x() > 0 ? 1 : -1;
			walkZ = 0;
		} else {
			walkX = 0;
			walkZ = way.z() > 0 ? 1 : -1;
		}

		return true;
	}

	/** Rising off the level by vanilla's own jump, with the tower asked for and no Y lock or ramp to hold the level. */
	private boolean towering(ScaffoldContext client, int under) {
		return settings.tower() && settings.yLock() == YLock.OFF && settings.ramp() == Ramp.OFF
			&& (client.jumpHeld() || !settings.towerOnlyWithJump())
			&& !client.onGround() && under > level;
	}

	private static int underFeet(Vec feet) {
		return (int) Math.floor(feet.y() + 1e-6) - 1;
	}

	private static void add(List<Target> plan, Target target) {
		if (plan.stream().noneMatch(planned -> planned.pos().equals(target.pos()))) {
			plan.add(target);
		}
	}

	private boolean fills(Gap gap) {
		return switch (gap) {
			case AIR -> settings.fillAir();
			case WATER -> settings.fillWater();
			case LAVA -> settings.fillLava();
			case NONE -> false;
		};
	}

	/** The stack {@link BlockChoice} picks — with "same block", the block stood on first — into the main hand. */
	private boolean blocksInHand(ScaffoldContext client) {
		Optional<String> first = settings.sameBlock() ? standingOn : Optional.empty();
		return inHand(client, selected -> BlockChoice.choose(client.blocks(), first, settings.preferred(), settings.blocked(),
			settings.preferFullStack(), settings.fallback(), selected), true);
	}

	private boolean torchInHand(ScaffoldContext client) {
		return inHand(client, selected -> BlockChoice.torch(client.blocks(), selected), false);
	}

	/**
	 * Gets the chosen stack into the main hand. A swap from the backpack is undone before another
	 * stack is fetched, so the item from before the run is always the one that comes back — and
	 * the inventory is read again, since the undo moved things.
	 *
	 * @param blocks whether finding nothing is running out of blocks, for the action bar
	 */
	private boolean inHand(ScaffoldContext client, IntFunction<Optional<BlockStack>> choose, boolean blocks) {
		for (int attempt = 0; attempt < 2; attempt++) {
			int selected = client.selectedSlot();
			Optional<BlockStack> choice = choose.apply(selected);

			if (choice.isEmpty()) {
				starved |= blocks;
				return false;
			}

			BlockStack stack = choice.get();

			if (stack.slot() != selected && swappedFrom != NO_SWAP) {
				client.swap(swappedFrom, swappedTo);
				swappedFrom = NO_SWAP;
				continue;
			}

			boolean inHotbar = stack.slot() < HOTBAR_SIZE;
			Hands.Claim[] claims = inHotbar
				? new Hands.Claim[] {Hands.Claim.MAIN_HAND}
				: new Hands.Claim[] {Hands.Claim.MAIN_HAND, Hands.Claim.INVENTORY};

			if (!hands.take(this, Hands.Priority.SCAFFOLD, claims)) {
				return false;
			}

			if (!out) {
				out = true;
				previousSlot = selected;
				idle = 0;
			}

			if (stack.slot() == selected) {
				return true;
			}

			if (inHotbar) {
				client.selectSlot(stack.slot());
			} else {
				swappedFrom = stack.slot();
				swappedTo = selected;
				client.swap(swappedFrom, swappedTo);
			}

			return true;
		}

		return false;
	}

	/** Nothing more to put down: what was in hand goes back if the user wants it to. */
	private void giveBack(ScaffoldContext client) {
		if (out && settings.restorePrevious()) {
			undo(client);
		}

		forget();
		hands.release(this);
	}

	private void undo(ScaffoldContext client) {
		// Switched off on the way out of a world: there is no inventory left to put back.
		if (!out || !client.ready()) {
			forget();
			return;
		}

		if (swappedFrom != NO_SWAP) {
			client.swap(swappedFrom, swappedTo);
		}

		if (client.selectedSlot() != previousSlot) {
			client.selectSlot(previousSlot);
		}

		forget();
	}

	private void forget() {
		out = false;
		swappedFrom = NO_SWAP;
		idle = 0;
	}
}
