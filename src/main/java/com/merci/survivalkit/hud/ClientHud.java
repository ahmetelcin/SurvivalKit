package com.merci.survivalkit.hud;

import com.merci.survivalkit.module.impl.HudModule;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws the HUD's panels, as a layer of the game's own HUD. Nothing at all with the HUD hidden
 * (F1) or the debug screen up (F3), whatever the panels say — nor while the HUD editor is open,
 * which draws them itself.
 */
public final class ClientHud {
	private final HudModule hud;
	/** Where each panel was drawn last frame, in GUI units; the editor and the tests read it. */
	private final Map<String, HudBox> drawn = new HashMap<>();
	/** Whether a frame was drawn since the last tick: the game skips the HUD's layers entirely under F1. */
	private boolean renderedSinceTick;

	public ClientHud(HudModule hud) {
		this.hud = hud;
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		drawn.clear();
		renderedSinceTick = true;
		Minecraft minecraft = Minecraft.getInstance();

		if (!visible(minecraft)) {
			return;
		}

		for (HudPanel panel : hud.panels()) {
			List<HudLine> lines = panel.lines();

			if (panel.placement().shown() && !lines.isEmpty()) {
				HudBox box = HudDrawing.place(minecraft.font, panel.placement(), lines, graphics.guiWidth(), graphics.guiHeight());
				drawn.put(panel.id(), box);
				HudDrawing.draw(graphics, minecraft.font, panel.placement(), lines, box, panel.placement().anchor().horizontal() > 0);
			}
		}
	}

	/**
	 * The HUD module on, no debug screen over it and its editor not open. F1 needs no check here:
	 * the game leaves the HUD's layers out altogether while its HUD is hidden (the F1 gametest, and
	 * its mutation, show it).
	 */
	public boolean visible(Minecraft minecraft) {
		return hud.isEnabled() && minecraft.player != null
			&& !minecraft.gui.hud.getDebugOverlay().showDebugScreen()
			&& !(minecraft.gui.screen() instanceof HudEditorScreen);
	}

	/**
	 * At the end of every client tick: with no frame of the HUD since the last one — hidden with F1,
	 * which leaves its layers out altogether — nothing is on the screen any more.
	 */
	public void tickEnd() {
		if (!renderedSinceTick) {
			drawn.clear();
		}

		renderedSinceTick = false;
	}

	/** Where a panel was drawn last frame, or null if it was not. */
	public HudBox drawn(String panel) {
		return drawn.get(panel);
	}
}
