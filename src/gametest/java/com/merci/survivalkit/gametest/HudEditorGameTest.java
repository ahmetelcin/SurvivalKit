package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.hud.ActiveModules;
import com.merci.survivalkit.hud.Anchor;
import com.merci.survivalkit.hud.HudBox;
import com.merci.survivalkit.hud.HudDrop;
import com.merci.survivalkit.hud.HudEditorScreen;
import com.merci.survivalkit.hud.HudPlacement;
import com.merci.survivalkit.menu.SurvivalKitScreen;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.module.impl.HudModule;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.renderer.Rect2i;

/**
 * The HUD editor, with the real mouse: opened from the menu's own button, the list of modules that
 * are on dragged from the top right to near the left edge low down and let go there — where it
 * sticks to the edge and is kept from the bottom left — and, after the world is left, the settings
 * read back from the file and a world entered again, drawn right there, white pixels and all. Then
 * a right click puts it back, and so does "reset all".
 */
public class HudEditorGameTest implements FabricClientGameTest {
	private static final InputConstants.Key MENU_KEY = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT);
	/** Let go this far from the left edge: inside {@link HudDrop#SNAP}, so it sticks. */
	private static final int NEAR_THE_EDGE = 3;
	/** How far below the middle of the screen its top is let go. */
	private static final int BELOW_THE_MIDDLE = 30;

	@Override
	public void runTest(ClientGameTestContext context) {
		HudBox expected;
		HudDrop.Placed placed;

		try {
			try (TestSingleplayerContext world = context.worldBuilder().create()) {
				HudGameTest.darkStage(context, world);
				context.runOnClient(client -> {
					module("hud").setEnabled(true);
					module("autoeat").setEnabled(true);
				});
				context.waitTicks(5);

				openEditor(context);
				HudBox before = editor(context).boxOf(ActiveModules.ID);
				context.takeScreenshot("hud-duzenleyici");
				int screenHeight = context.computeOnClient(client -> client.getWindow().getGuiScaledHeight());
				int targetY = screenHeight / 2 + BELOW_THE_MIDDLE;
				drag(context, before, NEAR_THE_EDGE, targetY);

				HudPlacement placement = placement(context);
				placed = new HudDrop.Placed(placement.anchor(), placement.offsetX(), placement.offsetY());

				if (placed.anchor() != Anchor.BOTTOM_LEFT || placed.offsetX() != 0
					|| placed.offsetY() != screenHeight - before.height() - targetY) {
					throw new AssertionError("sol kenara yakın, ortanın altında bırakılan kutu sol kenara yapışıp sol alttan tutulmalıydı: "
						+ placed + ", kutu " + before + ", hedef y " + targetY);
				}

				expected = new HudBox(0, targetY, before.width(), before.height());
				HudBox inEditor = editor(context).boxOf(ActiveModules.ID);

				if (!expected.equals(inEditor)) {
					throw new AssertionError("düzenleyicide kutu bırakıldığı yerde olmalıydı: " + inEditor + ", beklenen " + expected);
				}

				clickButton(context, editor(context).doneX());
				closeMenu(context);
				// The menu's save comes a second after the last change.
				context.waitTicks(40);
			}

			// Off the disk, the way the game starts: nothing of this survives in memory but the file.
			context.runOnClient(client -> SurvivalKit.reloadSettings());

			try (TestSingleplayerContext world = context.worldBuilder().create()) {
				HudGameTest.darkStage(context, world);
				context.waitTicks(5);
				HudPlacement placement = context.computeOnClient(client -> placement(client));

				if (placement.anchor() != placed.anchor() || placement.offsetX() != placed.offsetX() || placement.offsetY() != placed.offsetY()) {
					throw new AssertionError("dosyadan okunan yer bırakılan yer olmalıydı: " + placement.anchor() + " "
						+ placement.offsetX() + "," + placement.offsetY() + ", beklenen " + placed);
				}

				HudBox drawn = context.computeOnClient(client -> SurvivalKit.hud().drawn(ActiveModules.ID));
				int lit = drawn == null ? 0 : HudGameTest.white(context, "hud-duzenleyici-yeniden", drawn);

				if (!expected.equals(drawn) || lit < 30) {
					throw new AssertionError("oyuna yeniden girince liste bırakıldığı yerde çizilmeliydi: " + drawn + ", beklenen "
						+ expected + ", beyaz piksel " + lit);
				}

				SurvivalKit.LOGGER.info("[gametest] HUD düzenleyici: liste {} -> {} ({}), yeniden girişte {} beyaz piksel",
					"sağ üst", expected, placed, lit);

				aRightClickPutsItBack(context);
				resetAllPutsItBack(context);
			}
		} catch (AssertionError e) {
			SurvivalKit.LOGGER.error("[gametest-hata] {}", e.getMessage());
			throw e;
		}

		SurvivalKit.LOGGER.info("[gametest] HUD düzenleyici doğrulandı (menüden açıldı, gerçek sürüklemeyle taşındı, kenara yapıştı, "
			+ "dosyadan okunup yeni dünyada yerinde çizildi; sağ tık ve \"hepsini sıfırla\" yerine koydu)");
	}

	private static void aRightClickPutsItBack(ClientGameTestContext context) {
		openEditor(context);
		HudBox box = editor(context).boxOf(ActiveModules.ID);
		clickAt(context, box.x() + box.width() / 2, box.y() + box.height() / 2, InputConstants.MOUSE_BUTTON_RIGHT);
		HudPlacement placement = placement(context);

		if (placement.anchor() != Anchor.TOP_RIGHT || placement.offsetX() != 2 || placement.offsetY() != 2) {
			throw new AssertionError("sağ tık kutuyu sağ üste, kenarlardan 2'ye koymalıydı: " + placement.anchor() + " "
				+ placement.offsetX() + "," + placement.offsetY());
		}
	}

	private static void resetAllPutsItBack(ClientGameTestContext context) {
		HudBox box = editor(context).boxOf(ActiveModules.ID);
		drag(context, box, 40, 40);

		if (placement(context).anchor() == Anchor.TOP_RIGHT) {
			throw new AssertionError("hazırlık: kutu taşınmalıydı");
		}

		clickButton(context, editor(context).resetAllX());
		HudPlacement placement = placement(context);

		if (placement.anchor() != Anchor.TOP_RIGHT || placement.offsetX() != 2 || placement.offsetY() != 2) {
			throw new AssertionError("\"hepsini sıfırla\" kutuyu sağ üste koymalıydı: " + placement.anchor());
		}

		clickButton(context, editor(context).doneX());
		closeMenu(context);
	}

	/** The real menu key, then a real click on the bar's "Edit HUD". */
	private static void openEditor(ClientGameTestContext context) {
		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);
		SurvivalKitScreen menu = context.computeOnClient(client -> client.gui.screen() instanceof SurvivalKitScreen open ? open : null);

		if (menu == null) {
			throw new AssertionError("menü açılmalıydı");
		}

		Rect2i button = context.computeOnClient(client -> menu.hudEditorButton());
		clickAt(context, button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2, InputConstants.MOUSE_BUTTON_LEFT);
		editor(context);
	}

	/** Takes the box by its middle, moves the pointer there a little at a time, lets go with the box's top left at (x, y). */
	private static void drag(ClientGameTestContext context, HudBox box, int x, int y) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		int grabX = box.width() / 2;
		int grabY = box.height() / 2;
		double fromX = box.x() + grabX;
		double fromY = box.y() + grabY;
		double toX = x + grabX;
		double toY = y + grabY;
		context.getInput().setCursorPos(fromX * scale, fromY * scale);
		context.waitTick();
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();

		for (int step = 1; step <= 10; step++) {
			context.getInput().setCursorPos((fromX + (toX - fromX) * step / 10) * scale, (fromY + (toY - fromY) * step / 10) * scale);
			context.waitTick();
		}

		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static void clickButton(ClientGameTestContext context, int x) {
		HudEditorScreen editor = editor(context);
		int y = context.computeOnClient(client -> editor.buttonsY());
		clickAt(context, x + editor.buttonCenterOffsetX(), y + editor.buttonCenterOffsetY(), InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static void clickAt(ClientGameTestContext context, int guiX, int guiY, int button) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos(guiX * scale, guiY * scale);
		context.waitTick();
		context.getInput().pressMouse(button);
		context.waitTicks(3);
	}

	private static void closeMenu(ClientGameTestContext context) {
		if (context.computeOnClient(client -> client.gui.screen() instanceof SurvivalKitScreen)) {
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(3);
		}

		if (context.computeOnClient(client -> client.gui.screen() != null)) {
			throw new AssertionError("menü kapanmalıydı, açık: " + context.computeOnClient(client -> client.gui.screen()));
		}
	}

	private static HudEditorScreen editor(ClientGameTestContext context) {
		HudEditorScreen editor = context.computeOnClient(client -> client.gui.screen() instanceof HudEditorScreen open ? open : null);

		if (editor == null) {
			throw new AssertionError("HUD düzenleyicisi açık olmalıydı, açık: " + context.computeOnClient(client -> client.gui.screen()));
		}

		return editor;
	}

	private static HudPlacement placement(ClientGameTestContext context) {
		return context.computeOnClient(HudEditorGameTest::placement);
	}

	private static HudPlacement placement(net.minecraft.client.Minecraft client) {
		return ((HudModule) module("hud")).panels().getFirst().placement();
	}

	private static Module module(String id) {
		return SurvivalKit.modules().byId(id).orElseThrow(() -> new AssertionError("modül yok: " + id));
	}
}
