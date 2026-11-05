package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.menu.SurvivalKitScreen;
import com.merci.survivalkit.module.Module;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

/**
 * The menu skeleton, driven the way a player drives it: the real key, the real cursor.
 *
 * <p>Nothing here reaches past the screen to set state directly — a click has to travel
 * through the layout and the hit testing, or the test would prove only that the model works,
 * which the unit tests already do.
 */
public class MenuGameTest implements FabricClientGameTest {
	private static final InputConstants.Key MENU_KEY =
		InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT);

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestLanguage.ensureTurkish(context);
			context.waitTicks(5);

			theMenuKeyOpensTheMenu(context);
			oneClickTogglesAutoEat(context);
			Module remembered = theSelectionSurvivesClosingTheMenu(context);

			context.takeScreenshot("menu-iskelet");

			closeMenu(context);
			SurvivalKit.LOGGER.info("[gametest] menü iskeleti doğrulandı (Right Shift açtı, tek tık AutoEat'i çevirdi, seçim {} olarak hatırlandı)",
					remembered.id());
		}
	}

	private static void theMenuKeyOpensTheMenu(ClientGameTestContext context) {
		if (screen(context) != null) {
			throw new AssertionError("hazırlık başarısız: menü zaten açık");
		}

		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);

		if (screen(context) == null) {
			throw new AssertionError("Right Shift menüyü açmalıydı");
		}
	}

	/** One click on the left-hand toggle, and AutoEat has to change state. */
	private static void oneClickTogglesAutoEat(ClientGameTestContext context) {
		Module autoEat = module("autoeat");
		boolean before = context.computeOnClient(client -> autoEat.isEnabled());

		clickAt(context, screenOf(context).toggleCenterX(), rowY(context, autoEat));

		boolean after = context.computeOnClient(client -> autoEat.isEnabled());

		if (after == before) {
			throw new AssertionError("tek tık AutoEat'i çevirmeliydi, hâlâ " + after);
		}

		// Put it back, so the rest of the suite starts where it expects to.
		clickAt(context, screenOf(context).toggleCenterX(), rowY(context, autoEat));

		if (context.computeOnClient(client -> autoEat.isEnabled()) != before) {
			throw new AssertionError("ikinci tık eski hâline döndürmeliydi");
		}
	}

	/** Selecting the other module, closing, reopening: the menu must come back where it was. */
	private static Module theSelectionSurvivesClosingTheMenu(ClientGameTestContext context) {
		Module other = module("wdl");

		clickAt(context, screenOf(context).nameCenterX(), rowY(context, other));

		String selected = context.computeOnClient(client -> screenOf(context).model().selected().orElseThrow().id());

		if (!"wdl".equals(selected)) {
			throw new AssertionError("tıklanan modül seçilmeliydi, seçili olan " + selected);
		}

		closeMenu(context);

		if (screen(context) != null) {
			throw new AssertionError("Right Shift menüyü kapatmalıydı");
		}

		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);

		String remembered = context.computeOnClient(client -> screenOf(context).model().selected().orElseThrow().id());

		if (!"wdl".equals(remembered)) {
			throw new AssertionError("menü son seçili modülle açılmalıydı, açılan " + remembered);
		}

		return other;
	}

	private static void closeMenu(ClientGameTestContext context) {
		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);
	}

	// --- plumbing ---

	private static Module module(String id) {
		return SurvivalKit.modules().byId(id).orElseThrow(() -> new AssertionError("modül kayıtlı değil: " + id));
	}

	private static SurvivalKitScreen screen(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.gui.screen() instanceof SurvivalKitScreen menu ? menu : null);
	}

	private static SurvivalKitScreen screenOf(ClientGameTestContext context) {
		SurvivalKitScreen menu = screen(context);

		if (menu == null) {
			throw new AssertionError("menü açık olmalıydı");
		}

		return menu;
	}

	private static int rowY(ClientGameTestContext context, Module module) {
		return screenOf(context).rowCenterY(module)
				.orElseThrow(() -> new AssertionError("modül listede görünmüyor: " + module.id()));
	}

	/**
	 * The cursor is placed in window pixels while the screen reasons in GUI units, so the
	 * click has to be scaled on its way out.
	 */
	private static void clickAt(ClientGameTestContext context, int guiX, int guiY) {
		double scale = context.computeOnClient(client -> guiScale(client));

		context.getInput().setCursorPos(guiX * scale, guiY * scale);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static double guiScale(Minecraft client) {
		return client.getWindow().getGuiScale();
	}
}
