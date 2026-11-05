package com.merci.survivalkit.area;

import com.merci.survivalkit.vein.Pos;
import java.util.List;
import java.util.Optional;

/**
 * The one rule every module that breaks blocks keeps to: with an area in force, only the blocks
 * inside it; without one, everywhere or nowhere as the module's setting says. The area is in
 * force only while Yapı Modu is on and both corners are marked in this place — see
 * {@link BuildMode#area}.
 */
public final class AreaRule {
	private AreaRule() {
	}

	/** Whether this block may be touched. */
	public static boolean allows(Pos pos, Optional<Area> area, WithoutArea without) {
		return area.map(box -> box.contains(pos)).orElse(without == WithoutArea.EVERYWHERE);
	}

	/** Whether anything at all may be touched: false only with no area and "nowhere". */
	public static boolean worksAtAll(Optional<Area> area, WithoutArea without) {
		return area.isPresent() || without == WithoutArea.EVERYWHERE;
	}

	/** The blocks that may be touched, in the order given. */
	public static List<Pos> keep(List<Pos> blocks, Optional<Area> area, WithoutArea without) {
		return blocks.stream().filter(pos -> allows(pos, area, without)).toList();
	}
}
