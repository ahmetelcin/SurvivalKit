package com.merci.survivalkit.menu;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.settings.RegistryListSetting;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * A grid of things to add to a list, with a search box above it.
 *
 * <p>It does not close when something is picked. Building a preference list means adding
 * several things in a row, and a picker that shut after each one would have to be reopened
 * for every entry. What is already on the list is drawn faded instead of hidden, so the grid
 * does not shuffle under the cursor mid-click.
 */
public final class ItemPickerScreen extends Screen {
	private static final int MARGIN = 20;
	private static final int SEARCH_HEIGHT = 18;
	private static final int CELL = 20;
	private static final int ICON_INSET = 2;

	private static final int PANEL = 0xE0101014;
	private static final int PANEL_EDGE = 0xFF2C2C34;
	private static final int TEXT = 0xFFE8E8EC;
	private static final int TEXT_DIM = 0xFF9A9AA4;
	private static final int CELL_HOVER = 0x40FFFFFF;
	private static final int CELL_PICKED = 0x90101014;
	private static final int TRACK = 0xFF3A3A44;
	/** The column the magnifier sits in, matching the menu behind this screen. */
	private static final int SEARCH_ICON_WIDTH = 11;

	private final Screen parent;
	private final RegistryListSetting target;
	private final PickerModel model;
	private final Runnable onChange;

	private EditBox search;
	private int scroll;

	public ItemPickerScreen(Screen parent, RegistryListSetting target, Runnable onChange) {
		super(Component.literal(Lang.of(Keys.PICKER_TITLE)));
		this.parent = parent;
		this.target = target;
		this.onChange = onChange;

		// Timed and logged here rather than only in the game test, which sees a vanilla registry
		// and a few dozen items. How long this takes is a question about the pack the player
		// actually runs, and the answer has to be readable in that game's own log.
		long started = System.nanoTime();
		this.model = new PickerModel(ItemCatalogue.forKind(target.kind()), target::get);
		long took = (System.nanoTime() - started) / 1_000_000L;

		SurvivalKit.LOGGER.info("Eşya seçici açıldı: {} ms, katalogda {} eşya ({})",
				took, model.all().size(), target.kind());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		Rect2i frame = searchFrame();
		// Bordered off and inset past the magnifier, the same way the menu's box is built.
		search = new EditBox(font, frame.getX() + SEARCH_ICON_WIDTH, frame.getY() + 3,
				frame.getWidth() - SEARCH_ICON_WIDTH - 3, SEARCH_HEIGHT - 6,
				Component.literal(Lang.of(Keys.MENU_SEARCH)));
		search.setBordered(false);
		search.setHint(Component.literal(Lang.of(Keys.PICKER_SEARCH)));
		search.setResponder(value -> {
			model.setQuery(value);
			scroll = 0;
		});
		// Input only: drawn in extractRenderState after its frame, whose opaque background
		// otherwise covered what was typed.
		addWidget(search);
		setFocused(search);
		search.setFocused(true);
	}

	/** The edge drawn around the search box, and the room the box is laid out inside. */
	public Rect2i searchFrame() {
		return new Rect2i(MARGIN + 6, MARGIN + 6, width - 2 * MARGIN - 12, SEARCH_HEIGHT);
	}

	private int gridTop() {
		return MARGIN + 6 + SEARCH_HEIGHT + 8;
	}

	private int gridBottom() {
		return height - MARGIN - 18;
	}

	private int columns() {
		return Math.max(1, (width - 2 * MARGIN - 12) / CELL);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		super.extractRenderState(graphics, mouseX, mouseY, partial);

		graphics.fill(MARGIN, MARGIN, width - MARGIN, height - MARGIN, PANEL);
		graphics.fill(MARGIN, MARGIN, width - MARGIN, MARGIN + 1, PANEL_EDGE);
		graphics.fill(MARGIN, height - MARGIN - 1, width - MARGIN, height - MARGIN, PANEL_EDGE);

		Rect2i frame = searchFrame();
		graphics.fill(frame.getX(), frame.getY(), frame.getX() + frame.getWidth(),
				frame.getY() + frame.getHeight(), TRACK);
		graphics.outline(frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight(), PANEL_EDGE);
		SurvivalKitScreen.drawMagnifier(graphics, frame.getX() + 3, frame.getY() + 5, TEXT_DIM);
		// The box, its text and its hint go on top of the frame, not under it.
		search.extractRenderState(graphics, mouseX, mouseY, partial);

		drawGrid(graphics, mouseX, mouseY);

		String footer = Lang.of(Keys.PICKER_FOOTER, model.visible().size());
		graphics.text(font, Component.literal(footer), MARGIN + 6, height - MARGIN - 12, TEXT_DIM);
	}

	private void drawGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int columns = columns();
		int left = MARGIN + 6;
		int index = 0;

		for (ItemChoice choice : model.visible()) {
			int x = left + index % columns * CELL;
			int y = gridTop() + index / columns * CELL - scroll;
			index++;

			if (y + CELL < gridTop() || y > gridBottom()) {
				continue;
			}

			boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL
					&& mouseY >= gridTop() && mouseY <= gridBottom();

			if (hovered) {
				graphics.fill(x, y, x + CELL, y + CELL, CELL_HOVER);
			}

			graphics.item(stackOf(choice), x + ICON_INSET, y + ICON_INSET);

			// Already on the list: dimmed rather than removed, so the grid stays still.
			if (model.isPicked(choice)) {
				graphics.fill(x, y, x + CELL, y + CELL, CELL_PICKED);
			}

			if (hovered) {
				String note = model.isPicked(choice) ? " (" + Lang.of(Keys.PICKER_PICKED) + ")" : "";
				graphics.setTooltipForNextFrame(Component.literal(choice.displayName() + note), mouseX, mouseY);
			}
		}

		if (model.visible().isEmpty()) {
			graphics.text(font, Component.literal(Lang.of(Keys.PICKER_EMPTY)), left, gridTop() + 4, TEXT);
		}
	}

	private static ItemStack stackOf(ItemChoice choice) {
		return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(choice.id())));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}

		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT
				|| event.y() < gridTop() || event.y() > gridBottom()) {
			return false;
		}

		int columns = columns();
		int column = (int) ((event.x() - MARGIN - 6) / CELL);
		int row = (int) ((event.y() + scroll - gridTop()) / CELL);

		if (column < 0 || column >= columns || row < 0) {
			return false;
		}

		int index = row * columns + column;

		if (index >= model.visible().size()) {
			return false;
		}

		target.add(model.visible().get(index).id());
		onChange.run();
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int rows = (model.visible().size() + columns() - 1) / columns();
		int content = rows * CELL;
		int viewport = gridBottom() - gridTop();

		scroll = Math.clamp(scroll - (int) Math.round(scrollY * CELL), 0, Math.max(0, content - viewport));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE) {
			// The search is undone first, the same as in the menu behind this screen.
			if (!search.getValue().isEmpty()) {
				search.setValue("");
				return true;
			}

			onClose();
			return true;
		}

		return super.keyPressed(event);
	}

	/** Back to the menu rather than out of every screen, which is where the list is. */
	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	/** For the game test, which has to type into the search box. */
	public EditBox searchBox() {
		return search;
	}

	public PickerModel model() {
		return model;
	}

	/** Where the nth visible cell sits, so a test can click it. */
	public int cellCentreX(int index) {
		return MARGIN + 6 + index % columns() * CELL + CELL / 2;
	}

	public int cellCentreY(int index) {
		return gridTop() + index / columns() * CELL - scroll + CELL / 2;
	}
}
