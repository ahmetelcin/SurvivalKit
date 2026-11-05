package com.merci.survivalkit.vein;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Finding the blocks that belong to one vein or one tree. */
public final class VeinScan {
	private static final String DEEPSLATE = "deepslate_";

	private VeinScan() {
	}

	/**
	 * Which blocks count as "the same kind". An ore and its deepslate twin are one kind, because a
	 * vein runs straight across the stone/deepslate line; everything else is matched by id.
	 */
	public static String family(String id) {
		int colon = id.indexOf(':');
		String path = id.substring(colon + 1);
		return path.startsWith(DEEPSLATE) ? id.substring(0, colon + 1) + path.substring(DEEPSLATE.length()) : id;
	}

	/**
	 * The blocks joined to {@code start} through blocks that pass {@code member}, nearest first,
	 * corners included. Stops at {@code limit}, start included.
	 */
	public static List<Pos> connected(Pos start, Predicate<Pos> member, int limit) {
		List<Pos> found = new ArrayList<>();
		Set<Pos> seen = new HashSet<>();
		ArrayDeque<Pos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start);

		while (!queue.isEmpty() && found.size() < limit) {
			Pos next = queue.poll();
			found.add(next);

			for (Pos neighbour : next.around()) {
				if (seen.add(neighbour) && member.test(neighbour)) {
					queue.add(neighbour);
				}
			}
		}

		return found;
	}

	/** A pile of logs is a tree when natural foliage touches at least one of them. */
	public static boolean touchesFoliage(List<Pos> logs, Predicate<Pos> foliage) {
		for (Pos log : logs) {
			for (Pos neighbour : log.around()) {
				if (foliage.test(neighbour)) {
					return true;
				}
			}
		}

		return false;
	}
}
