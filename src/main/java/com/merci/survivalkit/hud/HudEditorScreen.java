package com.merci.survivalkit.hud;

import com.merci.survivalkit.module.impl.HudModule;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Every shown HUD box where it is, over the world: dragged with the mouse, stuck to an edge it
 * comes near, kept from the nearest corner when let go ({@link HudDrop}). A right click puts one
 * box back where it started, a button all of them. A box with nothing to say right now shows its
 * name, so it can be placed all the same.
 */
public final class HudEditorScreen extends Screen {
	private static final int OUTLINE = 0xFF4C7AB0;
	private static final int OUTLINE_GRABBED = 0xFFE8C34C;
	private static final int BUTTON = 0xFF3A3A44;
	private static final int BUTTON_HOVER = 0xFF4C7AB0;
	private static final int TEXT = 0xFFE8E8EC;
	private static final int BUTTON_WIDTH = 90;
	private static final int BUTTON_HEIGHT = 16;

	private final HudModule hud;
	private final Screen parent;
	private final Runnable onChange;
	/** The box being dragged, where it is shown now, and where in it the pointer took hold. */
	private HudPanel grabbed;
	private HudBox dragged;
	private int grabX;
	private int grabY;

	public HudEditorScreen(HudModule hud, Screen parent, Runnable onChange) {
		super(Component.literal(Lang.of(Keys.HUD_EDITOR_TITLE)));
		this.hud = hud;
		this.parent = parent;
		this.onChange = onChange;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The world shows through: the boxes are placed against what is really behind them. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		graphics.fill(0, 0, width, height, 0x30000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		super.extractRenderState(graphics, mouseX, mouseY, partial);

		for (HudPanel panel : shown()) {
			List<HudLine> lines = linesOf(panel);
			HudBox box = panel == grabbed ? dragged : boxOf(panel);
			boolean right = panel == grabbed ? dragged.x() + dragged.width() / 2.0 > width * 2 / 3.0
				: panel.placement().anchor().horizontal() > 0;
			HudDrawing.draw(graphics, font, panel.placement(), lines, box, right);
			graphics.outline(box.x() - 1, box.y() - 1, box.width() + 2, box.height() + 2, panel == grabbed ? OUTLINE_GRABBED : OUTLINE);
		}

		String hint = Lang.of(Keys.HUD_EDITOR_HINT);
		graphics.text(font, hint, (width - font.width(hint)) / 2, height / 2 - 30, TEXT, true);
		button(graphics, resetAllX(), buttonsY(), Lang.of(Keys.HUD_EDITOR_RESET_ALL), mouseX, mouseY);
		button(graphics, doneX(), buttonsY(), Lang.of(Keys.HUD_EDITOR_DONE), mouseX, mouseY);
	}

	private void button(GuiGraphicsExtractor graphics, int x, int y, String label, int mouseX, int mouseY) {
		boolean over = mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
		graphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, over ? BUTTON_HOVER : BUTTON);
		graphics.text(font, label, x + (BUTTON_WIDTH - font.width(label)) / 2, y + 4, TEXT, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x();
		double y = event.y();

		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && over(x, y, resetAllX())) {
			hud.panels().forEach(panel -> panel.placement().reset());
			onChange.run();
			return true;
		}

		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && over(x, y, doneX())) {
			onClose();
			return true;
		}

		Optional<HudPanel> under = under(x, y);

		if (under.isEmpty()) {
			return super.mouseClicked(event, doubleClick);
		}

		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			under.get().placement().reset();
			onChange.run();
			return true;
		}

		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			grabbed = under.get();
			dragged = boxOf(grabbed);
			grabX = (int) x - dragged.x();
			grabY = (int) y - dragged.y();
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (grabbed == null) {
			return super.mouseDragged(event, dragX, dragY);
		}

		dragged = HudDrop.snap((int) event.x() - grabX, (int) event.y() - grabY, dragged.width(), dragged.height(), width, height);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (grabbed == null) {
			return super.mouseReleased(event);
		}

		HudDrop.Placed placed = HudDrop.drop(dragged.x(), dragged.y(), dragged.width(), dragged.height(), width, height);
		grabbed.placement().move(placed.anchor(), placed.offsetX(), placed.offsetY());
		grabbed = null;
		dragged = null;
		onChange.run();
		return true;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	/** The topmost shown box under the pointer: the last drawn. */
	private Optional<HudPanel> under(double x, double y) {
		List<HudPanel> shown = shown();

		for (int i = shown.size() - 1; i >= 0; i--) {
			if (boxOf(shown.get(i)).contains(x, y)) {
				return Optional.of(shown.get(i));
			}
		}

		return Optional.empty();
	}

	private boolean over(double x, double y, int buttonX) {
		return x >= buttonX && x < buttonX + BUTTON_WIDTH && y >= buttonsY() && y < buttonsY() + BUTTON_HEIGHT;
	}

	private List<HudPanel> shown() {
		return hud.panels().stream().filter(panel -> panel.placement().shown()).toList();
	}

	/** What a box says, or its name while it has nothing to say. */
	private static List<HudLine> linesOf(HudPanel panel) {
		List<HudLine> lines = panel.lines();
		return lines.isEmpty() ? List.of(HudLine.of(Lang.of(Keys.setting("hud.group." + panel.id())))) : lines;
	}

	// --- where things are, for the tests ---

	/** Where a box is drawn in the editor now, null for one not shown. */
	public HudBox boxOf(String id) {
		return shown().stream().filter(panel -> panel.id().equals(id)).findFirst().map(this::boxOf).orElse(null);
	}

	private HudBox boxOf(HudPanel panel) {
		return HudDrawing.place(font, panel.placement(), linesOf(panel), width, height);
	}

	public int buttonsY() {
		return height / 2 - 12;
	}

	public int resetAllX() {
		return width / 2 - BUTTON_WIDTH - 4;
	}

	public int doneX() {
		return width / 2 + 4;
	}

	public int buttonCenterOffsetX() {
		return BUTTON_WIDTH / 2;
	}

	public int buttonCenterOffsetY() {
		return BUTTON_HEIGHT / 2;
	}
}
