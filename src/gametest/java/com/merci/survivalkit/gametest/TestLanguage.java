package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.menu.ItemCatalogue;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;

/**
 * Puts the test client into Turkish.
 *
 * <p>Two things need it. The menu is written in Turkish, so a screenshot taken in English is
 * not the screen the player is looking at. And the item picker searches item names — proving
 * that "pismis" finds Pişmiş Biftek means nothing against a client that calls it Cooked Beef.
 *
 * <p>Switching the language means reloading the resource packs, which is slow, so this is
 * done once for the whole client and every later call is a no-op. All the game tests share
 * one client, so whichever runs first pays for it.
 */
public final class TestLanguage {
	private static final String TURKISH = "tr_tr";

	private TestLanguage() {
	}

	public static void ensureTurkish(ClientGameTestContext context) {
		ensure(context, TURKISH);
	}

	/** Any shipped language, so the menu can be read in one it was not written in. */
	public static void ensure(ClientGameTestContext context, String language) {
		boolean already = context.computeOnClient(client -> language.equals(client.getLanguageManager().getSelected()));

		if (already) {
			return;
		}

		context.runOnClient(client -> {
			client.getLanguageManager().setSelected(language);
			client.options.languageCode = language;
			client.options.save();
		});

		// The reload runs off the render thread behind a loading overlay, and that overlay
		// swallows input while it is up. Waiting for the language alone is not enough: the
		// next test would press keys and click at a screen that is not listening yet.
		context.runOnClient(Minecraft::reloadResourcePacks);
		// Names come from the language, so anything cached under the old one is stale.
		ItemCatalogue.forget();
		context.waitFor(client -> language.equals(client.getLanguageManager().getSelected())
				&& client.getLanguageManager().getLanguage(language) != null
				&& client.gui.overlay() == null, 600);
		context.waitTicks(40);

		SurvivalKit.LOGGER.info("[gametest] test istemcisi {} diline alındı", language);
	}
}
