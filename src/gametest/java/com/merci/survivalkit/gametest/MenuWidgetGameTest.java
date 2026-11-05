package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.menu.SettingsAutoSave;
import com.merci.survivalkit.menu.SurvivalKitScreen;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.settings.RegistryListSetting;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

/**
 * The setting controls, driven through real clicks: a change reaches the file about a second
 * later, a setting that hides itself leaves no gap behind, and the reset arrow only exists
 * while there is something to undo.
 */
public class MenuWidgetGameTest implements FabricClientGameTest {
	private static final InputConstants.Key MENU_KEY =
		InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT);

	/** The stated second, plus a few ticks for the write itself. */
	private static final int SAVE_WAIT_TICKS = SettingsAutoSave.DELAY_TICKS + 5;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestLanguage.ensureTurkish(context);
			context.waitTicks(5);
			openMenuOn(context, "autoeat");

			aToggleChangeReachesTheFile(context);
			aHiddenSettingLeavesNoGap(context);
			theResetArrowOnlyExistsWhenThereIsSomethingToUndo(context);
			theNumberBesideTheTrackOpensABoxAndTakesATypedValue(context);
			theCrossDropsAListEntry(context);
			aHeadingFoldsOnAClickAndStaysFoldedWhenTheMenuReopens(context);

			context.takeScreenshot("menu-widgetlar");

			resetEverything(context);
			context.getInput().pressKey(MENU_KEY);
			context.waitTicks(5);

			SurvivalKit.LOGGER.info("[gametest] menü widget'ları doğrulandı (1 sn içinde kaydetme, gizli ayar boşluk bırakmıyor, ↺ varsayılana döndürüyor, sayıya basınca kutu açılıp yazılan değer geçiyor, ✕ girdiyi düşürüyor, başlık tıkla katlanıyor ve menü kapanıp açılınca katlı kalıyor)");
		}
	}

	/** Clicking the golden apple toggle has to end up in the profile on disk. */
	private static void aToggleChangeReachesTheFile(ClientGameTestContext context) {
		BoolSetting apple = bool("golden_apple");

		if (!apple.isDefault()) {
			throw new AssertionError("hazırlık başarısız: altın elma zaten varsayılan değil");
		}

		clickSetting(context, "golden_apple", screen(context).controlToggleCenterX());

		if (!apple.get()) {
			throw new AssertionError("tık ayarı çevirmeliydi");
		}

		if (readProfile().contains("\"golden_apple\": true")) {
			throw new AssertionError("dosyaya beklenenden erken yazılmış, gecikme çalışmıyor");
		}

		context.waitTicks(SAVE_WAIT_TICKS);

		if (!readProfile().contains("\"golden_apple\": true")) {
			throw new AssertionError("değişiklik bir saniye içinde dosyada olmalıydı:\n" + readProfile());
		}
	}

	/**
	 * The health threshold is only shown while the golden apple rule is on. Turning the rule
	 * off must remove the row outright, not leave an empty line where it was.
	 */
	private static void aHiddenSettingLeavesNoGap(ClientGameTestContext context) {
		if (screen(context).settingCenterY("health_threshold").isEmpty()) {
			throw new AssertionError("altın elma açıkken can eşiği görünmeliydi");
		}

		// The golden apple rows close the section, so the next thing down is the "hands"
		// heading. With nothing hidden, the last apple row sits directly above it; that
		// distance is what the apple toggle itself must end up at once the rows between go.
		int lastY = screen(context).settingCenterY("enchanted_golden_apple").orElseThrow();
		int headingY = screen(context).settingCenterY("hands").orElseThrow();
		int spacing = headingY - lastY;

		clickSetting(context, "golden_apple", screen(context).controlToggleCenterX());

		// Reaching the toggle may have scrolled the panel, so the rows below are measured
		// again from where the apple sits now rather than where it sat before the click.
		int appleAfterY = screen(context).settingCenterY("golden_apple").orElseThrow();

		if (screen(context).settingCenterY("health_threshold").isPresent()) {
			throw new AssertionError("altın elma kapanınca can eşiği gitmeliydi");
		}

		if (screen(context).settingCenterY("enchanted_golden_apple").isPresent()) {
			throw new AssertionError("altın elma kapanınca büyülü elma da gitmeliydi");
		}

		// The heading below the apple must have moved up into the space, not sat below a blank.
		int afterY = screen(context).settingCenterY("hands").orElseThrow();

		if (afterY - appleAfterY != spacing) {
			throw new AssertionError("gizlenen ayar boşluk bırakmış: beklenen aralık " + spacing
					+ ", bulunan " + (afterY - appleAfterY));
		}
	}

	/** The arrow is absent at the default, appears once changed, and puts the value back. */
	private static void theResetArrowOnlyExistsWhenThereIsSomethingToUndo(ClientGameTestContext context) {
		IntSetting threshold = number("threshold");
		int original = threshold.get();

		if (!threshold.isDefault()) {
			throw new AssertionError("hazırlık başarısız: eşik zaten varsayılan değil");
		}

		// Nothing to undo yet, so a click where the arrow would be must do nothing.
		clickSetting(context, "threshold", screen(context).resetCenterX());

		if (threshold.get() != original) {
			throw new AssertionError("varsayılandayken ↺ tıklanması bir şeyi değiştirmemeliydi");
		}

		clickSetting(context, "threshold", screen(context).sliderTrackLeftX());

		if (threshold.get() == original) {
			throw new AssertionError("slider değeri değiştirmeliydi");
		}

		if (threshold.isDefault()) {
			throw new AssertionError("değer değişince varsayılan sayılmamalıydı");
		}

		clickSetting(context, "threshold", screen(context).resetCenterX());

		if (!threshold.isDefault() || threshold.get() != original) {
			throw new AssertionError("↺ varsayılana döndürmeliydi, bulunan " + threshold.get());
		}
	}

	/**
	 * An int setting has two controls in one column and the right-hand one had never been
	 * pressed by a test: the number itself opens a box to type an exact value into. Dragging
	 * the track can only reach the value the pixel under the cursor stands for.
	 */
	private static void theNumberBesideTheTrackOpensABoxAndTakesATypedValue(ClientGameTestContext context) {
		IntSetting threshold = number("threshold");
		int wanted = 5;

		context.runOnClient(client -> threshold.set(threshold.max()));
		context.waitTicks(2);

		clickSetting(context, "threshold", screen(context).numberBoxCenterX());

		if (!context.computeOnClient(client -> screen(context).numberBoxOpen("threshold"))) {
			throw new AssertionError("sayıya basmak yazma kutusunu açmalıydı");
		}

		if (threshold.get() != threshold.max()) {
			throw new AssertionError("kutuyu açmak değeri değiştirmemeliydi, bulunan " + threshold.get());
		}

		// The box opens with the current value in it, so it is cleared before typing rather
		// than typed onto the end of.
		for (int stroke = 0; stroke < 4; stroke++) {
			context.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_BACKSPACE));
		}

		context.getInput().typeChars(String.valueOf(wanted));
		context.waitTicks(3);

		if (threshold.get() != wanted) {
			throw new AssertionError("yazılan değer ayara geçmeliydi, beklenen " + wanted
					+ ", bulunan " + threshold.get());
		}

		// Enter is the way out, not Escape: Escape with an empty search box belongs to the
		// screen and closes the whole menu.
		context.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RETURN));
		context.waitTicks(2);

		if (context.computeOnClient(client -> screen(context).numberBoxOpen("threshold"))) {
			throw new AssertionError("Enter yazma kutusunu kapatmalıydı");
		}

		context.runOnClient(client -> threshold.reset());
		context.waitTicks(2);
	}

	/**
	 * The cross on a list entry, which is the only way to take something off a list again and
	 * had no test pressing it either. Paired with its negative control: the same row, a few
	 * units to the left, keeps the entry — that spot starts a drag instead.
	 */
	private static void theCrossDropsAListEntry(ClientGameTestContext context) {
		RegistryListSetting priority = list("priority");

		context.runOnClient(client -> priority.set(List.of("minecraft:bread", "minecraft:apple")));
		context.waitTicks(2);
		bringIntoView(context, "priority");

		int entryY = entryY(context, "priority", 0);
		clickAt(context, context.computeOnClient(client -> screen(context).listEntryX("priority")), entryY);
		context.waitTicks(2);

		if (priority.get().size() != 2) {
			throw new AssertionError("negatif kontrol başarısız: girdinin gövdesine basmak silmemeliydi, kalan "
					+ priority.get());
		}

		entryY = entryY(context, "priority", 0);
		clickAt(context, context.computeOnClient(client -> screen(context).listEntryRemoveX()), entryY);
		context.waitTicks(2);

		if (!priority.get().equals(List.of("minecraft:apple"))) {
			throw new AssertionError("✕ ilk girdiyi düşürmeliydi, kalan " + priority.get());
		}

		context.runOnClient(client -> priority.reset());
		context.waitTicks(2);
	}

	/**
	 * A click on a heading's arrow folds what is under it and nothing else; the fold outlives
	 * the menu being closed and opened; a second click opens it again.
	 */
	private static void aHeadingFoldsOnAClickAndStaysFoldedWhenTheMenuReopens(ClientGameTestContext context) {
		List<String> food = List.of("priority", "blocked", "fallback");

		for (String id : List.of("trigger", "food", "golden_apples", "hands")) {
			if (screen(context).settingCenterY(id).isEmpty()) {
				throw new AssertionError("başlık çizilmemiş: " + id);
			}
		}

		requireShown(context, food, true, "gruplar açık başlamalıydı");

		// Negative control: a click on a setting's own line folds nothing.
		clickSetting(context, "threshold", screen(context).headingArrowCenterX());
		requireShown(context, food, true, "bir ayarın satırına tıklamak başlık katlamamalıydı");

		clickSetting(context, "food", screen(context).headingArrowCenterX());
		requireShown(context, food, false, "başlığa tıklamak altındakileri katlamalıydı");
		requireShown(context, List.of("food", "golden_apple", "threshold"), true,
				"yalnızca o başlığın altı katlanmalıydı");

		// Folded rows are gone, not blank: the next heading closes up under the folded one.
		int foodBottom = screen(context).rowBottomY("food").orElseThrow();
		int applesTop = screen(context).rowTopY("golden_apples").orElseThrow();

		if (applesTop != foodBottom) {
			throw new AssertionError("katlanan başlığın altında boşluk kalmış: başlık " + foodBottom
					+ "'de bitiyor, sonraki " + applesTop + "'te başlıyor");
		}

		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);
		openMenuOn(context, "autoeat");
		requireShown(context, food, false, "menü kapanıp açılınca katlı kalmalıydı");

		clickSetting(context, "food", screen(context).headingArrowCenterX());
		requireShown(context, food, true, "ikinci tık başlığı yeniden açmalıydı");
	}

	private static void requireShown(ClientGameTestContext context, List<String> ids, boolean shown, String message) {
		for (String id : ids) {
			if (screen(context).settingCenterY(id).isPresent() != shown) {
				throw new AssertionError(message + ": " + id);
			}
		}
	}

	/** An entry's own line, scrolled into view: entries hang below the row that owns them. */
	private static int entryY(ClientGameTestContext context, String settingId, int index) {
		for (int attempt = 0; attempt < 60; attempt++) {
			int top = context.computeOnClient(client -> screen(context).settingsViewTop());
			int bottom = context.computeOnClient(client -> screen(context).settingsViewBottom());
			int y = context.computeOnClient(client -> screen(context).listEntryY(settingId, index)
					.orElseThrow(() -> new AssertionError("girdi yerleştirilmemiş: " + settingId + " " + index)));

			if (y >= top && y <= bottom) {
				return y;
			}

			double scale = context.computeOnClient(MenuWidgetGameTest::guiScale);
			int cursorX = context.computeOnClient(client -> screen(context).controlToggleCenterX());
			context.getInput().setCursorPos(cursorX * scale, (top + bottom) / 2.0 * scale);
			context.getInput().scroll(y < top ? 1 : -1);
			context.waitTick();
		}

		throw new AssertionError("girdi görünür alana kaydırılamadı: " + settingId + " " + index);
	}

	// --- plumbing ---

	private static void resetEverything(ClientGameTestContext context) {
		context.runOnClient(client -> module("autoeat").settings().reset());
		context.waitTicks(SAVE_WAIT_TICKS);
	}

	private static void openMenuOn(ClientGameTestContext context, String moduleId) {
		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);

		Module module = module(moduleId);
		int y = screen(context).rowCenterY(module).orElseThrow(() -> new AssertionError("modül listede yok: " + moduleId));
		clickAt(context, screen(context).nameCenterX(), y);
	}

	private static void clickSetting(ClientGameTestContext context, String settingId, int x) {
		bringIntoView(context, settingId);

		int y = screen(context).settingCenterY(settingId)
				.orElseThrow(() -> new AssertionError("ayar ekranda değil: " + settingId));
		clickAt(context, x, y);
	}

	/**
	 * A row placed outside the visible band refuses clicks, so it is wheeled into view first,
	 * the way a player would reach it. The lists added in step 4 made AutoEat's panel taller
	 * than the screen, which is when clicking blind stopped working.
	 */
	private static void bringIntoView(ClientGameTestContext context, String settingId) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		int cursorX = context.computeOnClient(client -> screen(context).controlToggleCenterX());

		for (int attempt = 0; attempt < 60; attempt++) {
			int y = screen(context).settingCenterY(settingId)
					.orElseThrow(() -> new AssertionError("ayar ekranda değil: " + settingId));
			int top = context.computeOnClient(client -> screen(context).settingsViewTop());
			int bottom = context.computeOnClient(client -> screen(context).settingsViewBottom());

			if (y >= top && y <= bottom) {
				return;
			}

			// The cursor has to sit over the settings panel for the wheel to reach it.
			context.getInput().setCursorPos(cursorX * scale, (top + bottom) / 2.0 * scale);
			context.getInput().scroll(y > bottom ? -1 : 1);
			context.waitTick();
		}

		throw new AssertionError("ayar görünür alana kaydırılamadı: " + settingId);
	}

	private static void clickAt(ClientGameTestContext context, int guiX, int guiY) {
		double scale = context.computeOnClient(MenuWidgetGameTest::guiScale);

		context.getInput().setCursorPos(guiX * scale, guiY * scale);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static double guiScale(Minecraft client) {
		return client.getWindow().getGuiScale();
	}

	private static SurvivalKitScreen screen(ClientGameTestContext context) {
		SurvivalKitScreen menu = context.computeOnClient(
				client -> client.gui.screen() instanceof SurvivalKitScreen open ? open : null);

		if (menu == null) {
			throw new AssertionError("menü açık olmalıydı");
		}

		return menu;
	}

	private static Module module(String id) {
		return SurvivalKit.modules().byId(id).orElseThrow(() -> new AssertionError("modül kayıtlı değil: " + id));
	}

	private static BoolSetting bool(String id) {
		return module("autoeat").settings().<BoolSetting>byId(id).orElseThrow();
	}

	private static RegistryListSetting list(String id) {
		return module("autoeat").settings().<RegistryListSetting>byId(id).orElseThrow();
	}

	private static IntSetting number(String id) {
		return module("autoeat").settings().<IntSetting>byId(id).orElseThrow();
	}

	private static String readProfile() {
		Path file = SurvivalKit.settingsFile();

		try {
			return Files.exists(file) ? Files.readString(file) : "";
		} catch (IOException e) {
			throw new AssertionError("profil okunamadı: " + file, e);
		}
	}
}
