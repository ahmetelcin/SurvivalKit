package com.merci.survivalkit.weapon;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Which weapon the hostile mobs around call for, when nothing is aimed at.
 *
 * <p>Each hostile mob votes for its own best weapon, and mobs whose best weapon is the same one
 * are on the same side whatever they are: without a Bane sword a spider is on the creeper's side.
 * The biggest side wins; a tie goes to the side with the nearest mob in it. Cows, villagers and
 * players never vote.
 *
 * <p>A new winner has to hold for {@link #HOLD_TICKS} before it is taken, and the one taken
 * stays until another has held as long — so a mob stepping in and out of range, or the count
 * wavering for a moment, never sets the hand flickering. The first winner waits as well.
 *
 * <p>What is kept is the target kind the weapon is for, not the weapon or its slot: the slot
 * moves when the ready slot is filled, and the weapon is looked up again every tick.
 */
final class Crowd {
	/** A second. */
	static final int HOLD_TICKS = 20;

	private TargetKind chosen;
	private TargetKind candidate;
	private long candidateSince;

	/**
	 * @param around everyone in range, as the client gives them; only hostile mobs count
	 * @param best   the best weapon for a kind of target, out of everything in reach
	 * @return the kind of target to arm for, if one has been taken and anyone counts right now
	 */
	Optional<TargetKind> update(List<WeaponContext.Target> around, Function<TargetKind, Optional<WeaponSlot>> best,
			long now) {
		Optional<TargetKind> winner = winner(around, best);

		// Nobody to count: nothing to arm for, or a sword picked by hand would be taken away on an
		// empty field. The choice is kept, so the same crowd coming back needs no new second.
		if (winner.isEmpty()) {
			candidate = null;
			return Optional.empty();
		}

		if (chosen != null && sameWeapon(chosen, winner.get(), best)) {
			candidate = null;
			return Optional.of(chosen);
		}

		if (candidate == null || !sameWeapon(candidate, winner.get(), best)) {
			candidate = winner.get();
			candidateSince = now;
		}

		if (now - candidateSince >= HOLD_TICKS) {
			chosen = candidate;
			candidate = null;
		}

		return Optional.ofNullable(chosen);
	}

	/** Forgets everything: nothing has been chosen, nothing is waiting. */
	void reset() {
		chosen = null;
		candidate = null;
	}

	/** One side: how many mobs it has, and its nearest mob, whose kind speaks for it. */
	private record Side(int count, WeaponContext.Target nearest) {
		Side with(WeaponContext.Target mob) {
			return new Side(count + 1, mob.distance() < nearest.distance() ? mob : nearest);
		}
	}

	private static Optional<TargetKind> winner(List<WeaponContext.Target> around,
			Function<TargetKind, Optional<WeaponSlot>> best) {
		Map<Integer, Side> sides = new LinkedHashMap<>();

		for (WeaponContext.Target mob : around) {
			if (!mob.hostile() || mob.player()) {
				continue;
			}

			best.apply(mob.kind()).ifPresent(weapon -> sides.merge(weapon.slot(), new Side(1, mob),
				(side, one) -> side.with(mob)));
		}

		return sides.values().stream()
			.max(Comparator.comparingInt(Side::count)
				.thenComparing(Comparator.comparingDouble((Side side) -> side.nearest().distance()).reversed()))
			.map(side -> side.nearest().kind());
	}

	private static boolean sameWeapon(TargetKind a, TargetKind b, Function<TargetKind, Optional<WeaponSlot>> best) {
		return a.equals(b) || best.apply(a).map(WeaponSlot::slot).equals(best.apply(b).map(WeaponSlot::slot));
	}
}
