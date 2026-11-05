package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.menu.KeyConflicts;
import com.merci.survivalkit.menu.KeyNames;
import com.merci.survivalkit.menu.SurvivalKitScreen;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.KeybindSetting;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/**
 * The keybind widget: click it, press something, and the setting takes it. Esc backs out,
 * Delete unbinds, and a key the game already uses is called out by name.
 */
public class MenuKeybindGameTest implements FabricClientGameTest {
	private static final InputConstants.Key MENU_KEY =
		InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT);
	private static final InputConstants.Key KEY_L = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_L);
	private static final InputConstants.Key KEY_J = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_J);
	private static final InputConstants.Key ESCAPE = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_ESCAPE);
	private static final InputConstants.Key DELETE = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_DELETE);

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestLanguage.ensureTurkish(context);
			context.waitTicks(5);
			openMenuOn(context, "autoeat");

			String original = keybind().get();

			aFreeKeyIsTakenAndShownInWords(context);
			bindingAKeyLeavesNothingInTheSearchBox(context);
			escapeBacksOutAndLeavesTheKeyAlone(context);
			deleteUnbindsIt(context);
			String warning = aClashIsNamed(context);

			context.takeScreenshot("menu-tus-cakisma");

			keybind().set(original);
			context.getInput().pressKey(MENU_KEY);
			context.waitTicks(5);

			SurvivalKit.LOGGER.info("[gametest] tuş widget'ı doğrulandı (atama, Esc iptal, Delete kaldırma, uyarı: \"{}\")", warning);
		}
	}

	private static void aFreeKeyIsTakenAndShownInWords(ClientGameTestContext context) {
		clickKeybind(context);
		context.getInput().pressKey(KEY_J);
		context.waitTicks(3);

		if (!"key.keyboard.j".equals(keybind().get())) {
			throw new AssertionError("J atanmalıydı, bulunan " + keybind().get());
		}

		// The letter itself comes from the keyboard layout, so what is checked is that a
		// readable name replaced the stored one rather than what that name happens to be.
		String shown = context.computeOnClient(client -> readable(keybind().get()));

		if (shown.isBlank() || shown.equals(keybind().get()) || shown.length() > 3) {
			throw new AssertionError("tuş adı okunur görünmeliydi, bulunan \"" + shown + "\"");
		}
	}

	/**
	 * A keyboard sends the letter alongside the key, and the search box is sitting right
	 * there with the keyboard: binding J must not also leave a "j" in the box and send the
	 * module list searching. {@link ClientGameTestContext#getInput()} keeps the two apart,
	 * so the character has to be sent by hand for this to be the real thing.
	 */
	private static void bindingAKeyLeavesNothingInTheSearchBox(ClientGameTestContext context) {
		clickKeybind(context);
		context.getInput().pressKey(KEY_J);
		context.getInput().typeChar('j');
		context.waitTicks(3);

		if (!"key.keyboard.j".equals(keybind().get())) {
			throw new AssertionError("J atanmalıydı, bulunan " + keybind().get());
		}

		String typed = context.computeOnClient(client -> screen(context).searchBox().getValue());

		if (!typed.isEmpty()) {
			throw new AssertionError("tuş atarken harf arama kutusuna düşmemeliydi, kutuda: \"" + typed + "\"");
		}
	}

	private static void escapeBacksOutAndLeavesTheKeyAlone(ClientGameTestContext context) {
		String before = keybind().get();

		clickKeybind(context);
		context.getInput().pressKey(ESCAPE);
		context.waitTicks(3);

		if (!before.equals(keybind().get())) {
			throw new AssertionError("Esc atamayı iptal etmeliydi, bulunan " + keybind().get());
		}

		if (screen(context) == null) {
			throw new AssertionError("Esc menüyü kapatmamalıydı, yalnızca dinlemeyi bırakmalıydı");
		}
	}

	private static void deleteUnbindsIt(ClientGameTestContext context) {
		clickKeybind(context);
		context.getInput().pressKey(DELETE);
		context.waitTicks(3);

		if (!KeybindSetting.UNBOUND.equals(keybind().get())) {
			throw new AssertionError("Delete tuşu kaldırmalıydı, bulunan " + keybind().get());
		}

		String unbound = context.computeOnClient(client -> KeyNames.unbound());

		if (!unbound.equals(context.computeOnClient(client -> readable(keybind().get())))) {
			throw new AssertionError("atanmamış tuş \"" + unbound + "\" olarak görünmeliydi");
		}
	}

	/** L is vanilla's advancements key, so binding it has to say so. */
	private static String aClashIsNamed(ClientGameTestContext context) {
		clickKeybind(context);
		context.getInput().pressKey(KEY_L);
		context.waitTicks(3);

		if (!"key.keyboard.l".equals(keybind().get())) {
			throw new AssertionError("L atanmalıydı, bulunan " + keybind().get());
		}

		String expected = context.computeOnClient(client -> Component.translatable(advancementsKey(client)).getString());
		String warning = context.computeOnClient(client -> KeyConflicts
				.warningFor(keybind().get(), "autoeat", com.merci.survivalkit.client.KeyRegistry.current(client))
				.orElse(null));

		if (warning == null) {
			throw new AssertionError("L için çakışma uyarısı çıkmalıydı");
		}

		if (!warning.equals(expected + " ile çakışıyor")) {
			throw new AssertionError("uyarı çakışan eylemi adıyla anmalıydı, beklenen \""
					+ expected + " ile çakışıyor\", bulunan \"" + warning + "\"");
		}

		return warning;
	}

	/** The translation key of whatever vanilla currently has on L. */
	private static String advancementsKey(Minecraft client) {
		for (KeyMapping mapping : client.options.keyMappings) {
			if ("key.advancements".equals(mapping.getName())) {
				return mapping.getName();
			}
		}

		throw new AssertionError("vanilla'da key.advancements bulunamadı");
	}

	// --- plumbing ---

	private static String readable(String keyName) {
		return KeyNames.readable(keyName, SurvivalKitScreen::fromLayout);
	}

	private static KeybindSetting keybind() {
		return module("autoeat").keybind();
	}

	private static void clickKeybind(ClientGameTestContext context) {
		int y = screen(context).settingCenterY("key")
				.orElseThrow(() -> new AssertionError("tuş satırı ekranda değil"));
		clickAt(context, screen(context).controlToggleCenterX(), y);
	}

	private static void openMenuOn(ClientGameTestContext context, String moduleId) {
		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);

		Module module = module(moduleId);
		int y = screen(context).rowCenterY(module).orElseThrow(() -> new AssertionError("modül listede yok: " + moduleId));
		clickAt(context, screen(context).nameCenterX(), y);
	}

	private static void clickAt(ClientGameTestContext context, int guiX, int guiY) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());

		context.getInput().setCursorPos(guiX * scale, guiY * scale);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static SurvivalKitScreen screen(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.gui.screen() instanceof SurvivalKitScreen open ? open : null);
	}

	private static Module module(String id) {
		return SurvivalKit.modules().byId(id).orElseThrow(() -> new AssertionError("modül kayıtlı değil: " + id));
	}
}
