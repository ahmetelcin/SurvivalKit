package com.merci.survivalkit.gametest;

import com.merci.survivalkit.menu.SurvivalKitScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Wheels the settings panel until a point is inside its visible band, the way a player
 * would. A row outside the band refuses clicks, so a test that clicks a row a new setting
 * has pushed further down has to scroll to it first rather than count on where it used to be.
 */
public final class MenuScroll {
	/** Half a list line: the point has to be clear of the edge, not on it. */
	private static final int MARGIN = 6;

	private MenuScroll() {
	}

	/** The "+ Add" line under a list setting. */
	public static void bringAddLineIntoView(ClientGameTestContext context, String settingId) {
		for (int attempt = 0; attempt < 60; attempt++) {
			int y = context.computeOnClient(client -> menu(client).addLineY(settingId)
					.orElseThrow(() -> new AssertionError("+ Ekle satırı ekranda değil: " + settingId)));
			int top = context.computeOnClient(client -> menu(client).settingsViewTop());
			int bottom = context.computeOnClient(client -> menu(client).settingsViewBottom());

			if (y - MARGIN >= top && y + MARGIN <= bottom) {
				return;
			}

			double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
			int x = context.computeOnClient(client -> menu(client).controlToggleCenterX());
			context.getInput().setCursorPos(x * scale, (top + bottom) / 2.0 * scale);
			context.getInput().scroll(y < top ? 1 : -1);
			context.waitTick();
		}

		throw new AssertionError("+ Ekle satırı görünür alana kaydırılamadı: " + settingId);
	}

	private static SurvivalKitScreen menu(net.minecraft.client.Minecraft client) {
		if (!(client.gui.screen() instanceof SurvivalKitScreen menu)) {
			throw new AssertionError("menü açık olmalıydı");
		}

		return menu;
	}
}
