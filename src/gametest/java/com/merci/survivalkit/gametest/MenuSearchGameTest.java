package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.menu.ItemPickerScreen;
import com.merci.survivalkit.menu.SearchHit;
import com.merci.survivalkit.menu.SurvivalKitScreen;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.BoolSetting;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The menu key and the search box, which share Right Shift between them.
 *
 * <p>Typing goes to the search box without anyone clicking it, a capital letter typed with
 * the menu key held leaves the menu standing — in the module search and in the item picker
 * both — and Escape empties the box before it gives up the screen.
 */
public class MenuSearchGameTest implements FabricClientGameTest {
	private static final InputConstants.Key MENU_KEY =
		InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT);
	private static final InputConstants.Key ESCAPE =
		InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_ESCAPE);
	/** Comfortably longer than the highlight is meant to last. */
	private static final int HIGHLIGHT_TICKS = 60;

	private static final InputConstants.Key LETTER_D =
		InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_D);

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestLanguage.ensureTurkish(context);
			context.waitTicks(5);

			openMenu(context);

			theMenuOpensWithTheKeyboardFree(context);
			typingGoesStraightToTheSearchBox(context);
			escapeEmptiesTheSearchBeforeItClosesAnything(context);
			aCapitalTypedWithTheMenuKeyLeavesTheMenuStanding(context);
			context.takeScreenshot("menu-arama");

			theSameIsTrueInTheItemPicker(context);
			aSettingResultJumpsToTheSettingAndLightsItBriefly(context);
			aLoneTapOfTheMenuKeyCloses(context);

			SurvivalKit.LOGGER.info("[gametest] menü araması doğrulandı (tıklamadan yazma, Right Shift ile büyük harf menüyü ve seçiciyi kapatmıyor, Esc önce metni siliyor, \"esik\" iki eşik ayarını buluyor ve sonuca tıklayınca panel oraya kayıp kısa süre vurguluyor)");
		}
	}

	/**
	 * The box earns the keyboard by being typed into; it does not hold it from the moment
	 * the menu opens. Holding it is what made every key a letter — a key being bound landed
	 * in the box as well, and there was no way to put the cursor down.
	 */
	private static void theMenuOpensWithTheKeyboardFree(ClientGameTestContext context) {
		if (context.computeOnClient(client -> screen(context).searchBox().isFocused())) {
			throw new AssertionError("menü açılır açılmaz imleç arama kutusunda olmamalıydı");
		}
	}

	/** Nobody clicked the box: the menu opens ready to be typed into. */
	private static void typingGoesStraightToTheSearchBox(ClientGameTestContext context) {
		context.getInput().typeChars("dunya");
		context.waitTicks(3);

		String typed = context.computeOnClient(client -> screen(context).searchBox().getValue());

		if (!"dunya".equals(typed)) {
			throw new AssertionError("yazılan arama kutusuna gitmeliydi, kutuda: \"" + typed + "\"");
		}

		if (!context.computeOnClient(client -> screen(context).searchBox().isFocused())) {
			throw new AssertionError("yazılınca kutu klavyeyi almalıydı (silme tuşu da oraya gitmeli)");
		}

		List<String> listed = visibleModuleIds(context);

		// The HUD comes up too: its box for Dünya İndirici is a heading of that name.
		if (!listed.equals(List.of("hud", "wdl"))) {
			throw new AssertionError("arama modül listesini süzmeliydi, listelenen: " + listed);
		}
	}

	/** One Escape for the text, a second one for the screen. */
	private static void escapeEmptiesTheSearchBeforeItClosesAnything(ClientGameTestContext context) {
		context.getInput().pressKey(ESCAPE);
		context.waitTicks(3);

		if (screen(context) == null) {
			throw new AssertionError("ilk Esc yalnızca aramayı silmeliydi, menü açık kalmalıydı");
		}

		String left = context.computeOnClient(client -> screen(context).searchBox().getValue());

		if (!left.isEmpty()) {
			throw new AssertionError("ilk Esc arama metnini silmeliydi, kalan: \"" + left + "\"");
		}

		// Emptying the box is also the way out of it: the cursor is put down with the text.
		if (context.computeOnClient(client -> screen(context).searchBox().isFocused())) {
			throw new AssertionError("ilk Esc imleci de kutudan çıkarmalıydı");
		}

		context.getInput().pressKey(ESCAPE);
		context.waitTicks(3);

		if (screen(context) != null) {
			throw new AssertionError("boş kutuda ikinci Esc menüyü kapatmalıydı");
		}

		openMenu(context);
	}

	/** The whole point: Right Shift held is a capital letter, not a way out. */
	private static void aCapitalTypedWithTheMenuKeyLeavesTheMenuStanding(ClientGameTestContext context) {
		typeCapitalWithMenuKey(context, "D");

		if (screen(context) == null) {
			throw new AssertionError("menü tuşuyla büyük harf yazmak menüyü kapatmamalıydı");
		}

		String typed = context.computeOnClient(client -> screen(context).searchBox().getValue());

		if (!"D".equals(typed)) {
			throw new AssertionError("büyük harf kutuya girmeliydi, kutuda: \"" + typed + "\"");
		}

		clearSearch(context);
	}

	/** The picker has a search box of its own, and the same key still types into it. */
	private static void theSameIsTrueInTheItemPicker(ClientGameTestContext context) {
		selectModule(context, "autoeat");
		openPicker(context);

		typeCapitalWithMenuKey(context, "D");

		if (picker(context) == null) {
			throw new AssertionError("menü tuşuyla büyük harf yazmak seçiciyi kapatmamalıydı");
		}

		String typed = context.computeOnClient(client -> picker(context).searchBox().getValue());

		if (!"D".equals(typed)) {
			throw new AssertionError("büyük harf seçicinin kutusuna girmeliydi, kutuda: \"" + typed + "\"");
		}

		context.getInput().pressKey(ESCAPE);
		context.waitTicks(3);

		if (picker(context) == null) {
			throw new AssertionError("seçicide ilk Esc yalnızca aramayı silmeliydi");
		}

		context.getInput().pressKey(ESCAPE);
		context.waitTicks(3);

		if (screen(context) == null) {
			throw new AssertionError("seçicide ikinci Esc menüye dönmeliydi");
		}
	}

	/** Pressed and released with nothing in between, it is still the way out. */
	private static void aLoneTapOfTheMenuKeyCloses(ClientGameTestContext context) {
		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);

		if (screen(context) != null) {
			throw new AssertionError("menü tuşuna tek basış menüyü kapatmalıydı");
		}
	}

	/**
	 * The point of searching: a setting is found by name, wherever it lives, and opening the
	 * result puts it on screen among its neighbours with something to draw the eye.
	 */
	private static void aSettingResultJumpsToTheSettingAndLightsItBriefly(ClientGameTestContext context) {
		// The can eşiği only exists while the golden apple rule is on, and it is the one worth
		// jumping to: it sits below the fold, where the panel has to move to show it.
		goldenAppleRule(context, true);

		context.getInput().typeChars("esik");
		context.waitTicks(3);

		List<String> found = resultIds(context);

		if (!found.contains("threshold") || !found.contains("health_threshold")) {
			throw new AssertionError("\"esik\" her iki eşik ayarını da sonuçlara koymalıydı, bulunanlar: " + found);
		}

		int resultY = context.computeOnClient(client -> screen(context).resultCenterY("health_threshold")
				.orElseThrow(() -> new AssertionError("sonuç satırı ekranda değil")));
		clickAt(context, context.computeOnClient(client -> screen(context).resultX()), resultY);

		String selected = context.computeOnClient(client -> screen(context).model().selected()
				.map(Module::id)
				.orElse(""));

		if (!"autoeat".equals(selected)) {
			throw new AssertionError("sonuca tıklamak ayarın modülünü seçmeliydi, seçili: " + selected);
		}

		int settingY = context.computeOnClient(client -> screen(context).settingCenterY("health_threshold")
				.orElseThrow(() -> new AssertionError("ayar sağ panelde çizilmeliydi")));
		int top = context.computeOnClient(client -> screen(context).settingsViewTop());
		int bottom = context.computeOnClient(client -> screen(context).settingsViewBottom());

		if (settingY < top || settingY > bottom) {
			throw new AssertionError("panel ayara kaymalıydı: ayar y=" + settingY + ", görünür bant " + top + ".." + bottom);
		}

		// Off the top of the panel is where it was, so an unmoved panel is not a pass.
		if (context.computeOnClient(client -> screen(context).scrollOffset()) <= 0) {
			throw new AssertionError("ayarı göstermek için panelin kayması gerekirdi");
		}

		String lit = context.computeOnClient(client -> screen(context).highlightedSetting().orElse(""));

		if (!"health_threshold".equals(lit)) {
			throw new AssertionError("açılan ayar vurgulanmalıydı, vurgulanan: \"" + lit + "\"");
		}

		context.takeScreenshot("menu-arama-vurgu");

		context.waitTicks(HIGHLIGHT_TICKS);

		if (context.computeOnClient(client -> screen(context).highlightedSetting().isPresent())) {
			throw new AssertionError("vurgu kısa süreli olmalıydı, hala duruyor");
		}

		goldenAppleRule(context, false);
	}

	private static void goldenAppleRule(ClientGameTestContext context, boolean on) {
		context.runOnClient(client -> SurvivalKit.modules().byId("autoeat").orElseThrow()
				.settings().<BoolSetting>byId("golden_apple").orElseThrow().set(on));
		context.waitTicks(2);
	}

	// --- plumbing ---

	/**
	 * Holds the menu key, presses the letter under it, and lets go — the real order of
	 * events for a capital letter, which is what the rule has to survive.
	 */
	private static void typeCapitalWithMenuKey(ClientGameTestContext context, String letter) {
		context.getInput().holdKey(MENU_KEY);
		context.waitTick();
		context.getInput().pressKey(LETTER_D);
		context.getInput().typeChars(letter);
		context.waitTick();
		context.getInput().releaseKey(MENU_KEY);
		context.waitTicks(3);
	}

	private static void openMenu(ClientGameTestContext context) {
		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);

		if (screen(context) == null) {
			throw new AssertionError("menü tuşu menüyü açmalıydı");
		}
	}

	private static void clearSearch(ClientGameTestContext context) {
		context.runOnClient(client -> screen(context).searchBox().setValue(""));
		context.waitTicks(2);
	}

	private static void selectModule(ClientGameTestContext context, String moduleId) {
		Module module = SurvivalKit.modules().byId(moduleId)
				.orElseThrow(() -> new AssertionError("modül kayıtlı değil: " + moduleId));
		int y = context.computeOnClient(client -> screen(context).rowCenterY(module)
				.orElseThrow(() -> new AssertionError("modül listede yok: " + moduleId)));
		clickAt(context, context.computeOnClient(client -> screen(context).nameCenterX()), y);
	}

	private static void openPicker(ClientGameTestContext context) {
		MenuScroll.bringAddLineIntoView(context, "priority");
		int y = context.computeOnClient(client -> screen(context).addLineY("priority")
				.orElseThrow(() -> new AssertionError("+ Ekle satırı ekranda değil")));
		clickAt(context, context.computeOnClient(client -> screen(context).listEntryX("priority")), y);

		if (picker(context) == null) {
			throw new AssertionError("+ Ekle seçiciyi açmalıydı");
		}
	}

	private static void clickAt(ClientGameTestContext context, int guiX, int guiY) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());

		context.getInput().setCursorPos(guiX * scale, guiY * scale);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static List<String> visibleModuleIds(ClientGameTestContext context) {
		return context.computeOnClient(client -> screen(context).model().visibleModules().stream()
				.map(Module::id)
				.toList());
	}

	private static List<String> resultIds(ClientGameTestContext context) {
		return context.computeOnClient(client -> screen(context).model().results().stream()
				.map(SearchHit::id)
				.toList());
	}

	private static SurvivalKitScreen screen(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.gui.screen() instanceof SurvivalKitScreen open ? open : null);
	}

	private static ItemPickerScreen picker(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.gui.screen() instanceof ItemPickerScreen open ? open : null);
	}
}
