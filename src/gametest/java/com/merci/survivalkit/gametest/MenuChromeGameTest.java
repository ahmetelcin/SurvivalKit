package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.menu.SurvivalKitScreen;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.settings.RegistryListSetting;
import com.merci.survivalkit.settings.TextSetting;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

/**
 * The furniture of the menu, as opposed to what it does: a search box you can see the edges
 * of, list entries that show what they name, and a reset arrow big enough to aim at.
 *
 * <p>None of this changes behaviour, which is why it is easy to leave half-done and hard to
 * notice. So each piece is asserted rather than eyeballed, and the screenshot at the end is
 * for the human, not for the test.
 */
public class MenuChromeGameTest implements FabricClientGameTest {
	private static final InputConstants.Key MENU_KEY =
		InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT);

	/** Smallest square worth aiming at with a mouse, in GUI units. */
	private static final int AIMABLE = 14;

	/** An id no build answers to, so the icon column has nothing to draw. */
	private static final String UNKNOWN_ID = "bazımod:efsanevi_meyve";

	/** Air the hint has to leave between itself and the frame, in GUI units. */
	private static final int HINT_MARGIN = 4;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestLanguage.ensureTurkish(context);
			context.waitTicks(5);

			openMenuOn(context, "autoeat");

			theSearchBoxHasAFrameAMagnifierAndItsOwnHint(context);
			everyListEntryShowsTheItemItNames(context);
			theResetArrowIsBigEnoughToAimAt(context);

			context.takeScreenshot("menu-cerceve");

			openMenuOn(context, "wdl");
			theSeedBoxHasAFrameAndSaysWhatEmptyMeans(context);

			context.takeScreenshot("menu-tohum-kutusu");

			tidyUp(context);
			context.getInput().pressKey(MENU_KEY);
			context.waitTicks(5);

			SurvivalKit.LOGGER.info("[gametest] menü çerçevesi doğrulandı (arama kutusu çerçeveli ve büyüteçli, liste satırlarında item ikonu, ↺ {} birim, tohum kutusu yer tutuculu)", AIMABLE);
		}
	}

	/**
	 * Three things the box needs to look like a search box: an edge of its own, a magnifier
	 * that does not sit under the text, and a hint that says what can be searched rather
	 * than repeating the word on the label.
	 */
	private static void theSearchBoxHasAFrameAMagnifierAndItsOwnHint(ClientGameTestContext context) {
		Rect2i frame = context.computeOnClient(client -> screen(context).searchFrame());
		EditBox box = context.computeOnClient(client -> screen(context).searchBox());
		int iconRight = context.computeOnClient(client -> screen(context).searchIconRight());

		int boxLeft = context.computeOnClient(client -> box.getX());
		int boxRight = context.computeOnClient(client -> box.getX() + box.getWidth());

		if (frame.getX() >= boxLeft || frame.getX() + frame.getWidth() <= boxRight) {
			throw new AssertionError("çerçeve kutuyu sarmalıydı: çerçeve "
					+ frame.getX() + ".." + (frame.getX() + frame.getWidth())
					+ ", kutu " + boxLeft + ".." + boxRight);
		}

		// A frame, not a second panel: it hugs the box rather than the whole column.
		if (frame.getWidth() > box.getWidth() + 2 * AIMABLE) {
			throw new AssertionError("çerçeve kutudan çok geniş: " + frame.getWidth() + " / " + box.getWidth());
		}

		if (iconRight <= frame.getX() || iconRight > boxLeft) {
			throw new AssertionError("büyüteç çerçevenin içinde ve metnin solunda olmalıydı: çerçeve "
					+ frame.getX() + ", büyüteç sağ kenarı " + iconRight + ", metin " + boxLeft);
		}

		String hint = context.computeOnClient(client -> screen(context).searchHint());

		if (hint.isBlank() || Lang.isRaw(hint)) {
			throw new AssertionError("arama kutusunun yer tutucusu çevrilmiş olmalıydı, bulunan: " + hint);
		}

		if (hint.equals(Lang.of(Keys.MENU_SEARCH))) {
			throw new AssertionError("yer tutucu etiketin kopyası olmamalıydı: " + hint);
		}

		// Set is not shown: the first version of this had the text ready and drew nothing.
		String onScreen = context.computeOnClient(client -> screen(context).searchHintOnScreen());

		if (!onScreen.equals(hint)) {
			throw new AssertionError("boş kutuda yer tutucu ekranda olmalıydı, bulunan: \"" + onScreen + "\"");
		}

		// And it fits, with air to spare. A hint that runs into the frame reads as cut off,
		// and the next language to be added is the one that will overflow.
		int hintWidth = context.computeOnClient(client -> client.font.width(hint));
		int room = context.computeOnClient(client -> box.getWidth()) - HINT_MARGIN;

		if (hintWidth > room) {
			throw new AssertionError("yer tutucu kutuya sığmalıydı: " + hintWidth + " birim, yer " + room);
		}

		SurvivalKit.LOGGER.info("[gametest] yer tutucu \"{}\" {} birim, kutuda {} birim yer var", hint, hintWidth, room);

		// And it gets out of the way of what is typed, or it would sit under the query.
		context.getInput().typeChars("a");
		context.waitTicks(2);
		String whileTyping = context.computeOnClient(client -> screen(context).searchHintOnScreen());

		context.runOnClient(client -> screen(context).searchBox().setValue(""));
		context.waitTicks(2);

		if (!whileTyping.isEmpty()) {
			throw new AssertionError("yazarken yer tutucu kalkmalıydı, bulunan: \"" + whileTyping + "\"");
		}

		if (!context.computeOnClient(client -> screen(context).searchHintOnScreen()).equals(hint)) {
			throw new AssertionError("kutu boşalınca yer tutucu geri gelmeliydi");
		}
	}

	/** The name tells you what it is; the icon tells you at a glance. */
	private static void everyListEntryShowsTheItemItNames(ClientGameTestContext context) {
		RegistryListSetting blocked = list("blocked");
		context.runOnClient(client -> blocked.add(UNKNOWN_ID));
		context.waitTicks(2);

		Optional<ItemStack> first = context.computeOnClient(client -> screen(context).entryIcon("blocked", 0));

		if (first.isEmpty() || first.get().isEmpty()) {
			throw new AssertionError("listenin ilk satırında item ikonu olmalıydı");
		}

		String drawn = context.computeOnClient(client ->
			net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(first.get().getItem()).toString());

		if (!drawn.equals(blocked.get().get(0))) {
			throw new AssertionError("ikon satırın item'ı olmalıydı: " + blocked.get().get(0) + ", çizilen: " + drawn);
		}

		// An id this build never heard of has nothing to draw, and must not fall back to a
		// stand-in that would read as a real item.
		int unknownAt = blocked.get().indexOf(UNKNOWN_ID);
		Optional<ItemStack> unknown = context.computeOnClient(client -> screen(context).entryIcon("blocked", unknownAt));

		if (unknown.isPresent()) {
			throw new AssertionError("bilinmeyen id için ikon çizilmemeliydi, bulunan: " + unknown.get());
		}

		// The name has to move over to make room, or the icon would be drawn under the text.
		int iconRight = context.computeOnClient(client -> screen(context).entryIconRight("blocked"));
		int labelLeft = context.computeOnClient(client -> screen(context).entryLabelX("blocked"));

		if (labelLeft < iconRight) {
			throw new AssertionError("satır adı ikonun sağında başlamalıydı: ikon " + iconRight + ", ad " + labelLeft);
		}
	}

	/** The old arrow was a nine-pixel glyph in an eleven-pixel column: a dart, not a button. */
	private static void theResetArrowIsBigEnoughToAimAt(ClientGameTestContext context) {
		IntSetting threshold = number("threshold");
		context.runOnClient(client -> threshold.set(threshold.get() == threshold.max() ? threshold.min() : threshold.max()));
		context.waitTicks(2);

		bringIntoView(context, "threshold");

		Rect2i reset = context.computeOnClient(client -> screen(context).resetBox("threshold"))
				.orElseThrow(() -> new AssertionError("açlık eşiği satırı ekranda değil"));

		if (reset.getWidth() < AIMABLE || reset.getHeight() < AIMABLE) {
			throw new AssertionError("↺ en az " + AIMABLE + " birim olmalıydı, bulunan "
					+ reset.getWidth() + "x" + reset.getHeight());
		}

		// The mark itself, not just the area it answers clicks in: it used to be a nine-pixel
		// character floating in the middle of the box.
		Rect2i glyph = context.computeOnClient(client -> screen(context).resetGlyph("threshold"))
				.orElseThrow(() -> new AssertionError("↺ işaretinin çizildiği alan yok"));

		if (glyph.getWidth() < AIMABLE - 4 || glyph.getHeight() < AIMABLE - 4) {
			throw new AssertionError("↺ işareti kutusunu doldurmalıydı, bulunan "
					+ glyph.getWidth() + "x" + glyph.getHeight());
		}

		if (glyph.getX() < reset.getX() || glyph.getX() + glyph.getWidth() > reset.getX() + reset.getWidth()) {
			throw new AssertionError("↺ işareti tıklanan kutunun içinde kalmalıydı");
		}

		// The far left of the new box is outside where the old one ended: clicking there is
		// what proves the box grew rather than just the glyph.
		clickAt(context, reset.getX() + 1, reset.getY() + reset.getHeight() / 2);

		if (!threshold.isDefault()) {
			throw new AssertionError("↺ kutusunun sol ucuna tıklamak varsayılana döndürmeliydi, bulunan " + threshold.get());
		}
	}

	/** An empty box that quietly means zero should say so. */
	private static void theSeedBoxHasAFrameAndSaysWhatEmptyMeans(ClientGameTestContext context) {
		bringIntoView(context, "seed");

		String hint = context.computeOnClient(client -> screen(context).textBoxHint("seed"));
		String wanted = Lang.of(Keys.hint("wdl.seed"));

		if (!hint.equals(wanted) || hint.isBlank() || Lang.isRaw(hint)) {
			throw new AssertionError("tohum kutusu \"" + wanted + "\" yazmalıydı, bulunan: " + hint);
		}

		// Painted, not merely ready. Both earlier versions of this check passed while the box
		// on screen was blank: first because the widget's own hint drew nothing, then because
		// the drawing looked the hint up somewhere that was still empty mid-frame.
		String painted = context.computeOnClient(client -> screen(context).paintedHint("seed"));

		if (!painted.equals(wanted)) {
			throw new AssertionError("boş tohum kutusuna yer tutucu çizilmeliydi, çizilen: \"" + painted + "\"");
		}

		Rect2i frame = context.computeOnClient(client -> screen(context).textBoxFrame("seed"))
				.orElseThrow(() -> new AssertionError("tohum kutusunun çerçevesi yok"));

		if (frame.getWidth() < AIMABLE || frame.getHeight() < AIMABLE) {
			throw new AssertionError("tohum kutusunun çerçevesi kutuyu sarmalıydı, bulunan "
					+ frame.getWidth() + "x" + frame.getHeight());
		}

		// Typed into the way a player would, which is also what proves the box still takes
		// input after being bordered off and moved.
		TextSetting seed = seed();
		clickAt(context, frame.getX() + frame.getWidth() / 2, frame.getY() + frame.getHeight() / 2);
		context.getInput().typeChars("12345");
		context.waitTicks(3);

		if (!"12345".equals(seed.get())) {
			throw new AssertionError("tohum kutusuna yazılan ayara geçmeliydi, bulunan: " + seed.get());
		}

		// And the hint gets out of the way of a real seed.
		String whileFilled = context.computeOnClient(client -> screen(context).paintedHint("seed"));

		if (!whileFilled.isEmpty()) {
			throw new AssertionError("dolu kutuda yer tutucu kalmamalıydı, bulunan: \"" + whileFilled + "\"");
		}

		// The reset arrow on a text row: the near edge undoes, one pixel further out does not.
		Rect2i reset = context.computeOnClient(client -> screen(context).resetBox("seed"))
				.orElseThrow(() -> new AssertionError("tohum satırı ekranda değil"));

		clickAt(context, reset.getX() - 1, reset.getY() + reset.getHeight() / 2);

		if (!"12345".equals(seed.get())) {
			throw new AssertionError("↺ kutusunun dışına tıklamak tohumu değiştirmemeliydi, bulunan: " + seed.get());
		}

		clickAt(context, reset.getX() + 1, reset.getY() + reset.getHeight() / 2);

		if (!seed.get().isEmpty()) {
			throw new AssertionError("↺ tohumu boşaltmalıydı, bulunan: " + seed.get());
		}
	}

	// --- plumbing ---

	private static void tidyUp(ClientGameTestContext context) {
		context.runOnClient(client -> {
			module("autoeat").settings().reset();
			module("wdl").settings().reset();
		});
		context.waitTicks(5);
	}

	private static void openMenuOn(ClientGameTestContext context, String moduleId) {
		boolean open = context.computeOnClient(client -> client.gui.screen() instanceof SurvivalKitScreen);

		if (!open) {
			context.getInput().pressKey(MENU_KEY);
			context.waitTicks(5);
		}

		Module module = module(moduleId);
		int y = screen(context).rowCenterY(module)
				.orElseThrow(() -> new AssertionError("modül listede yok: " + moduleId));
		clickAt(context, screen(context).nameCenterX(), y);
	}

	/** A row outside the visible band refuses clicks, so it is wheeled in the way a player would. */
	private static void bringIntoView(ClientGameTestContext context, String settingId) {
		double scale = context.computeOnClient(MenuChromeGameTest::guiScale);
		int cursorX = context.computeOnClient(client -> screen(context).controlToggleCenterX());

		for (int attempt = 0; attempt < 60; attempt++) {
			int y = screen(context).settingCenterY(settingId)
					.orElseThrow(() -> new AssertionError("ayar ekranda değil: " + settingId));
			int top = context.computeOnClient(client -> screen(context).settingsViewTop());
			int bottom = context.computeOnClient(client -> screen(context).settingsViewBottom());

			if (y >= top && y <= bottom) {
				return;
			}

			context.getInput().setCursorPos(cursorX * scale, (top + bottom) / 2.0 * scale);
			context.getInput().scroll(y > bottom ? -1 : 1);
			context.waitTick();
		}

		throw new AssertionError("ayar görünür alana kaydırılamadı: " + settingId);
	}

	private static void clickAt(ClientGameTestContext context, int guiX, int guiY) {
		double scale = context.computeOnClient(MenuChromeGameTest::guiScale);

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

	private static IntSetting number(String id) {
		return module("autoeat").settings().<IntSetting>byId(id).orElseThrow();
	}

	private static RegistryListSetting list(String id) {
		return module("autoeat").settings().<RegistryListSetting>byId(id).orElseThrow();
	}

	private static TextSetting seed() {
		return module("wdl").settings().<TextSetting>byId("seed").orElseThrow();
	}
}
