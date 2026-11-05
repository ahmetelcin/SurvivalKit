package com.merci.survivalkit.weapon;

import com.merci.survivalkit.hands.Hands;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Oto Silah: the right weapon for what is in front of you.
 *
 * <p>With a weapon already in hand, the moment the crosshair lands on a target the best weapon
 * for it is selected — on that first tick, because switching resets the attack cooldown and the
 * earlier it resets the sooner the hit is ready. The hand is only held for the switch itself.
 *
 * <p>With nothing aimed at, the weapon the hostile mobs around call for comes out instead, if the
 * user has not asked for aim only — see {@link Crowd} for how they are counted and why a new
 * majority has to last a second. Aim always comes first.
 *
 * <p>For a few seconds after a hit given or taken, the fight lock holds back any change that
 * would throw the attack cooldown away. Only a change to a different item does that, and only
 * while the cooldown is not yet full; everything else goes ahead.
 *
 * <p>With "auto hit" on, an aimed target is hit once the cooldown is full, never before, and
 * never on the tick of a switch. It is the same hit a click would make, on what the crosshair is
 * on; nothing is aimed for the player.
 *
 * <p>Like the other modules, all of the thinking is here and none of Minecraft is: which weapon
 * is best is {@link BestWeapon}'s call, and the client is reached through {@link WeaponContext}.
 */
public final class AutoWeapon implements Hands.Owner {
	/** The nine slots that can be selected directly. */
	static final int HOTBAR_SIZE = 9;

	private static final int TICKS_PER_SECOND = 20;

	private final WeaponSettings settings;
	private final Hands hands;

	/** Game tick the fight lock runs out at; nothing is locked before the first hit. */
	private long lockedUntil = Long.MIN_VALUE;

	/** What the hostile mobs around call for; read by the hand with nothing aimed, and by the ready slot. */
	private final Crowd crowd = new Crowd();

	private final AutoHitWatch watch;

	public AutoWeapon(WeaponSettings settings, Hands hands) {
		this(settings, hands, line -> {
		});
	}

	/** @param log where the auto hit's watchdog writes when it sees a stall */
	public AutoWeapon(WeaponSettings settings, Hands hands, Consumer<String> log) {
		this.settings = Objects.requireNonNull(settings, "settings");
		this.hands = Objects.requireNonNull(hands, "hands");
		this.watch = new AutoHitWatch(log);
	}

	public void tick(WeaponContext client) {
		watch.observe(act(client), () -> describe(client));
	}

	/** What the watchdog's log line says besides the state: enough to tell why it stuck. */
	private String describe(WeaponContext client) {
		return "hedef " + client.aimed().map(target -> target.id() + " (" + target.distance() + " blok)").orElse("yok")
			+ ", seçili slot " + client.selectedSlot()
			+ ", elde silah " + client.holdingWeapon()
			+ ", bekleme dolu " + client.attackReady()
			+ ", kilit " + (client.gameTime() < lockedUntil)
			+ ", tutulan el " + hands.taken();
	}

	/** One tick of Oto Silah; what the auto hit did on it, for the watchdog. */
	private AutoHitWatch.State act(WeaponContext client) {
		if (!client.ready()) {
			return AutoHitWatch.State.IDLE;
		}

		boolean locked = fightLock(client);
		List<WeaponSlot> everything = weapons(client);
		Optional<TargetKind> forCrowd = crowd.update(client.around(settings.range()),
			kind -> best(everything, kind), client.gameTime());

		forCrowd.ifPresent(kind -> prepare(client, kind, locked));

		Optional<WeaponContext.Target> aimed = client.aimed().filter(settings.targets()::allows);

		// The wand is a sword, but it is in hand for marking corners: not to be swapped away or swung.
		if (!client.holdingWeapon() || client.reserved(client.selectedSlot())) {
			return AutoHitWatch.State.IDLE;
		}

		if (aimed.isEmpty()) {
			if (settings.source() == WeaponSource.AIM_THEN_CROWD) {
				forCrowd.ifPresent(kind -> switchTo(client, kind, locked && settings.lockAim()));
			}

			return AutoHitWatch.State.IDLE;
		}

		boolean switched = switchTo(client, aimed.get().kind(), locked && settings.lockAim());

		if (!settings.autoAttack()) {
			return AutoHitWatch.State.IDLE;
		}

		if (switched) {
			return AutoHitWatch.State.SWITCH;
		}

		if (!client.attackReady()) {
			return AutoHitWatch.State.COOLDOWN;
		}

		client.attack(aimed.get().id());
		return AutoHitWatch.State.ATTACK;
	}

	/**
	 * Whether the fight lock is on. A hit given or taken starts it again for the configured
	 * seconds. Both signals are read every tick, whatever else happens, so a hit is only ever
	 * counted on the tick it came in — an old one never locks a later fight.
	 *
	 * <p>It is not a ban on every change: {@link #wouldReset} says which ones it holds back. The
	 * auto hit is not held back by it at all: hitting is not a change.
	 */
	private boolean fightLock(WeaponContext client) {
		boolean hit = client.hitSomethingSinceLastTick();
		boolean hurt = client.hurtSinceLastTick();
		long now = client.gameTime();

		if (settings.lockSeconds() <= 0) {
			return false;
		}

		if (hit || hurt) {
			lockedUntil = now + settings.lockSeconds() * TICKS_PER_SECOND;
		}

		return now < lockedUntil;
	}

