package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.menu.SurvivalKitScreen;
import com.merci.survivalkit.module.Module;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * How the settings column behaves on a wide screen.
 *
 * <p>On a 1920-pixel monitor the panel used to run the whole width of the screen: the label
 * sat at one end of the room and its switch at the other, with the eye asked to carry the
 * line across the gap. The column is capped instead, and the row under the cursor lights up
 * so label and control are visibly the same row.
 */
public class MenuLayoutGameTest implements FabricClientGameTest {
	private static final InputConstants.Key MENU_KEY =
		InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT);
	/** Comfortable reading width for a label and its control, in GUI units. */
	private static final int WIDEST = 400;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestLanguage.ensureTurkish(context);

			// A 1920 monitor at the scale people actually play at: this is the screen the
			// panel looked wrong on, and at a larger scale the problem hides itself.
			context.getInput().resizeWindow(1920, 1080);
			context.runOnClient(client -> {
				client.options.guiScale().set(2);
				client.resizeGui();
			});
			context.waitTicks(5);

			openMenuOn(context, "autoeat");

			theColumnDoesNotRunTheWholeWidthOfTheScreen(context);
			theRowUnderTheCursorIsTheOneThatLightsUp(context);
			context.takeScreenshot("menu-genis-ekran");

			context.getInput().pressKey(MENU_KEY);
			context.waitTicks(5);

			SurvivalKit.LOGGER.info("[gametest] geniş ekran düzeni doğrulandı (sütun {} birimde sınırlı, imlecin altındaki satır vurgulanıyor)", WIDEST);
		}
	}

	private static void theColumnDoesNotRunTheWholeWidthOfTheScreen(ClientGameTestContext context) {
		int left = context.computeOnClient(client -> screen(context).contentLeft());
		int right = context.computeOnClient(client -> screen(context).contentRight());
		int guiWidth = context.computeOnClient(client -> client.getWindow().getGuiScaledWidth());

		if (right - left > WIDEST) {
			throw new AssertionError("ayar sütunu " + (right - left) + " birim geniş; en fazla " + WIDEST
					+ " olmalıydı (ekran " + guiWidth + " birim)");
		}

		if (right <= left) {
			throw new AssertionError("sütunun sağ kenarı solunun sağında olmalıydı: " + left + " -> " + right);
		}
	}

	/** Hovering anywhere along a row counts, including the empty middle of it. */
	private static void theRowUnderTheCursorIsTheOneThatLightsUp(ClientGameTestContext context) {
		int y = screen(context).settingCenterY("threshold")
				.orElseThrow(() -> new AssertionError("açlık eşiği satırı ekranda değil"));
		int middle = context.computeOnClient(client ->
			(screen(context).contentLeft() + screen(context).contentRight()) / 2);

		moveTo(context, middle, y);

		Optional<String> hovered = context.computeOnClient(client -> screen(context).hoveredRowId(middle, y));

		if (hovered.isEmpty() || !"threshold".equals(hovered.get())) {
			throw new AssertionError("imlecin altındaki satır threshold olmalıydı, bulunan: " + hovered);
		}

		// Off the panel entirely: nothing is lit, or every row would glow at once.
		Optional<String> off = context.computeOnClient(client -> screen(context).hoveredRowId(2, 2));

		if (off.isPresent()) {
			throw new AssertionError("panel dışında vurgulanan satır olmamalıydı, bulunan: " + off.get());
		}
	}

	private static void moveTo(ClientGameTestContext context, int guiX, int guiY) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos(guiX * scale, guiY * scale);
		context.waitTicks(2);
	}

	private static void openMenuOn(ClientGameTestContext context, String moduleId) {
		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);

		Module module = SurvivalKit.modules().byId(moduleId)
				.orElseThrow(() -> new AssertionError("modül kayıtlı değil: " + moduleId));
		int y = screen(context).rowCenterY(module).orElseThrow(() -> new AssertionError("modül listede yok"));
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos(screen(context).nameCenterX() * scale, y * scale);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static SurvivalKitScreen screen(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.gui.screen() instanceof SurvivalKitScreen open ? open : null);
	}
}
