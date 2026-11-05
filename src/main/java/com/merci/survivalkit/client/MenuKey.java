package com.merci.survivalkit.client;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.menu.MenuModel;
import com.merci.survivalkit.menu.SurvivalKitScreen;
import com.merci.survivalkit.preset.Presets;
import com.merci.survivalkit.profile.Profiles;
import com.mojang.blaze3d.platform.InputConstants;
import java.text.Collator;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * The key that opens the menu, and the only one this mod registers with the game.
 *
 * <p>Module keys are polled rather than registered, so the game cannot show or rebind them.
 * This one is deliberately different: it appears in Controls under SurvivalKit, which makes
 * it the way back in when a module key clashes with something or is simply forgotten. If it
 * were polled like the rest, a broken config could leave the menu unreachable.
 */
public final class MenuKey {
	public static final String TRANSLATION_KEY = "key.survivalkit.menu";
	/** Shows up in Controls as its own heading; the label comes from key.category.survivalkit.main. */
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(SurvivalKit.id("main"));

	private final KeyMapping mapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
		TRANSLATION_KEY,
		InputConstants.Type.KEYBOARD,
		InputConstants.KEY_RSHIFT,
		CATEGORY));

	private final MenuModel model;
	private final Runnable onChange;
	private final UnaryOperator<Screen> hudEditor;
	private final Supplier<Profiles> profiles;
	private final Supplier<Presets> presets;

	/**
	 * @param hudEditor the HUD editor, opened over the menu it is given and going back to it
	 * @param profiles  the profiles, for the bar above the menu
	 * @param presets   the presets, for the strip at the top of each module's settings
	 */
	public MenuKey(MenuModel model, Runnable onChange, UnaryOperator<Screen> hudEditor, Supplier<Profiles> profiles, Supplier<Presets> presets) {
		this.model = model;
		this.onChange = onChange;
		this.hudEditor = hudEditor;
		this.profiles = profiles;
		this.presets = presets;
	}

	/** Opens the menu on a fresh press, and only while no screen is in the way. */
	public void poll(Minecraft client) {
		boolean pressed = false;

		while (mapping.consumeClick()) {
			pressed = true;
		}

		if (pressed && client.gui.screen() == null) {
			// The player may have changed language since the model was built, and the order
			// of the module list depends on it.
			model.resort(collatorFor(client));

			SurvivalKitScreen screen = new SurvivalKitScreen(model);
			screen.onChange(onChange);
			screen.keyUses(() -> KeyRegistry.current(client));
			screen.hudEditor(hudEditor);
			screen.profiles(profiles);
			screen.presets(presets);
			client.gui.setScreen(screen);
		}
	}

	/**
	 * The game names its language "tr_tr"; a {@link Collator} wants the two halves apart.
	 * Sorting through one is what keeps Ç, Ş and Ü beside C, S and U rather than after Z.
	 */
	private static Collator collatorFor(Minecraft client) {
		String code = client.getLanguageManager().getSelected();
		int underscore = code.indexOf('_');

		Locale locale = underscore < 0
			? Locale.of(code)
			: Locale.of(code.substring(0, underscore), code.substring(underscore + 1).toUpperCase(Locale.ROOT));

		return Collator.getInstance(locale);
	}

	public KeyMapping mapping() {
		return mapping;
	}
}