	/**
	 * Called when the module is switched on. Off, it does not tick, so a hit given or taken
	 * meanwhile is still waiting on the client; it is cleared here rather than read on the first
	 * tick as a fight that just started. A lock from before switching off ends here too, and so
	 * does the crowd it was counting: a new one has to hold for its second like any other.
	 */
	public void start(WeaponContext client) {
		lockedUntil = Long.MIN_VALUE;
		crowd.reset();

		if (client.ready()) {
			client.hitSomethingSinceLastTick();
			client.hurtSinceLastTick();
		}
	}

	/** Ticks the fight lock still has to run, 0 with none. */
	public long lockTicksLeft(long now) {
		// Compared first: with no lock yet the end is Long.MIN_VALUE, and the difference would overflow.
		return now < lockedUntil ? lockedUntil - now : 0;
	}

	/** Nothing to undo: the hand is only held inside a tick, never across ticks. */
	@Override
	public void handsTaken() {
	}

	/**
	 * Readies the best weapon for what the crowd calls for in the user's slot, from anywhere in
	 * the inventory, before it is aimed at — so the switch on aim is a hotbar switch, not a hunt.
	 *
	 * <p>The item in hand never changes because of it: when the slot is the one in hand, or the
	 * best weapon is the one in hand, it is left alone — unless the user asked for the slot to be
	 * selected as well, in which case taking it in hand is the point. Only that part answers to
	 * the fight lock; a swap the hand does not see costs the cooldown nothing.
	 */
	private void prepare(WeaponContext client, TargetKind kind, boolean locked) {
		if (settings.prepSlot() < 1 || settings.prepSlot() > HOTBAR_SIZE) {
			return;
		}

		List<WeaponSlot> weapons = weapons(client);
		Optional<WeaponSlot> best = best(weapons, kind);

		if (best.isEmpty()) {
			return;
		}

		int slot = settings.prepSlot() - 1;
		boolean select = settings.selectPrepSlot();
		int from = best.get().slot();
		int hand = client.selectedSlot();

		// The wand stays in the ready slot if that is where it was put, and in hand if it is there.
		if (client.reserved(slot) || client.reserved(hand) && (slot == hand || select)) {
			return;
		}

		if (from != slot) {
			boolean touchesTheHand = slot == hand || from == hand;

			if (touchesTheHand && !select) {
				return;
			}

			Optional<String> nextInHand = slot == hand ? Optional.of(best.get().item()) : itemAt(weapons, slot);

			if (touchesTheHand && locked && wouldReset(client, weapons, nextInHand)) {
				return;
			}

			Hands.Claim[] claims = touchesTheHand
				? new Hands.Claim[] {Hands.Claim.INVENTORY, Hands.Claim.MAIN_HAND}
				: new Hands.Claim[] {Hands.Claim.INVENTORY};

			if (!hands.take(this, Hands.Priority.AUTO_WEAPON, claims)) {
				return;
			}

			client.swap(from, slot);
			hands.release(this);
		}

		if (!select || client.selectedSlot() == slot) {
			return;
		}

		if (locked && wouldReset(client, weapons(client), Optional.of(best.get().item()))) {
			return;
		}

		if (hands.take(this, Hands.Priority.AUTO_WEAPON, Hands.Claim.MAIN_HAND)) {
			client.selectSlot(slot);
			hands.release(this);
		}
	}

	/**
	 * Selects the best hotbar weapon for this kind of target; true if the selection moved.
	 *
	 * @param lockHolds the fight lock is on for this switch: a switch that would reset the
	 *                  cooldown waits
	 */
	private boolean switchTo(WeaponContext client, TargetKind kind, boolean lockHolds) {
		List<WeaponSlot> weapons = weapons(client);
		Optional<WeaponSlot> best = best(hotbar(weapons), kind);

		if (best.isEmpty() || best.get().slot() == client.selectedSlot()) {
			return false;
		}

		if (lockHolds && wouldReset(client, weapons, Optional.of(best.get().item()))) {
			return false;
		}

		if (!hands.take(this, Hands.Priority.AUTO_WEAPON, Hands.Claim.MAIN_HAND)) {
			return false;
		}

		client.selectSlot(best.get().slot());
		hands.release(this);
		return true;
	}

	/**
	 * Whether putting this item in hand now would throw away a cooldown that is still filling.
	 * The game resets it only when the item becomes a different one ({@code Player.tick}), so two
	 * of the same item — whatever their enchantments — swap freely. Anything not known to be the
	 * same item, a non-weapon in hand included, counts as a reset.
	 */
	private static boolean wouldReset(WeaponContext client, List<WeaponSlot> weapons, Optional<String> next) {
		if (client.attackReady()) {
			return false;
		}

		Optional<String> held = itemAt(weapons, client.selectedSlot());
		return held.isEmpty() || !held.equals(next);
	}

	private static Optional<String> itemAt(List<WeaponSlot> weapons, int slot) {
		return weapons.stream().filter(weapon -> weapon.slot() == slot).findFirst().map(WeaponSlot::item);
	}

	/** Every weapon but the ones another module has claimed. */
	private static List<WeaponSlot> weapons(WeaponContext client) {
		return client.weapons().stream().filter(weapon -> !client.reserved(weapon.slot())).toList();
	}

	private Optional<WeaponSlot> best(List<WeaponSlot> weapons, TargetKind kind) {
		return BestWeapon.choose(weapons, kind, settings.types(), settings.durabilityThreshold());
	}

	private static List<WeaponSlot> hotbar(List<WeaponSlot> weapons) {
		return weapons.stream().filter(weapon -> weapon.slot() < HOTBAR_SIZE).toList();
	}
}
