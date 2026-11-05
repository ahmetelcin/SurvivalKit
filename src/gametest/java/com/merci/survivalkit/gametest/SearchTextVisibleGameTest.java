package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.menu.ItemPickerScreen;
import com.merci.survivalkit.menu.SurvivalKitScreen;
import com.merci.survivalkit.module.Module;
import com.mojang.blaze3d.platform.InputConstants;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;

/**
 * What is typed into a text box can be seen — checked on the pixels of a real screenshot,
 * not on the box's value, which was right all along.
 *
 * <p>Every box in the menu was drawn by the screen's widget pass, which ran before the panel,
 * the box's own opaque background and its frame were drawn; those then covered the text. The
 * boxes are now drawn in place, right after their frame. All three kinds are checked: the
 * menu's search, a setting's text box, and the item picker's search.
 */
public class SearchTextVisibleGameTest implements FabricClientGameTest {
	private static final InputConstants.Key MENU_KEY =
		InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT);
	private static final InputConstants.Key ESCAPE =
		InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_ESCAPE);

	/** The boxes' text is 0xE0E0E0; their background, frame and dimmed hints are far darker. */
	private static final int LIT = 0xC8;

	/** A line of typed characters leaves well over a hundred lit pixels at any GUI scale; a hidden one leaves none. */
	private static final int ENOUGH = 40;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(5);
			context.getInput().pressKey(MENU_KEY);
			context.waitTicks(5);

			int search = theMenuSearchShowsWhatIsTyped(context);
			int seed = aSettingBoxShowsWhatIsTyped(context);
			int picker = thePickerSearchShowsWhatIsTyped(context);

			context.getInput().pressKey(ESCAPE);
			context.getInput().pressKey(ESCAPE);
			context.waitTicks(3);

			SurvivalKit.LOGGER.info("[gametest] kutulara yazılan ekranda görünüyor (açık piksel: menü araması {}, tohum kutusu {}, seçici araması {})",
				search, seed, picker);
		}
	}

	private static int theMenuSearchShowsWhatIsTyped(ClientGameTestContext context) {
		context.getInput().typeChars("WWWWWW");
		context.waitTicks(3);

		int lit = litPixels(context, "yazi-menu-aramasi", client -> textArea(menu(client).searchBox(), client));
		demand(lit, "menü arama kutusuna yazılan");

		// Emptied again, so the module list comes back for the next step.
		context.getInput().pressKey(ESCAPE);
		context.waitTicks(3);
		return lit;
	}

	/**
	 * The seed box: what is typed shows, and it sits on the line the hint sat on, in the middle
	 * of the frame. The hint's top is read off the empty box first — its "0" is as tall as the
	 * typed digits, so the two tops are the same row when the two lines agree.
	 *
	 * <p>The click lands near the frame's top edge, not its middle: the box inside is only a
	 * line tall, and the whole frame has to answer as the box.
	 */
	private static int aSettingBoxShowsWhatIsTyped(ClientGameTestContext context) {
		selectModule(context, "wdl");

		Rect2i frame = context.computeOnClient(client -> menu(client).textBoxFrame("seed"))
				.orElseThrow(() -> new AssertionError("tohum kutusu ekranda değil"));
		java.util.function.Function<net.minecraft.client.Minecraft, int[]> inFrame = client -> {
			Rect2i box = menu(client).textBoxFrame("seed").orElseThrow();
			return scaled(client, box.getX() + 1, box.getY() + 1, box.getX() + box.getWidth() - 1, box.getY() + box.getHeight() - 1);
		};

		int[] hint = rows(context, "tohum-yer-tutucu", inFrame, SearchTextVisibleGameTest::dim);

		clickAt(context, frame.getX() + frame.getWidth() / 2, frame.getY() + 2);

		if (!context.computeOnClient(client -> menu(client).getFocused() instanceof EditBox)) {
			throw new AssertionError("çerçevenin üst kenarına tıklamak tohum kutusunu açmalıydı");
		}

		context.getInput().typeChars("88888888");
		context.waitTicks(3);

		int lit = litPixels(context, "yazi-tohum-kutusu", inFrame::apply);
		demand(lit, "tohum kutusuna yazılan");

		int[] typed = rows(context, "tohum-yazi-hizasi", inFrame, SearchTextVisibleGameTest::lit);
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		int[] frameArea = context.computeOnClient(inFrame::apply);

		if (hint[0] < 0 || typed[0] < 0) {
			throw new AssertionError("hiza ölçülemedi: yer tutucu satırları " + java.util.Arrays.toString(hint)
				+ ", yazı satırları " + java.util.Arrays.toString(typed));
		}

		if (Math.abs(typed[0] - hint[0]) > 1) {
			throw new AssertionError("tohum kutusunda yazı yer tutucuyla aynı hizada olmalı: yazının üstü "
				+ typed[0] + ", yer tutucunun üstü " + hint[0] + " (ekran pikseli)");
		}

		double textMiddle = (typed[0] + typed[1]) / 2.0;
		double frameMiddle = (frameArea[1] + frameArea[3]) / 2.0;

		if (Math.abs(textMiddle - frameMiddle) > 1.5 * scale) {
			throw new AssertionError("tohum kutusunda yazı dikeyde ortalı olmalı: yazının ortası " + textMiddle
				+ ", çerçevenin ortası " + frameMiddle + " (ekran pikseli)");
		}

		SurvivalKit.LOGGER.info("[gametest] tohum kutusu hizası: yazının üstü {}, yer tutucunun üstü {}, yazının ortası {}, çerçevenin ortası {} (ekran pikseli, ölçek {})",
			typed[0], hint[0], textMiddle, frameMiddle, scale);

		context.runOnClient(client -> module("wdl").settings().reset());
		context.getInput().pressKey(ESCAPE);
		context.waitTicks(3);
		return lit;
	}

	private static int thePickerSearchShowsWhatIsTyped(ClientGameTestContext context) {
		if (!context.computeOnClient(client -> client.gui.screen() instanceof SurvivalKitScreen)) {
			context.getInput().pressKey(MENU_KEY);
			context.waitTicks(5);
		}

		selectModule(context, "autoeat");
		MenuScroll.bringAddLineIntoView(context, "priority");

		int y = context.computeOnClient(client -> menu(client).addLineY("priority")
				.orElseThrow(() -> new AssertionError("+ Ekle satırı ekranda değil")));
		clickAt(context, context.computeOnClient(client -> menu(client).listEntryX("priority")), y);

		if (!context.computeOnClient(client -> client.gui.screen() instanceof ItemPickerScreen)) {
			throw new AssertionError("+ Ekle seçiciyi açmalıydı");
		}

		context.getInput().typeChars("WWWW");
		context.waitTicks(3);

		int lit = litPixels(context, "yazi-secici-aramasi",
			client -> textArea(((ItemPickerScreen) client.gui.screen()).searchBox(), client));
		demand(lit, "seçici arama kutusuna yazılan");

		context.getInput().pressKey(ESCAPE);
		context.getInput().pressKey(ESCAPE);
		context.waitTicks(3);
		return lit;
	}

	// --- pixels ---

	private static void demand(int lit, String what) {
		if (lit < ENOUGH) {
			throw new AssertionError(what + " ekranda görünmeli: yazı alanında " + lit
				+ " açık piksel var, en az " + ENOUGH + " bekleniyordu");
		}
	}

	/** The part of a box its typed text occupies, in screenshot pixels. */
	private static int[] textArea(EditBox box, net.minecraft.client.Minecraft client) {
		int textWidth = client.font.width(box.getValue());
		return scaled(client, box.getX(), box.getY(), box.getX() + textWidth + 2, box.getY() + box.getHeight());
	}

	private static int[] scaled(net.minecraft.client.Minecraft client, int x0, int y0, int x1, int y1) {
		double scale = client.getWindow().getGuiScale();
		return new int[] {(int) Math.floor(x0 * scale), (int) Math.floor(y0 * scale),
			(int) Math.ceil(x1 * scale), (int) Math.ceil(y1 * scale)};
	}

	private static boolean lit(int rgb) {
		return ((rgb >> 16) & 0xFF) >= LIT && ((rgb >> 8) & 0xFF) >= LIT && (rgb & 0xFF) >= LIT;
	}

	/** The dimmed hint colour, 0x9A9AA4, give or take the blending. */
	private static boolean dim(int rgb) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		return r >= 0x88 && r <= 0xB0 && g >= 0x88 && g <= 0xB0 && b >= 0x90 && b <= 0xBA;
	}

	/** The first and last screenshot row inside the area holding a matching pixel; -1s if none does. */
	private static int[] rows(ClientGameTestContext context, String name,
			java.util.function.Function<net.minecraft.client.Minecraft, int[]> where, java.util.function.IntPredicate matches) {
		int[] area = context.computeOnClient(where::apply);
		BufferedImage image = read(context.takeScreenshot(name));
		int first = -1;
		int last = -1;

		for (int y = Math.max(0, area[1]); y < Math.min(image.getHeight(), area[3]); y++) {
			for (int x = Math.max(0, area[0]); x < Math.min(image.getWidth(), area[2]); x++) {
				if (matches.test(image.getRGB(x, y))) {
					if (first < 0) {
						first = y;
					}

					last = y;
					break;
				}
			}
		}

		return new int[] {first, last};
	}

	private static BufferedImage read(Path shot) {
		try {
			return ImageIO.read(shot.toFile());
		} catch (IOException unreadable) {
			throw new AssertionError("ekran görüntüsü okunamadı: " + shot, unreadable);
		}
	}

	/** Lit pixels inside an area of a real screenshot. */
	private static int litPixels(ClientGameTestContext context, String name,
			java.util.function.Function<net.minecraft.client.Minecraft, int[]> where) {
		int[] area = context.computeOnClient(where::apply);
		Path shot = context.takeScreenshot(name);
		BufferedImage image;

		try {
			image = ImageIO.read(shot.toFile());
		} catch (IOException unreadable) {
			throw new AssertionError("ekran görüntüsü okunamadı: " + shot, unreadable);
		}

		int lit = 0;

		for (int y = Math.max(0, area[1]); y < Math.min(image.getHeight(), area[3]); y++) {
			for (int x = Math.max(0, area[0]); x < Math.min(image.getWidth(), area[2]); x++) {
				int rgb = image.getRGB(x, y);

				if (((rgb >> 16) & 0xFF) >= LIT && ((rgb >> 8) & 0xFF) >= LIT && (rgb & 0xFF) >= LIT) {
					lit++;
				}
			}
		}

		return lit;
	}

	// --- plumbing ---

	private static void selectModule(ClientGameTestContext context, String moduleId) {
		Module module = module(moduleId);
		int y = context.computeOnClient(client -> menu(client).rowCenterY(module)
				.orElseThrow(() -> new AssertionError("modül listede yok: " + moduleId)));
		clickAt(context, context.computeOnClient(client -> menu(client).nameCenterX()), y);
	}

	private static void clickAt(ClientGameTestContext context, int guiX, int guiY) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());

		context.getInput().setCursorPos(guiX * scale, guiY * scale);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static SurvivalKitScreen menu(net.minecraft.client.Minecraft client) {
		if (!(client.gui.screen() instanceof SurvivalKitScreen open)) {
			throw new AssertionError("menü açık değil, açık olan: " + client.gui.screen());
		}

		return open;
	}

	private static Module module(String id) {
		return SurvivalKit.modules().byId(id).orElseThrow(() -> new AssertionError("modül kayıtlı değil: " + id));
	}
}
