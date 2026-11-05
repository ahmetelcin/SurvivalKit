package com.merci.survivalkit.hud;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** How a panel is measured, placed and drawn: one way, for the HUD and for its editor alike. */
public final class HudDrawing {
	/** Dark and see-through, behind the text: readable over snow and sky alike. */
	public static final int BACKGROUND = 0x90000000;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int WARNING = 0xFFFF5555;
	static final int PADDING = 2;
	static final int LINE = 10;

	private HudDrawing() {
	}

	/** The panel's size at its scale, before it is placed: widest line, and a line per line. */
	public static int[] size(Font font, List<HudLine> lines, int scale) {
		int width = 0;

		for (HudLine line : lines) {
			width = Math.max(width, font.width(line.text()));
		}

		float factor = scale / 100f;
		return new int[] {Math.round((width + 2 * PADDING) * factor), Math.round((lines.size() * LINE + PADDING) * factor)};
	}

	/** Where its settings put the panel on this screen. */
	public static HudBox place(Font font, HudPlacement placement, List<HudLine> lines, int screenWidth, int screenHeight) {
		int[] size = size(font, lines, placement.scale());
		return HudLayout.place(placement.anchor(), placement.offsetX(), placement.offsetY(), size[0], size[1], screenWidth, screenHeight);
	}

	/** The panel's lines in its box, at its scale; right-aligned when measured from the right. */
	public static void draw(GuiGraphicsExtractor graphics, Font font, HudPlacement placement, List<HudLine> lines, HudBox box, boolean right) {
		float scale = placement.scale() / 100f;
		int width = 0;

		for (HudLine line : lines) {
			width = Math.max(width, font.width(line.text()));
		}

		width += 2 * PADDING;
		int height = lines.size() * LINE + PADDING;

		graphics.pose().pushMatrix();
		graphics.pose().translate(box.x(), box.y());
		graphics.pose().scale(scale, scale);
		graphics.fill(0, 0, width, height, BACKGROUND);

		for (int i = 0; i < lines.size(); i++) {
			HudLine line = lines.get(i);
			int x = right ? width - PADDING - font.width(line.text()) : PADDING;
			graphics.text(font, line.text(), x, PADDING + i * LINE, line.warning() ? WARNING : TEXT, true);
		}

		graphics.pose().popMatrix();
	}
}
