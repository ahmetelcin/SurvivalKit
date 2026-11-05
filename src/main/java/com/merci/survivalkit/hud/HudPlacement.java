package com.merci.survivalkit.hud;

import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.settings.SettingGroup;

/**
 * Where one panel is and whether it is shown: its own settings, under the panel's heading. The
 * ids carry the panel's id, since every panel's settings sit side by side in the HUD's file; the
 * wording is shared.
 */
public final class HudPlacement {
	/** As far as an offset goes, either way: wider than any screen in GUI units. */
	public static final int MAX_OFFSET = 4000;

	private final BoolSetting shown;
	private final EnumSetting<Anchor> anchor;
	private final IntSetting offsetX;
	private final IntSetting offsetY;
	private final IntSetting scale;

	public HudPlacement(SettingGroup section, String panel, Anchor anchor, int offsetX, int offsetY) {
		this.shown = section.add(new BoolSetting(panel + "_shown", "hud.shown", true));
		this.anchor = section.add(new EnumSetting<>(panel + "_anchor", "hud.anchor", anchor));
		this.offsetX = section.add(new IntSetting(panel + "_x", "hud.offset_x", offsetX, -MAX_OFFSET, MAX_OFFSET));
		this.offsetY = section.add(new IntSetting(panel + "_y", "hud.offset_y", offsetY, -MAX_OFFSET, MAX_OFFSET));
		this.scale = section.add(new IntSetting(panel + "_scale", "hud.scale", 100, 50, 300));
	}

	public boolean shown() {
		return shown.get();
	}

	public Anchor anchor() {
		return anchor.get();
	}

	public int offsetX() {
		return offsetX.get();
	}

	public int offsetY() {
		return offsetY.get();
	}

	/** In hundredths: 100 is the game's own text size. */
	public int scale() {
		return scale.get();
	}

	/** Where the HUD editor dropped it. */
	public void move(Anchor anchor, int offsetX, int offsetY) {
		this.anchor.set(anchor);
		this.offsetX.set(offsetX);
		this.offsetY.set(offsetY);
	}

	/** Back where it started: corner, offsets and size. */
	public void reset() {
		anchor.reset();
		offsetX.reset();
		offsetY.reset();
		scale.reset();
	}
}
