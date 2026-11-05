package com.merci.survivalkit.area;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.vein.Pos;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AreaTest {
	@Test
	void anyTwoOppositeCornersMakeTheSameBox() {
		Area area = Area.between(new Pos(5, 70, -2), new Pos(1, 64, 3));

		assertEquals(new Pos(1, 64, -2), area.min());
		assertEquals(new Pos(5, 70, 3), area.max());
		assertEquals(area, Area.between(new Pos(1, 64, 3), new Pos(5, 70, -2)));
	}

	@Test
	void bothCornersAreInsideAndTheVolumeCountsThem() {
		Area area = Area.between(new Pos(0, 0, 0), new Pos(4, 2, 6));

		assertTrue(area.contains(new Pos(0, 0, 0)));
		assertTrue(area.contains(new Pos(4, 2, 6)));
		assertFalse(area.contains(new Pos(5, 2, 6)));
		assertFalse(area.contains(new Pos(4, -1, 6)));
		assertEquals(5 * 3 * 7, area.volume());
	}

	@Test
	void oneBlockIsAnAreaOfOne() {
		assertEquals(1, Area.between(new Pos(3, 3, 3), new Pos(3, 3, 3)).volume());
	}

	@Test
	void aHugeBoxDoesNotOverflow() {
		Area area = Area.between(new Pos(-30_000_000, -64, -30_000_000), new Pos(30_000_000, 319, 30_000_000));

		assertTrue(area.volume() > Integer.MAX_VALUE);
	}

	@Test
	void cornersAreAnAreaOnlyOnceBothAreMarked() {
		Corners first = Corners.NONE.with(Corners.Corner.FIRST, new Pos(1, 2, 3));

		assertEquals(Optional.empty(), first.area());
		assertEquals(Optional.of(Area.between(new Pos(1, 2, 3), new Pos(4, 5, 6))),
			first.with(Corners.Corner.SECOND, new Pos(4, 5, 6)).area());
	}

	@Test
	void theRuleKeepsTheInsideWithAnAreaAndFollowsTheSettingWithout() {
		List<Pos> blocks = List.of(new Pos(0, 0, 0), new Pos(5, 0, 0), new Pos(1, 0, 0));
		Optional<Area> area = Optional.of(Area.between(new Pos(0, 0, 0), new Pos(2, 0, 0)));

		assertEquals(List.of(new Pos(0, 0, 0), new Pos(1, 0, 0)), AreaRule.keep(blocks, area, WithoutArea.NOWHERE));
		assertEquals(List.of(new Pos(0, 0, 0), new Pos(1, 0, 0)), AreaRule.keep(blocks, area, WithoutArea.EVERYWHERE));
		assertEquals(blocks, AreaRule.keep(blocks, Optional.empty(), WithoutArea.EVERYWHERE));
		assertEquals(List.of(), AreaRule.keep(blocks, Optional.empty(), WithoutArea.NOWHERE));
		assertTrue(AreaRule.worksAtAll(area, WithoutArea.NOWHERE));
		assertFalse(AreaRule.worksAtAll(Optional.empty(), WithoutArea.NOWHERE));
	}
}
