package com.merci.survivalkit.hud;

import java.util.List;

/** A module's box: whatever it has to say. */
public final class SourcePanel implements HudPanel {
	private final HudSource source;
	private final HudPlacement placement;

	public SourcePanel(HudSource source, HudPlacement placement) {
		this.source = source;
		this.placement = placement;
	}

	@Override
	public String id() {
		return source.id();
	}

	@Override
	public HudPlacement placement() {
		return placement;
	}

	@Override
	public List<HudLine> lines() {
		return source.hudLines();
	}
}
