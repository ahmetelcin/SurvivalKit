package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.menu.SurvivalKitScreen;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.EnumSetting;
import com.mojang.blaze3d.platform.InputConstants;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;

/**
 * The list an enum setting opens when its values will not fit beside each other.
 *
 * <p>Written after the control was reported broken in a real game: the button drew an arrow
 * promising a list, there was no list to open, and the click still divided the button's width
 * between values it was no longer drawing separately — so pressing the left half of one button
 * silently chose the first value. Nothing caught it because every test set these settings
 * through code instead of pressing them.
 *
 * <p>So this one presses them, and looks at the screen rather than at the state: the assertion
 * that the list is visible is a pixel out of a screenshot, because "open" and "on screen and
 * not covered by the row below" are different claims and it was the second one that mattered.
 */
public class MenuDropdownGameTest implements FabricClientGameTest {
	private static final InputConstants.Key MENU_KEY =
		InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT);

	/** Four values ("En doyurucu", "En uzun dayanan", ...): far too many to sit side by side. */
	private static final String LIST_SETTING = "fallback";

	/** Two values, but "Tüm envanter" alone is wider than half the control: also a list. */
	private static final String LONG_LABELS = "scope";

	/** Two short values ("Ana el" / "Diğer el"): these do fit, and stay side-by-side buttons. */
	private static final String SIDE_BY_SIDE = "eating_hand";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestLanguage.ensureTurkish(context);
			context.waitTicks(5);

			openMenuOn(context, "autoeat");

			theListAppearsOnScreenWhenTheButtonIsPressed(context);
			pressingAnywhereOnTheButtonOpensAndPicksNothing(context);
			aTwoValueSettingWithLongLabelsBehavesTheSameWay(context);
			choosingAnOptionPicksItAndNeverReachesTheRowUnderneath(context);
			theSameSpotWithTheListClosedDoesReachThatRow(context);
			valuesThatFitAreStillPickedWhereTheyAreDrawn(context);

			context.takeScreenshot("menu-acilir-liste");
			closeMenu(context);

			SurvivalKit.LOGGER.info("[gametest] açılır liste doğrulandı (buton basınca liste ekranda ve en üstte, butonun her yeri sadece açıyor, seçenek tıklaması alttaki satıra geçmiyor; negatif kontrol o noktanın normalde canlı olduğunu gösterdi; sığan değerler hâlâ yerinden seçiliyor)");
		}
	}

	/**
	 * The claim the report was about: press the button and the list is *there*, over the rows
	 * below it. Proved with a pixel, sampled twice — before the press and after — so a list
	 * that is open but invisible, or drawn and then painted over, fails here.
	 */
	private void theListAppearsOnScreenWhenTheButtonIsPressed(ClientGameTestContext context) {
		bringIntoView(context, LIST_SETTING, true);

		if (!context.computeOnClient(client -> screen(context).choiceOpensAList(LIST_SETTING))) {
			throw new AssertionError("hazırlık: " + LIST_SETTING + " tek buton olarak çizilmeliydi");
		}

		Rect2i button = button(context, LIST_SETTING);
		clickAt(context, button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2);

		if (!context.computeOnClient(client -> screen(context).dropdownOpen())) {
			throw new AssertionError("butona basınca liste açılmalıydı");
		}

		// Sampled where the list says its last option is — clear of the label text, and found
		// this way round so the probe follows the list whether it opened down or up.
		Rect2i last = option(context, optionCount(context, LIST_SETTING) - 1);
		int probeX = last.getX() + last.getWidth() - 6;
		int probeY = last.getY() + last.getHeight() / 2;

		int open = pixelAt(context, probeX, probeY);
		int expected = context.computeOnClient(client -> screen(context).dropdownColour());

		if (open != (expected | 0xFF000000)) {
			throw new AssertionError("son seçeneğin bulunduğu piksel liste rengi olmalıydı, beklenen "
					+ hex(expected) + ", bulunan " + hex(open));
		}

		closeList(context);
		int closed = pixelAt(context, probeX, probeY);

		if (closed == open) {
			throw new AssertionError("negatif kontrol başarısız: aynı piksel liste kapalıyken de "
					+ hex(closed) + " idi, yani ölçüm listeyi görmüyor");
		}
	}

	/**
	 * Every part of the button does the same thing, and that thing is not choosing. This is
	 * the regression itself: the left third used to pick the first value.
	 */
	private void pressingAnywhereOnTheButtonOpensAndPicksNothing(ClientGameTestContext context) {
		pressEveryPartOf(context, LIST_SETTING);
	}

	/**
	 * Two values, drawn as one button because "Tüm envanter" does not fit in half of it. The
	 * old clicking rule only counted values — three or fewer meant "divide the width" — so
	 * this setting was the one that picked a value from where it was hit.
	 */
	private void aTwoValueSettingWithLongLabelsBehavesTheSameWay(ClientGameTestContext context) {
		bringIntoView(context, LONG_LABELS);

		if (!context.computeOnClient(client -> screen(context).choiceOpensAList(LONG_LABELS))) {
			throw new AssertionError("hazırlık: " + LONG_LABELS + " uzun etiketleri yüzünden tek buton olmalıydı");
		}

		pressEveryPartOf(context, LONG_LABELS);
	}

	private void pressEveryPartOf(ClientGameTestContext context, String settingId) {
		bringIntoView(context, settingId);
		Rect2i button = button(context, settingId);
		String before = value(settingId);

		int[] spots = {
			button.getX() + 3,
			button.getX() + button.getWidth() / 4,
			button.getX() + button.getWidth() / 2,
			button.getX() + button.getWidth() - 4,
		};

		for (int x : spots) {
			clickAt(context, x, button.getY() + button.getHeight() / 2);

			if (!context.computeOnClient(client -> screen(context).dropdownOpen())) {
				throw new AssertionError("butonun " + x + " noktası listeyi açmalıydı");
			}

			if (!value(settingId).equals(before)) {
				throw new AssertionError("butona basmak değer seçmemeliydi (" + settingId + " @ " + x + "): "
						+ before + " -> " + value(settingId));
			}

			closeList(context);
		}
	}

	/**
	 * Choosing from the list sets that value, and the click stops there — the toggle the list
	 * was drawn over keeps its state.
	 */
	private void choosingAnOptionPicksItAndNeverReachesTheRowUnderneath(ClientGameTestContext context) {
		bringIntoView(context, LIST_SETTING, true);
		Rect2i button = button(context, LIST_SETTING);
		clickAt(context, button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2);

		// Which row the list is actually covering, asked rather than assumed: the list is 48
		// units tall and the rows under it are not the one that happens to be named next in
		// the module. Guessing that got this negative control passing for the wrong reason.
		covered = aToggleUnderTheList(context);
		int probeY = screen(context).settingCenterY(covered)
				.orElseThrow(() -> new AssertionError("kapanan satır ekranda değil: " + covered));
		int probeX = context.computeOnClient(client -> screen(context).controlToggleCenterX());

		String wanted = optionName(context, LIST_SETTING, optionIndexAt(context, probeY));
		boolean toggleBefore = toggle(covered).get();

		clickAt(context, probeX, probeY);

		if (!value(LIST_SETTING).equals(wanted)) {
			throw new AssertionError("listedeki seçenek seçilmeliydi, beklenen " + wanted
					+ ", bulunan " + value(LIST_SETTING));
		}

		if (context.computeOnClient(client -> screen(context).dropdownOpen())) {
			throw new AssertionError("seçimden sonra liste kapanmalıydı");
		}

		if (toggle(covered).get() != toggleBefore) {
			throw new AssertionError("tıklama alttaki satıra geçmemeliydi: " + covered + " değişti");
		}

		coveredSpot = new Rect2i(probeX, probeY, 1, 1);
	}

	/**
	 * A toggle the open list is drawn over, found by walking down the list and asking the
	 * screen which row is under each height. There has to be one for the negative control to
	 * mean anything, so not finding one fails rather than quietly skipping.
	 */
	private static String aToggleUnderTheList(ClientGameTestContext context) {
		Rect2i bounds = context.computeOnClient(client -> screen(context).openDropdownBounds());

		for (int y = bounds.getY() + 2; y < bounds.getY() + bounds.getHeight(); y += 2) {
			final int at = y;
			String id = context.computeOnClient(client -> screen(context).settingAt(at).orElse(""));

			if (!id.isEmpty() && !id.equals(LIST_SETTING) && toggleOrNull(id) != null) {
				return id;
			}
		}

		throw new AssertionError("listenin kapattığı bir anahtar ayarı bulunamadı");
	}

	private static int optionIndexAt(ClientGameTestContext context, int guiY) {
		Rect2i bounds = context.computeOnClient(client -> screen(context).openDropdownBounds());
		int index = (guiY - bounds.getY()) / 12;

		if (index < 0 || index >= optionCount(context, LIST_SETTING)) {
			throw new AssertionError("örnek noktası listenin dışında: " + guiY);
		}

		return index;
	}

	/**
	 * Negative control for the claim above. The very same pixel, with no list over it, does
	 * reach the toggle — so "the toggle did not change" was a fact about the list swallowing
	 * the click, not about that spot being dead ground.
	 */
	private void theSameSpotWithTheListClosedDoesReachThatRow(ClientGameTestContext context) {
		if (coveredSpot == null || covered == null) {
			throw new AssertionError("hazırlık: önceki test kapanan satırı bırakmalıydı");
		}

		if (context.computeOnClient(client -> screen(context).dropdownOpen())) {
			throw new AssertionError("hazırlık: liste kapalı olmalıydı");
		}

		boolean before = toggle(covered).get();

		clickAt(context, coveredSpot.getX(), coveredSpot.getY());

		if (toggle(covered).get() == before) {
			throw new AssertionError("negatif kontrol başarısız: liste kapalıyken o nokta " + covered
					+ " anahtarını çevirmeliydi, yani seçenek tıklamasının onu çevirmemesi bir şey ispatlamıyor");
		}

		toggle(covered).set(before);
	}

	/**
	 * The other half of the one rule: values that do fit are still drawn side by side and
	 * still picked from where they are drawn. The fix must not have turned every enum into a
	 * list.
	 */
	private void valuesThatFitAreStillPickedWhereTheyAreDrawn(ClientGameTestContext context) {
		bringIntoView(context, SIDE_BY_SIDE);

		if (context.computeOnClient(client -> screen(context).choiceOpensAList(SIDE_BY_SIDE))) {
			throw new AssertionError("hazırlık: " + SIDE_BY_SIDE + " yan yana çizilmeliydi");
		}

		Rect2i button = button(context, SIDE_BY_SIDE);
		String first = optionName(context, SIDE_BY_SIDE, 0);
		String second = optionName(context, SIDE_BY_SIDE, 1);

		clickAt(context, button.getX() + button.getWidth() * 3 / 4, button.getY() + button.getHeight() / 2);

		if (!value(SIDE_BY_SIDE).equals(second)) {
			throw new AssertionError("sağ yarıya basmak ikinci değeri seçmeliydi, bulunan " + value(SIDE_BY_SIDE));
		}

		if (context.computeOnClient(client -> screen(context).dropdownOpen())) {
			throw new AssertionError("yan yana çizilen ayar liste açmamalıydı");
		}

		clickAt(context, button.getX() + button.getWidth() / 4, button.getY() + button.getHeight() / 2);

		if (!value(SIDE_BY_SIDE).equals(first)) {
			throw new AssertionError("sol yarıya basmak ilk değeri seçmeliydi, bulunan " + value(SIDE_BY_SIDE));
		}
	}

	// --- plumbing ---

	/** The toggle the open list was drawn over, and where it was, for the negative control. */
	private String covered;
	private Rect2i coveredSpot;

	/**
	 * One pixel of the real screen, as ARGB.
	 *
	 * <p>Through a screenshot, which is the only way to see what actually ended up on screen:
	 * geometry accessors would only repeat what the code believes. GUI units are scaled to
	 * window pixels the same way the input is.
	 */
	private static int pixelAt(ClientGameTestContext context, int guiX, int guiY) {
		Path shot = context.takeScreenshot("dropdown-probe");
		double scale = context.computeOnClient(MenuDropdownGameTest::guiScale);
		int x = (int) Math.round(guiX * scale);
		int y = (int) Math.round(guiY * scale);

		try {
			BufferedImage image = ImageIO.read(shot.toFile());

			if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
				throw new AssertionError("örnek noktası ekran dışında: " + x + "," + y);
			}

			return image.getRGB(x, y);
		} catch (IOException failure) {
			throw new AssertionError("ekran görüntüsü okunamadı: " + shot, failure);
		}
	}

	private static String hex(int argb) {
		return String.format("#%08X", argb);
	}

	private static Rect2i button(ClientGameTestContext context, String settingId) {
		return context.computeOnClient(client -> screen(context).choiceButton(settingId)
				.orElseThrow(() -> new AssertionError("ayarın butonu ekranda değil: " + settingId)));
	}

	private static Rect2i option(ClientGameTestContext context, int index) {
		return context.computeOnClient(client -> screen(context).openDropdownOption(index));
	}

	private static int optionCount(ClientGameTestContext context, String settingId) {
		return choice(settingId).options().size();
	}

	private static String optionName(ClientGameTestContext context, String settingId, int index) {
		return String.valueOf(choice(settingId).options().get(index));
	}

	private static String value(String settingId) {
		return String.valueOf(choice(settingId).get());
	}

	private static EnumSetting<?> choice(String settingId) {
		return module("autoeat").settings().<EnumSetting<?>>byId(settingId)
				.orElseThrow(() -> new AssertionError("enum ayarı yok: " + settingId));
	}

	private static BoolSetting toggleOrNull(String settingId) {
		return module("autoeat").settings().<BoolSetting>byId(settingId).orElse(null);
	}

	private static BoolSetting toggle(String settingId) {
		return module("autoeat").settings().<BoolSetting>byId(settingId)
				.orElseThrow(() -> new AssertionError("anahtar ayarı yok: " + settingId));
	}

	private static void closeList(ClientGameTestContext context) {
		context.getInput().pressKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_ESCAPE));
		context.waitTicks(2);

		if (context.computeOnClient(client -> screen(context).dropdownOpen())) {
			throw new AssertionError("Esc listeyi kapatmalıydı");
		}
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

	private static void closeMenu(ClientGameTestContext context) {
		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);
	}

	/** A row outside the visible band refuses clicks, so it is wheeled in the way a player would. */
	private static void bringIntoView(ClientGameTestContext context, String settingId) {
		bringIntoView(context, settingId, false);
	}

	/**
	 *  roomBelow whether the row has to end up high enough for its list to hang under it.
	 *                  Only the tests about the row the list covers need that; for the rest,
	 *                  a list that opens upwards because it is near the bottom is correct and
	 *                  insisting otherwise only makes the test unable to reach the setting.
	 */
	private static void bringIntoView(ClientGameTestContext context, String settingId, boolean roomBelow) {
		double scale = context.computeOnClient(MenuDropdownGameTest::guiScale);
		int cursorX = context.computeOnClient(client -> screen(context).controlToggleCenterX());

		for (int attempt = 0; attempt < 60; attempt++) {
			int y = screen(context).settingCenterY(settingId)
					.orElseThrow(() -> new AssertionError("ayar ekranda değil: " + settingId));
			int top = context.computeOnClient(client -> screen(context).settingsViewTop());
			int bottom = context.computeOnClient(client -> screen(context).settingsViewBottom());

			// Room for the list under it as well, or it would open upwards — which is correct
			// behaviour but not the case these tests are about: the row the list has to cover
			// is the one below it.
			int needed = roomBelow ? 5 * 12 : 0;

			if (y >= top && y + needed <= bottom) {
				return;
			}

			context.getInput().setCursorPos(cursorX * scale, (top + bottom) / 2.0 * scale);

			// Two ways to be wrong and they need opposite wheels. Asking only "is it below the
			// panel" sends a row that is merely too close to the bottom the wrong way, and it
			// never arrives.
			context.getInput().scroll(y < top ? 1 : -1);
			context.waitTick();
		}

		throw new AssertionError("ayar görünür alana kaydırılamadı: " + settingId
				+ " (satır " + screen(context).settingCenterY(settingId).orElse(-1)
				+ ", panel " + context.computeOnClient(client -> screen(context).settingsViewTop())
				+ ".." + context.computeOnClient(client -> screen(context).settingsViewBottom()) + ")");
	}

	private static void clickAt(ClientGameTestContext context, int guiX, int guiY) {
		double scale = context.computeOnClient(MenuDropdownGameTest::guiScale);

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
}
