package com.merci.survivalkit.hud;

import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.List;
import java.util.OptionalInt;

/** What each module's HUD box says, worded in one place. */
public final class StatusLines {
	private static final int TICKS_PER_SECOND = 20;

	private StatusLines() {
	}

	/** Totems carried, anywhere; none is a warning. */
	public static List<HudLine> totems(int count) {
		return List.of(new HudLine(Lang.of(Keys.HUD_TOTEMS, count), count == 0));
	}

	/** Oto Silah's fight lock: on with the seconds it has left, rounded up, or off. */
	public static List<HudLine> weaponLock(long ticksLeft) {
		return List.of(HudLine.of(ticksLeft > 0
			? Lang.of(Keys.HUD_WEAPON_LOCKED, (ticksLeft + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND)
			: Lang.of(Keys.HUD_WEAPON_FREE)));
	}

	/** Damar Kırıcı's job while it is being sent; nothing between jobs. */
	public static List<HudLine> vein(OptionalInt progress, int blocks) {
		return progress.isPresent()
			? List.of(HudLine.of(Lang.of(Keys.HUD_VEIN, blocks, progress.getAsInt())))
			: List.of();
	}

	/** Dünya İndirici: refused by the server, downloading, or not, with the chunks saved so far. */
	public static List<HudLine> downloader(boolean refused, boolean downloading, int chunks) {
		if (refused) {
			return List.of(new HudLine(Lang.of(Keys.HUD_WDL_REFUSED), true));
		}

		return List.of(HudLine.of(Lang.of(downloading ? Keys.HUD_WDL_ON : Keys.HUD_WDL_OFF, chunks)));
	}
}
