package com.merci.survivalkit.scaffold;

import com.merci.survivalkit.vein.Pos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Where to click to fill a block: which neighbour, which of its faces, which point.
 *
 * <p>The neighbour underneath comes first — a block put on top of another is the ordinary way to
 * build a floor. Then the four beside it, nearest face first; the one above last. A neighbour
 * counts only if a click on it places a block (the world says which, see {@link Supports}), the
 * support setting allows it, and the middle of its face is within reach of the eyes. The support
 * setting limits only where a bridge starts: "only the listed" with nothing listed is no limit,
 * and a block İskele put down itself always counts, so a bridge goes on along itself.
 */
public final class PlacementRule {
	/** What the world has to say about a block, for this rule. */
	public interface Supports {
		/**
		 * The block's id if a click on it puts a block beside it: something solid that is not
		 * replaced by what is placed and does nothing of its own when used — not a chest, a door,
		 * a lever. Empty for anything else.
		 */
		Optional<String> clickable(Pos pos);
	}

	private static final List<Face> BESIDE = List.of(Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST);

	private PlacementRule() {
	}

	/**
	 * @param target   the empty block to fill
	 * @param eyes     where the player's eyes are
	 * @param reach    how far the player can use a block, in blocks from the eyes
	 * @param mode     whether any block will do as a support or only the listed ones
	 * @param listed   the support list, read only with {@link SupportMode#LISTED}; empty, no limit
	 * @param laid     whether a block was put down by İskele itself, a support whatever the list says
	 */
	public static Optional<Placement> find(Pos target, Supports world, Vec eyes, double reach, SupportMode mode, List<String> listed,
		Predicate<Pos> laid) {
		List<Placement> beside = new ArrayList<>();

		for (Face side : BESIDE) {
			placement(target, side, world, mode, listed, laid).ifPresent(beside::add);
		}

		// Stable: equal distances keep the fixed order above, so one world always gives one answer.
		beside.sort(Comparator.comparingDouble(placement -> placement.hit().distanceTo(eyes)));

		List<Placement> ordered = new ArrayList<>();
		placement(target, Face.DOWN, world, mode, listed, laid).ifPresent(ordered::add);
		ordered.addAll(beside);
		placement(target, Face.UP, world, mode, listed, laid).ifPresent(ordered::add);

		return ordered.stream().filter(placement -> placement.hit().distanceTo(eyes) <= reach).findFirst();
	}

	/** The click on the neighbour on {@code side} of the target, on the face that looks back at it. */
	private static Optional<Placement> placement(Pos target, Face side, Supports world, SupportMode mode, List<String> listed,
		Predicate<Pos> laid) {
		Pos support = side.from(target);
		Optional<String> id = world.clickable(support);

		if (id.isEmpty() || !allowed(id.get(), support, mode, listed, laid)) {
			return Optional.empty();
		}

		Face face = side.opposite();
		Vec hit = new Vec(
			support.x() + 0.5 + face.dx() * 0.5,
			support.y() + 0.5 + face.dy() * 0.5,
			support.z() + 0.5 + face.dz() * 0.5);
		return Optional.of(new Placement(target, support, face, hit));
	}

	/** Whether the support setting lets {@code support} be built against. */
	private static boolean allowed(String id, Pos support, SupportMode mode, List<String> listed, Predicate<Pos> laid) {
		return mode == SupportMode.ALL || listed.isEmpty() || listed.contains(id) || laid.test(support);
	}
}
