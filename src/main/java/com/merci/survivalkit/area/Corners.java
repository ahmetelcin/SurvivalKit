package com.merci.survivalkit.area;

import com.merci.survivalkit.vein.Pos;
import java.util.Optional;

/**
 * What the wand has marked so far: either corner may be missing. It is an area once both are there.
 *
 * @param first  the left click's corner, or null
 * @param second the right click's corner, or null
 */
public record Corners(Pos first, Pos second) {
	public static final Corners NONE = new Corners(null, null);

	public Corners with(Corner which, Pos pos) {
		return which == Corner.FIRST ? new Corners(pos, second) : new Corners(first, pos);
	}

	public Pos get(Corner which) {
		return which == Corner.FIRST ? first : second;
	}

	public Optional<Area> area() {
		return first == null || second == null ? Optional.empty() : Optional.of(Area.between(first, second));
	}

	public boolean isEmpty() {
		return first == null && second == null;
	}

	public enum Corner {
		FIRST,
		SECOND;

		/** As the player counts them: 1 and 2. */
		public int number() {
			return ordinal() + 1;
		}
	}
}
