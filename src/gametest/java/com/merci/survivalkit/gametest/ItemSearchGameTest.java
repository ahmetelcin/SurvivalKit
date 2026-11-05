package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.menu.ItemChoice;
import com.merci.survivalkit.menu.ItemPickerScreen;
import com.merci.survivalkit.menu.SurvivalKitScreen;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.RegistryListSetting;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

/**
 * What the picker's search box has to find, in whichever language the game is in.
 *
 * <p>Two ways in, and the difference between them matters. The name is whatever the game
 * calls the item right now, so it changes with the language — and it is often not the id
 * spelled out: minecraft:cooked_beef is "Steak" in English and "Biftek" in Turkish, and
 * neither contains "cooked". The id never changes, so it is the one thing a player can type
 * that works everywhere, including the ids they copied out of someone else's config.
 *
 * <p>So: the id finds the item in every shipped language, the name finds it only in the
 * language that uses that name, and neither is allowed to match by accident.
 */
public class ItemSearchGameTest implements FabricClientGameTest {
	private static final InputConstants.Key MENU_KEY =
		InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT);
	private static final InputConstants.Key ESCAPE = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_ESCAPE);

	private static final String STEAK = "minecraft:cooked_beef";
	/** The id a player types, without the namespace, exactly as it is written in configs. */
	private static final String STEAK_ID_TYPED = "cooked_beef";

	/** The Turkish name, which an English client must not match. */
	private static final String TURKISH_NAME = "Biftek";
	/** The English name, which a Turkish client must not match. */
	private static final String ENGLISH_NAME = "Steak";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			priority().set(List.of());

			theIdFindsItInEveryLanguage(context);

			// Left in Turkish, which is what the rest of the suite expects.
			TestLanguage.ensureTurkish(context);

			SurvivalKit.LOGGER.info("[gametest] item araması doğrulandı (\"{}\" hem tr_tr hem en_us'te {} buldu; her dil kendi adını buldu, ötekininkini bulmadı)",
					STEAK_ID_TYPED, STEAK);
		}
	}

	private static void theIdFindsItInEveryLanguage(ClientGameTestContext context) {
		for (String language : List.of("tr_tr", "en_us")) {
			TestLanguage.ensure(context, language);
			context.waitTicks(5);

			openPicker(context);

			String gameName = nameOf(context, STEAK);
			String ownName = language.equals("tr_tr") ? TURKISH_NAME : ENGLISH_NAME;
			String otherName = language.equals("tr_tr") ? ENGLISH_NAME : TURKISH_NAME;

			if (!gameName.equals(ownName)) {
				throw new AssertionError(language + " için oyunun adı \"" + ownName
						+ "\" olmalıydı, bulunan: " + gameName);
			}

			// The id: the point of the whole test.
			mustFind(context, language, STEAK_ID_TYPED, STEAK);

			// The name the game uses in this language finds it too.
			mustFind(context, language, ownName, STEAK);

			// Negative control: the other language's name is not this language's name, so it
			// must find nothing. Without this, a search that quietly matched everything
			// would pass every assertion above.
			mustNotFind(context, language, otherName, STEAK);

			// Negative control: an id that does not exist finds nothing, so "contains" is
			// not being read as "starts with something like it".
			mustNotFind(context, language, STEAK_ID_TYPED + "zz", STEAK);

			closePicker(context);
		}
	}

	private static void mustFind(ClientGameTestContext context, String language, String query, String wantedId) {
		type(context, query);
		List<String> found = visibleIds(context);

		if (!found.contains(wantedId)) {
			throw new AssertionError(language + " istemcide \"" + query + "\" araması " + wantedId
					+ " getirmeliydi, bulunan " + found.size() + " sonuç: " + firstFew(found));
		}
	}

	private static void mustNotFind(ClientGameTestContext context, String language, String query, String unwantedId) {
		type(context, query);
		List<String> found = visibleIds(context);

		if (found.contains(unwantedId)) {
			throw new AssertionError(language + " istemcide \"" + query + "\" araması " + unwantedId
					+ " getirmemeliydi, bulunanlar: " + firstFew(found));
		}
	}

	private static String firstFew(List<String> ids) {
		return ids.size() <= 5 ? ids.toString() : ids.subList(0, 5) + "…";
	}

	// --- plumbing ---

	private static void openPicker(ClientGameTestContext context) {
		boolean open = context.computeOnClient(client -> client.gui.screen() instanceof SurvivalKitScreen);

		if (!open) {
			context.getInput().pressKey(MENU_KEY);
			context.waitTicks(5);
		}

		Module autoEat = SurvivalKit.modules().byId("autoeat")
				.orElseThrow(() -> new AssertionError("modül kayıtlı değil: autoeat"));
		int y = screen(context).rowCenterY(autoEat).orElseThrow(() -> new AssertionError("modül listede yok"));
		clickAt(context, screen(context).nameCenterX(), y);

		MenuScroll.bringAddLineIntoView(context, "priority");
		context.runOnClient(ItemSearchGameTest::clickAddLine);
		context.waitTicks(3);

		if (picker(context) == null) {
			throw new AssertionError("+ Ekle seçiciyi açmalıydı");
		}
	}

	private static void clickAddLine(Minecraft client) {
		SurvivalKitScreen menu = (SurvivalKitScreen) client.gui.screen();
		int y = menu.addLineY("priority").orElseThrow(() -> new AssertionError("+ Ekle satırı ekranda değil"));
		menu.clickFor(menu.listEntryX("priority"), y);
	}

	private static void closePicker(ClientGameTestContext context) {
		context.getInput().pressKey(ESCAPE);
		context.waitTicks(3);
		context.getInput().pressKey(ESCAPE);
		context.waitTicks(3);
		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);
	}

	private static void type(ClientGameTestContext context, String text) {
		context.runOnClient(client -> picker(context).searchBox().setValue(""));
		context.getInput().typeChars(text);
		context.waitTicks(3);
	}

	private static List<String> visibleIds(ClientGameTestContext context) {
		return context.computeOnClient(client -> picker(context).model().visible().stream()
				.map(ItemChoice::id)
				.toList());
	}

	private static String nameOf(ClientGameTestContext context, String id) {
		return context.computeOnClient(client -> picker(context).model().all().stream()
				.filter(choice -> choice.id().equals(id))
				.map(ItemChoice::displayName)
				.findFirst()
				.orElseThrow(() -> new AssertionError("seçici bunu sunmalıydı: " + id)));
	}

	private static void clickAt(ClientGameTestContext context, int guiX, int guiY) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());

		context.getInput().setCursorPos(guiX * scale, guiY * scale);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static SurvivalKitScreen screen(ClientGameTestContext context) {
		SurvivalKitScreen menu = context.computeOnClient(
				client -> client.gui.screen() instanceof SurvivalKitScreen open ? open : null);

		if (menu == null) {
			throw new AssertionError("menü açık olmalıydı");
		}

		return menu;
	}

	private static ItemPickerScreen picker(ClientGameTestContext context) {
		return context.computeOnClient(
				client -> client.gui.screen() instanceof ItemPickerScreen open ? open : null);
	}

	private static RegistryListSetting priority() {
		return SurvivalKit.modules().byId("autoeat").orElseThrow()
				.settings().<RegistryListSetting>byId("priority").orElseThrow();
	}
}
