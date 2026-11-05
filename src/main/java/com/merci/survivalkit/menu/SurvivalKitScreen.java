package com.merci.survivalkit.menu;

import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.preset.Presets;
import com.merci.survivalkit.profile.Profiles;
import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.settings.RegistryListSetting;
import com.merci.survivalkit.settings.Setting;
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.settings.TextSetting;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The two-panel menu: modules down the left, the selected module's settings down the right.
 *
 * <p>This class draws and takes clicks. What there is to draw — which modules are listed, in
 * what order, which lines are visible — belongs to {@link MenuModel}, which knows nothing
 * about screens and is tested without one.
 *
 * <p>Controls are drawn by hand rather than built from vanilla widgets, so a row can carry a
 * label, a control, a tooltip and a reset arrow at one fixed height and be hit-tested in one
 * place. The exception is text entry, which is genuinely fiddly and is left to {@link EditBox}.
 */
public final class SurvivalKitScreen extends Screen {
	private static final int MARGIN = 12;
	/** The bar above the two panels: the HUD editor's button, and the profiles. */
	private static final int BAR = 20;
	private static final int BAR_BUTTON_HEIGHT = 14;
	private static final int BAR_BUTTON_PADDING = 8;
	private static final int LEFT_WIDTH = 132;
	private static final int SEARCH_HEIGHT = 16;
	private static final int GUTTER = 10;
	private static final int ROW_HEIGHT = 18;
	private static final int SETTING_HEIGHT = 22;
	private static final int TOGGLE_WIDTH = 26;
	private static final int TOGGLE_HEIGHT = 12;
	private static final int INDENT = 10;
	private static final int CONTROL_WIDTH = 96;
	private static final int RESET_WIDTH = 16;
	private static final int SCROLL_STEP = 14;
	/** Tall enough for a sixteen-pixel item icon and the line it names. */
	private static final int ENTRY_HEIGHT = 18;
	/** The line a setting's note takes under its row. */
	private static final int NOTE_HEIGHT = 12;
	/** The item icon beside a list entry, at the size the game draws one. */
	private static final int ICON = 16;
	/** The column the magnifier sits in, at the left end of the search box. */
	private static final int SEARCH_ICON_WIDTH = 11;
	/** The undo arrow's own size, which its drawing is plotted for and does not scale from. */
	private static final int RESET_GLYPH = 12;
	private static final int RESULT_HEIGHT = 18;
	/**
	 * How wide the settings column is allowed to get, in GUI units. A 1920 screen gives the
	 * right-hand panel some 780 units; a label at one end and its switch at the other is a
	 * distance the eye has to carry, so the column stops here and the rest is margin.
	 */
	private static final int CONTENT_WIDTH = 400;

	/** How long an opened setting stays lit. Long enough to find, short enough to forget. */
	private static final long HIGHLIGHT_MS = 1500;

	private static final int PANEL = 0xC0101014;
	private static final int PANEL_EDGE = 0xFF2C2C34;
	private static final int ROW_SELECTED = 0xFF2E4A66;
	private static final int ROW_HOVER = 0x40FFFFFF;
	private static final int TEXT = 0xFFE8E8EC;
	private static final int TEXT_DIM = 0xFF9A9AA4;
	private static final int TOGGLE_ON = 0xFF4C9A4C;
	private static final int TOGGLE_OFF = 0xFF54545E;
	private static final int KNOB = 0xFFE8E8EC;
	private static final int TRACK = 0xFF3A3A44;
	private static final int FILL = 0xFF4C7AB0;
	private static final int BUTTON = 0xFF3A3A44;
	private static final int BUTTON_ON = 0xFF4C7AB0;
	/** The open list of an enum setting: opaque, because it is drawn over other rows. */
	private static final int DROPDOWN = 0xFF26262E;
	private static final int SCROLLBAR = 0xFF4A4A56;
	private static final int WARNING = 0xFFE8C34C;

	private static final int LISTENING = 0xFF6A5ACD;
	private static final int HIGHLIGHT = 0x4C7AB0;

	private final MenuModel model;
	/** One box per text setting, kept between frames so a half-typed word is not lost. */
	private final Map<String, EditBox> boxes = new HashMap<>();
	/** Each box's frame, laid out from its row; the box itself is only a line tall inside it. */
	private final Map<String, Rect2i> boxFrames = new HashMap<>();
	/** The hint text each box actually had painted into it last frame, or "" for none. */
	private final Map<String, String> painted = new HashMap<>();
	/** Where each row ended up last frame, so a click lands on what the eye sees. */
	private final List<PlacedRow> placed = new ArrayList<>();

	private Runnable onChange = () -> { };
	private UnaryOperator<Screen> hudEditor;
	private ProfileBar profileBar;
	private PresetStrip presetStrip;
	/** Where the preset strip sits in the scrolled panel, this frame, and how tall it is. */
	private int presetTop;
	private int presetHeight;
	private int scroll;
	private int contentHeight;
	/** The int setting currently being typed into, if any. */
	private String editingInt;
	/** The keybind setting waiting for a key, if any. */
	private KeybindSetting listening;
	/** Where the open list of an enum setting is, and which setting it belongs to. */
	private final Dropdown dropdown = new Dropdown();
	/** The setting whose list is open, kept beside {@link #dropdown} so its values are to hand. */
	private EnumSetting<?> openChoice;
	/** Where that setting's button was drawn this frame, so the list follows it as it scrolls. */
	private int openChoiceButtonTop;
	/** Whether that row was drawn at all this frame; a row scrolled out of sight is not. */
	private boolean openChoiceOnScreen;
	/** Set when a key was taken by a widget, so the character it also sends is dropped. */
	private boolean keyTaken;
	private Supplier<List<KeyUse>> keyUses = List::of;
	/** The setting a search result opened, and when it stops being lit. */
	private String highlighted;
	private long highlightUntil;
	/** Tracks the menu key across its press and its release. */
	private final MenuKeyRule menuKey = new MenuKeyRule();
	/** The list entry being dragged, and where it started. */
	private RegistryListSetting draggingList;
	private int draggingIndex = -1;

	private record PlacedRow(MenuRow row, int y, int height) {
	}

	/** The module search, which is where typing goes unless something else claims it. */
	private EditBox search;

	/** This frame's pointer and tick fraction, for boxes drawn from deep inside the layout. */
	private int frameMouseX;
	private int frameMouseY;
	private float framePartial;

	public SurvivalKitScreen(MenuModel model) {
		super(Component.literal("SurvivalKit"));
		this.model = model;
	}

	/** Called whenever a setting changes, so the save can be put off until things go quiet. */
	public void onChange(Runnable onChange) {
		this.onChange = onChange;
	}

	/** Everything else already listening for a key, for the clash warning. */
	public void keyUses(Supplier<List<KeyUse>> keyUses) {
		this.keyUses = keyUses;
	}

	/** The profiles the bar shows and switches between. */
	public void profiles(Supplier<Profiles> profiles) {
		this.profileBar = new ProfileBar(profiles, this::profileSwitched, () -> minecraft.gui.setScreen(new ServerRulesScreen(this, profiles.get())));
	}

	/** The presets the strip at the top of each module's settings offers. */
	public void presets(Supplier<Presets> presets) {
		this.presetStrip = new PresetStrip(presets, model.presetUndo(), () -> {
			forgetBoxes();
			onChange.run();
		});
	}

	/** Another profile under the menu: nothing a preset did is this one's to undo. */
	private void profileSwitched() {
		model.presetUndo().clear();

		if (presetStrip != null) {
			presetStrip.cancel();
		}

		forgetBoxes();
	}

	/**
	 * The settings changed under the menu — another profile, a preset: the text boxes, which keep
	 * what they were given, are made again from the settings as they are now.
	 */
	private void forgetBoxes() {
		boxes.values().forEach(this::removeWidget);
		boxes.clear();
		boxFrames.clear();
		painted.clear();
	}

	/** The preset strip, for the tests: where its buttons are this frame. */
	public PresetStrip presetStrip() {
		return presetStrip;
	}

	/** What the last preset applied did, while it is still said; empty otherwise. */
	public String presetStatus() {
		return presetStrip == null ? "" : presetStrip.status();
	}

	/** Where that line is drawn, on the module title's line at the right. */
	public Rect2i presetStatusBox() {
		String status = presetStatus();
		return new Rect2i(contentRight() - font.width(status), top() + 8, font.width(status), font.lineHeight);
	}

	/** The bar's profile part, for the tests. */
	public ProfileBar profileBar() {
		return profileBar;
	}

	/** What the bar's "Edit HUD" opens: the HUD editor over this menu, coming back to it. */
	public void hudEditor(UnaryOperator<Screen> hudEditor) {
		this.hudEditor = hudEditor;
	}

	/** The world keeps running underneath: this is a tool, not a pause menu. */
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		Rect2i frame = searchFrame();
		// Bordered off: the frame is drawn here instead, so the magnifier can live inside it
		// rather than beside a box that already has an edge of its own.
		search = new EditBox(font, frame.getX() + SEARCH_ICON_WIDTH, frame.getY() + 2,
				frame.getWidth() - SEARCH_ICON_WIDTH - 3, SEARCH_HEIGHT - 4,
				Component.literal(Lang.of(Keys.MENU_SEARCH)));
		search.setBordered(false);
		search.setValue(model.query());
		search.setResponder(model::setQuery);
		// Input only: the box is drawn by drawSearchBox, after its frame. Left to the screen's
		// widget pass it was drawn first, and the frame's opaque background then covered what
		// was typed. The hint is drawn there too, so the widget is given none of its own.
		addWidget(search);

		// The box does not start with the keyboard. Typing still reaches it without a click -
		// see charTyped - but a box that holds the keyboard from the first frame turns every
		// key into a letter, the keybind widget included, and never gives it back.
		setFocused(null);
	}

	/** Where the two panels start, below the bar. */
	private int top() {
		return MARGIN + BAR;
	}

	private int listTop() {
		return top() + 16 + SEARCH_HEIGHT + 4;
	}

	private int rightX() {
		return MARGIN + LEFT_WIDTH + GUTTER;
	}

	private int rightWidth() {
		return width - rightX() - MARGIN;
	}

	private int settingsTop() {
		Module module = model.selected().orElse(null);
		int y = top() + 20;

		if (module != null) {
			y += wrappedHeight(module.description()) + 16;
		}

		return y;
	}

	private int settingsBottom() {
		return height - MARGIN - 6;
	}

	private int controlX() {
		return contentRight() - RESET_WIDTH - 4 - CONTROL_WIDTH;
	}

	private int resetX() {
		return contentRight() - RESET_WIDTH;
	}

	// --- drawing ---

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		super.extractRenderState(graphics, mouseX, mouseY, partial);
		frameMouseX = mouseX;
		frameMouseY = mouseY;
		framePartial = partial;

		panel(graphics, MARGIN, top(), MARGIN + LEFT_WIDTH, height - MARGIN);
		panel(graphics, rightX(), top(), width - MARGIN, height - MARGIN);
		drawBar(graphics, mouseX, mouseY);

		graphics.text(font, Component.literal(Lang.of(Keys.MENU_MODULES)), MARGIN + 6, top() + 5, TEXT_DIM);
		drawSearchBox(graphics);
		drawModules(graphics, mouseX, mouseY);
		drawResults(graphics, mouseX, mouseY);
		drawSelectedModule(graphics, mouseX, mouseY);

		if (profileBar != null) {
			profileBar.drawList(graphics, font, mouseX, mouseY);
		}
	}

	/**
	 * The search box's own edge and the magnifier inside it. The widget draws neither: it is
	 * bordered off so the glass can sit in the same frame as the text rather than beside it.
	 */
	private void drawSearchBox(GuiGraphicsExtractor graphics) {
		Rect2i frame = searchFrame();

		graphics.fill(frame.getX(), frame.getY(), frame.getX() + frame.getWidth(),
				frame.getY() + frame.getHeight(), TRACK);
		graphics.outline(frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight(), PANEL_EDGE);
		drawMagnifier(graphics, frame.getX() + 3, frame.getY() + 4, TEXT_DIM);
		search.extractRenderState(graphics, frameMouseX, frameMouseY, framePartial);

		String hint = searchHintOnScreen();

		if (!hint.isBlank()) {
			graphics.text(font, Component.literal(hint), search.getX() + 1,
					frame.getY() + (SEARCH_HEIGHT - font.lineHeight) / 2 + 1, TEXT_DIM);
		}
	}

	/**
	 * A magnifier at seven pixels square, drawn from rectangles. The font has no glyph for
	 * one, and a texture for a single icon is a resource pack's worth of ceremony for
	 * something this size.
	 */
	static void drawMagnifier(GuiGraphicsExtractor graphics, int x, int y, int colour) {
		graphics.fill(x + 2, y, x + 4, y + 1, colour);
		graphics.fill(x + 1, y + 1, x + 2, y + 4, colour);
		graphics.fill(x + 4, y + 1, x + 5, y + 4, colour);
		graphics.fill(x + 2, y + 4, x + 4, y + 5, colour);
		graphics.fill(x + 4, y + 4, x + 6, y + 6, colour);
		graphics.fill(x + 5, y + 5, x + 7, y + 7, colour);
	}

	private void panel(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
		graphics.fill(x0, y0, x1, y1, PANEL);
		graphics.fill(x0, y0, x1, y0 + 1, PANEL_EDGE);
		graphics.fill(x0, y1 - 1, x1, y1, PANEL_EDGE);
		graphics.fill(x0, y0, x0 + 1, y1, PANEL_EDGE);
		graphics.fill(x1 - 1, y0, x1, y1, PANEL_EDGE);
	}

	/** The bar above the panels: "Edit HUD" at its right end. */
	private void drawBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (profileBar != null) {
			profileBar.draw(graphics, font, MARGIN, MARGIN - 2, mouseX, mouseY, framePartial);
		}

		Rect2i button = hudEditorButton();
		boolean over = button.contains(mouseX, mouseY);
		graphics.fill(button.getX(), button.getY(), button.getX() + button.getWidth(), button.getY() + button.getHeight(), over ? BUTTON_ON : BUTTON);
		graphics.outline(button.getX(), button.getY(), button.getWidth(), button.getHeight(), PANEL_EDGE);
		String label = Lang.of(Keys.MENU_EDIT_HUD);
		graphics.text(font, Component.literal(label), button.getX() + BAR_BUTTON_PADDING, button.getY() + 3, TEXT);
	}

	/** "Edit HUD", at the right end of the bar, as wide as its label. */
	public Rect2i hudEditorButton() {
		int labelWidth = font.width(Lang.of(Keys.MENU_EDIT_HUD));
		int buttonWidth = labelWidth + 2 * BAR_BUTTON_PADDING;
		return new Rect2i(width - MARGIN - buttonWidth, MARGIN - 2, buttonWidth, BAR_BUTTON_HEIGHT);
	}

	private void drawModules(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int y = listTop();

		for (Module module : model.visibleModules()) {
			boolean selected = model.selected().filter(chosen -> chosen == module).isPresent();

			if (selected) {
				graphics.fill(MARGIN + 2, y, MARGIN + LEFT_WIDTH - 2, y + ROW_HEIGHT, ROW_SELECTED);
			} else if (overRow(mouseX, mouseY, y)) {
				graphics.fill(MARGIN + 2, y, MARGIN + LEFT_WIDTH - 2, y + ROW_HEIGHT, ROW_HOVER);
			}

			graphics.text(font, Component.literal(module.displayName()), MARGIN + 8, y + 5, TEXT);
			drawToggle(graphics, toggleX(), y + (ROW_HEIGHT - TOGGLE_HEIGHT) / 2, module.isEnabled());
			y += ROW_HEIGHT;
		}
	}

	/** Where the found settings start: under the modules, with a line between. */
	private int resultsTop() {
		return listTop() + model.visibleModules().size() * ROW_HEIGHT + 7;
	}

	/**
	 * What the search found, listed under the modules: the setting on top, the module it
	 * lives in underneath, because the same name turns up in more than one module.
	 */
	private void drawResults(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		List<SearchHit> results = model.results();

		if (results.isEmpty()) {
			return;
		}

		int separator = resultsTop() - 4;
		graphics.fill(MARGIN + 6, separator, MARGIN + LEFT_WIDTH - 6, separator + 1, PANEL_EDGE);

		int y = resultsTop();

		for (SearchHit hit : results) {
			if (y + RESULT_HEIGHT > height - MARGIN) {
				return;
			}

			if (overResult(mouseX, mouseY, y)) {
				graphics.fill(MARGIN + 2, y, MARGIN + LEFT_WIDTH - 2, y + RESULT_HEIGHT, ROW_HOVER);
			}

			graphics.text(font, Component.literal(hit.displayName()), MARGIN + 8, y + 1, TEXT);
			graphics.text(font, Component.literal(hit.module().displayName()), MARGIN + 8, y + 10, TEXT_DIM);
			y += RESULT_HEIGHT;
		}
	}

	private boolean overResult(double mouseX, double mouseY, int rowY) {
		return mouseX >= MARGIN + 2 && mouseX <= MARGIN + LEFT_WIDTH - 2
				&& mouseY >= rowY && mouseY < rowY + RESULT_HEIGHT;
	}

	private int toggleX() {
		return MARGIN + LEFT_WIDTH - TOGGLE_WIDTH - 8;
	}

	private void drawToggle(GuiGraphicsExtractor graphics, int x, int y, boolean on) {
		graphics.fill(x, y, x + TOGGLE_WIDTH, y + TOGGLE_HEIGHT, on ? TOGGLE_ON : TOGGLE_OFF);
		int knobX = on ? x + TOGGLE_WIDTH - TOGGLE_HEIGHT + 2 : x + 2;
		graphics.fill(knobX, y + 2, knobX + TOGGLE_HEIGHT - 4, y + TOGGLE_HEIGHT - 2, KNOB);
	}

	private void drawSelectedModule(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Module module = model.selected().orElse(null);
		placed.clear();
		hideAllBoxes();

		if (module == null) {
			return;
		}

		int x = rightX() + 8;

		graphics.text(font, Component.literal(module.displayName()), x, top() + 8, TEXT);

		// What the last preset did, on the title's line, where the strip scrolled away cannot hide it.
		String status = presetStatus();

		if (!status.isEmpty()) {
			graphics.text(font, Component.literal(status), contentRight() - font.width(status), top() + 8, TOGGLE_ON);
		}

		graphics.textWithWordWrap(font, Component.literal(module.description()), x, top() + 20, contentRight() - x, TEXT_DIM);
		graphics.fill(x, settingsTop() - 8, contentRight(), settingsTop() - 7, PANEL_EDGE);

		drawSettings(graphics, mouseX, mouseY);
		drawScrollbar(graphics);
	}

	private void drawSettings(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int top = settingsTop();
		int bottom = settingsBottom();
		int y = top - scroll;
		openChoiceOnScreen = false;

		// Rows are drawn whole, so a scrolled panel would otherwise spill its top row over the
		// module name and description above it.
		graphics.enableScissor(rightX() + 1, top, width - MARGIN - 1, bottom);

		// The presets come first and scroll with the rows, so they cost no room once scrolled past.
		presetTop = y;
		presetHeight = presetStripHeight(y);

		if (presetHeight > 0) {
			presetStrip.draw(graphics, font, mouseX, mouseY, framePartial);
			y += presetHeight;
		}

		for (MenuRow row : model.rows()) {
			int rowHeight = rowHeight(row);

			// Only rows with something on screen are drawn, but every row is placed, so the
			// hit testing and the drawing never disagree.
			if (y + rowHeight > top && y < bottom) {
				if (highlightedSetting().filter(id -> id.equals(row.id())).isPresent()) {
					graphics.fill(contentLeft() - 4, y, contentRight() + 4, y + rowHeight, highlightColour());
				} else if (hoveredRowId(mouseX, mouseY).filter(id -> id.equals(row.id())).isPresent()) {
					// A faint band the width of the column, so the label and the control on the
					// far side of it read as one line.
					graphics.fill(contentLeft() - 4, y, contentRight() + 4, y + rowHeight, ROW_HOVER);
				}

				drawRow(graphics, row, y, mouseX, mouseY);
			}

			placed.add(new PlacedRow(row, y, rowHeight));
			y += rowHeight;
		}

		// Last, so it covers the rows below it instead of being covered by them, and still
		// inside the panel's scissor so it cannot spill over the module name above.
		drawOpenList(graphics, mouseX, mouseY);

		graphics.disableScissor();

		contentHeight = y + scroll - top;
		clampScroll();
	}

	/** Lays the preset strip out at this height for the selected module; 0 when there is none. */
	private int presetStripHeight(int y) {
		Module module = model.selected().orElse(null);

		if (presetStrip == null || module == null) {
			return 0;
		}

		return presetStrip.layout(font, module, contentLeft(), y, contentRight() - contentLeft());
	}

	/** A setting with a note grows a line under everything else in its row to say it. */
	private int rowHeight(MenuRow row) {
		return noteOf(row).isPresent() ? baseHeight(row) + NOTE_HEIGHT : baseHeight(row);
	}

	private static Optional<String> noteOf(MenuRow row) {
		return row.node() instanceof Setting<?> setting ? setting.note() : Optional.empty();
	}

	/** A keybind that clashes grows a line to say so, rather than putting it somewhere cramped. */
	private int baseHeight(MenuRow row) {
		if (row.isGroup()) {
			return ROW_HEIGHT;
		}

		if (row.node() instanceof KeybindSetting keybind && warningFor(keybind).isPresent()) {
			return SETTING_HEIGHT + 10;
		}

		// A list opens out: its own line, one per entry, and the line that adds another.
		if (row.node() instanceof RegistryListSetting list) {
			return SETTING_HEIGHT + (list.get().size() + 1) * ENTRY_HEIGHT;
		}

		return SETTING_HEIGHT;
	}

	/** Where the nth entry of a list sits inside its row. */
	private static int entryY(int rowY, int index) {
		return rowY + SETTING_HEIGHT + index * ENTRY_HEIGHT;
	}

	private Optional<String> warningFor(KeybindSetting keybind) {
		String ownerId = model.selected().map(module -> KeyUse.ownerOf(module, keybind)).orElse("");
		return KeyConflicts.warningFor(keybind.get(), ownerId, keyUses.get());
	}

	private void drawRow(GuiGraphicsExtractor graphics, MenuRow row, int y, int mouseX, int mouseY) {
		int x = rightX() + 8 + row.depth() * INDENT;

		if (row.node() instanceof SettingGroup group) {
			String arrow = model.isCollapsed(group) ? "▶" : "▼";
			graphics.text(font, Component.literal(arrow + " " + group.displayName()), x, y + 5, TEXT_DIM);
			return;
		}

		Setting<?> setting = (Setting<?>) row.node();
		graphics.text(font, Component.literal(setting.displayName()), x, y + 7, TEXT);

		drawControl(graphics, setting, y);
		setting.note().ifPresent(note -> graphics.text(font, Component.literal(note), x, y + baseHeight(row), TEXT_DIM));

		// The arrow only appears once there is something to undo, so a row that has never
		// been touched stays quiet. After a preset it undoes the preset, then the default.
		if (resettable(setting)) {
			drawResetArrow(graphics, y);

			if (overResetAt(mouseX, mouseY, y)) {
				String says = Lang.of(model.presetUndo().canUndo(setting) ? Keys.MENU_PRESET_UNDO : Keys.MENU_RESET);
				graphics.setTooltipForNextFrame(Component.literal(says), mouseX, mouseY);
			}
		}

		if (overLabel(mouseX, mouseY, x, y, setting) && !setting.tooltip().isBlank()) {
			graphics.setTooltipForNextFrame(Component.literal(setting.tooltip()), mouseX, mouseY);
		}
	}

	/** Whether the row's ↺ has anything to do: a preset to undo, or a value off its default. */
	private boolean resettable(Setting<?> setting) {
		return !setting.isDefault() || model.presetUndo().canUndo(setting);
	}

	private boolean overResetAt(double mouseX, double mouseY, int rowY) {
		return mouseX >= resetX() && mouseX <= resetX() + RESET_WIDTH && mouseY >= rowY && mouseY < rowY + SETTING_HEIGHT;
	}

	/**
	 * The undo arrow: a ring with a bite out of the top right and an arrowhead at the gap,
	 * drawn from rectangles.
	 *
	 * <p>It used to be the character "↺", which the game's font has no glyph for — every
	 * screenshot of this menu has it as the empty box the font draws for anything it does
	 * not know. Drawn by hand it is both an arrow and the size of the box it is hit-tested
	 * in, rather than nine pixels of text in the middle of it.
	 */
	private void drawResetArrow(GuiGraphicsExtractor graphics, int y) {
		Rect2i glyph = resetGlyphBox(y);
		int x = glyph.getX();
		int top = glyph.getY();

		// A twelve by twelve circle, one pixel thick, open at the top right — plotted from
		// the distance to its centre and written out as the rows that came back, because a
		// circle drawn freehand out of rectangles is a rounded square.
		graphics.fill(x + 2, top + 2, x + 4, top + 3, TEXT_DIM);
		graphics.fill(x + 1, top + 3, x + 3, top + 4, TEXT_DIM);
		graphics.fill(x + 1, top + 4, x + 2, top + 5, TEXT_DIM);
		graphics.fill(x + 1, top + 5, x + 2, top + 6, TEXT_DIM);
		graphics.fill(x + 10, top + 5, x + 11, top + 6, TEXT_DIM);
		graphics.fill(x + 1, top + 6, x + 2, top + 7, TEXT_DIM);
		graphics.fill(x + 10, top + 6, x + 11, top + 7, TEXT_DIM);
		graphics.fill(x + 1, top + 7, x + 2, top + 8, TEXT_DIM);
		graphics.fill(x + 10, top + 7, x + 11, top + 8, TEXT_DIM);
		graphics.fill(x + 1, top + 8, x + 3, top + 9, TEXT_DIM);
		graphics.fill(x + 9, top + 8, x + 11, top + 9, TEXT_DIM);
		graphics.fill(x + 2, top + 9, x + 4, top + 10, TEXT_DIM);
		graphics.fill(x + 8, top + 9, x + 10, top + 10, TEXT_DIM);
		graphics.fill(x + 3, top + 10, x + 9, top + 11, TEXT_DIM);

		// The head, carrying the circle on round through the gap it leaves.
		graphics.fill(x + 6, top, x + 10, top + 1, TEXT_DIM);
		graphics.fill(x + 3, top + 1, x + 10, top + 2, TEXT_DIM);
		graphics.fill(x + 8, top + 2, x + 10, top + 3, TEXT_DIM);
		graphics.fill(x + 9, top + 3, x + 10, top + 4, TEXT_DIM);
	}

	/**
	 * The square the arrow is painted in, centred in the box that answers the click.
	 *
	 * <p>{@link #RESET_GLYPH} is not a size the drawing scales to: the circle is a plotted
	 * list of rows at exactly that size. Changing it means plotting the circle again.
	 */
	private Rect2i resetGlyphBox(int rowY) {
		return new Rect2i(resetX() + (RESET_WIDTH - RESET_GLYPH) / 2,
				rowY + (SETTING_HEIGHT - RESET_GLYPH) / 2, RESET_GLYPH, RESET_GLYPH);
	}

	private void drawControl(GuiGraphicsExtractor graphics, Setting<?> setting, int y) {
		int x = controlX();
		int centre = y + (SETTING_HEIGHT - TOGGLE_HEIGHT) / 2;

		if (setting instanceof BoolSetting bool) {
			drawToggle(graphics, x + CONTROL_WIDTH - TOGGLE_WIDTH, centre, bool.get());
			return;
		}

		if (setting instanceof IntSetting number) {
			if (setting.id().equals(editingInt)) {
				placeBox(setting, x, y);
				drawBoxFrame(graphics, setting);
			} else {
				drawSlider(graphics, number, x, centre);
			}

			return;
		}

		if (setting instanceof KeybindSetting keybind) {
			drawKeybind(graphics, keybind, x, y, centre);
			return;
		}

		if (setting instanceof EnumSetting<?> choice) {
			drawChoice(graphics, choice, x, centre);
			return;
		}

		if (setting instanceof TextSetting) {
			placeBox(setting, x, y);
			drawBoxFrame(graphics, setting);
			return;
		}

		if (setting instanceof RegistryListSetting list) {
			drawList(graphics, list, y);
			return;
		}

		// Lists and keybinds get their own controls in later steps; until then, the value.
		String value = String.valueOf(setting.get());
		String shown = value.length() <= 20 ? value : value.substring(0, 19) + "…";
		graphics.text(font, Component.literal(shown), x + CONTROL_WIDTH - font.width(shown), y + 7, TEXT_DIM);
	}

	/**
	 * A button showing the key in words, and while it is waiting for one, a prompt in its
	 * place. A clash is spelled out underneath in yellow rather than refused: the player may
	 * well mean it, and the game will simply do both things at once.
	 */
	private void drawKeybind(GuiGraphicsExtractor graphics, KeybindSetting setting, int x, int y, int centre) {
		boolean waiting = setting == listening;
		graphics.fill(x, centre - 2, x + CONTROL_WIDTH, centre + TOGGLE_HEIGHT + 2, waiting ? LISTENING : BUTTON);

		String label = waiting ? Lang.of(Keys.MENU_PRESS_KEY) : KeyNames.readable(setting.get(), SurvivalKitScreen::fromLayout);
		graphics.text(font, Component.literal(label), x + (CONTROL_WIDTH - font.width(label)) / 2, centre + 2, TEXT);

		if (!waiting) {
			warningFor(setting).ifPresent(warning ->
					graphics.text(font, Component.literal("⚠ " + warning),
							x + CONTROL_WIDTH - font.width("⚠ " + warning), y + SETTING_HEIGHT, WARNING));
		}
	}

	/**
	 * The entries under a list setting: a handle to drag by, the name, and a cross to drop
	 * it, then a line to add another.
	 *
	 * <p>An id this build has never heard of is not thrown away — the list keeps it, because
	 * the mod that knew it may come back. It is drawn greyed and labelled so the player can
	 * see why it does nothing.
	 */
	private void drawList(GuiGraphicsExtractor graphics, RegistryListSetting list, int rowY) {
		int x = listLeft(list.id());
		int iconX = entryIconX(list.id());
		int labelX = entryLabelX(list.id());
		List<String> entries = list.get();
		// Where the words sit inside a row that is now as tall as an item icon.
		int textOffset = (ENTRY_HEIGHT - font.lineHeight) / 2;

		for (int index = 0; index < entries.size(); index++) {
			String id = entries.get(index);
			int y = entryY(rowY, index);
			boolean dragged = list == draggingList && index == draggingIndex;

			if (dragged) {
				graphics.fill(x - 4, y, contentRight(), y + ENTRY_HEIGHT, ROW_SELECTED);
			}

			graphics.text(font, Component.literal("≡"), x, y + textOffset, TEXT_DIM);

			// The icon says at a glance what the name says on reading. An id from a mod that
			// is not installed has no icon to show, and is left with its name alone rather
			// than given a stand-in that would read as a real item.
			iconFor(id).ifPresent(stack ->
					graphics.item(stack, iconX, y + (ENTRY_HEIGHT - ICON) / 2));

			graphics.text(font, Component.literal(entryLabel(id)), labelX, y + textOffset, entryColor(id));

			if (isMissing(id)) {
				int nameWidth = font.width(id);
				graphics.text(font, Component.literal(missingMark()), labelX + 4 + nameWidth,
						y + textOffset, missingMarkColor());
			}

			graphics.text(font, Component.literal("✕"), removeX(), y + textOffset, TEXT_DIM);
		}

		int addY = entryY(rowY, entries.size());
		graphics.text(font, Component.literal(Lang.of(Keys.MENU_ADD)), x, addY + textOffset, TEXT_DIM);
	}

	/** The cross that drops an entry, in the same column as every other control's right edge. */
	private int removeX() {
		return contentRight() - 12;
	}

	/** The game's name for an id, or null when nothing in this build answers to it. */
	private static String nameOf(String id) {
		try {
			Identifier key = Identifier.parse(id);

			if (!BuiltInRegistries.ITEM.containsKey(key)) {
				return null;
			}

			return new ItemStack(BuiltInRegistries.ITEM.getValue(key)).getHoverName().getString();
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** What the keyboard layout calls this key — the same source Controls uses. */
	public static String fromLayout(String keyName) {
		try {
			return InputConstants.getKey(keyName).getDisplayName().getString();
		} catch (IllegalArgumentException e) {
			return keyName;
		}
	}

	private void drawSlider(GuiGraphicsExtractor graphics, IntSetting setting, int x, int y) {
		int trackWidth = CONTROL_WIDTH - 28;
		graphics.fill(x, y + 4, x + trackWidth, y + 8, TRACK);

		double span = Math.max(1, setting.max() - setting.min());
		int filled = (int) Math.round((setting.get() - setting.min()) / span * trackWidth);
		graphics.fill(x, y + 4, x + filled, y + 8, FILL);

		int knobX = Math.clamp(x + filled - 2, x, x + trackWidth - 4);
		graphics.fill(knobX, y, knobX + 4, y + TOGGLE_HEIGHT, KNOB);

		String value = String.valueOf(setting.get());
		graphics.text(font, Component.literal(value), x + CONTROL_WIDTH - font.width(value), y + 2, TEXT);
	}

	private void drawChoice(GuiGraphicsExtractor graphics, EnumSetting<?> setting, int x, int y) {
		List<?> options = setting.options();

		if (!fitsSideBySide(setting)) {
			// Too many, or too wide once translated, to sit side by side: one button that
			// opens a list of them. The arrow says so, and pressing the button does it —
			// which it did not always: the arrow was drawn over a button that quietly
			// divided its own width between the values it was no longer showing.
			String label = optionLabel(setting, setting.get());
			graphics.fill(x, y - 2, x + CONTROL_WIDTH, y + TOGGLE_HEIGHT + 2, BUTTON);
			graphics.text(font, Component.literal(label), x + 4, y + 2, TEXT);
			graphics.text(font, Component.literal("▾"), x + CONTROL_WIDTH - 10, y + 2, TEXT_DIM);

			// Not drawn here: a list drawn mid-column is covered by every row after it. The
			// row only says where it is, and the list is drawn once all the rows are down.
			if (dropdown.isOpen(setting.id())) {
				openChoiceButtonTop = y - 2;
				openChoiceOnScreen = true;
			}

			return;
		}

		int each = CONTROL_WIDTH / options.size();

		for (int index = 0; index < options.size(); index++) {
			Object option = options.get(index);
			boolean chosen = option == setting.get();
			int left = x + index * each;
			graphics.fill(left, y - 2, left + each - 2, y + TOGGLE_HEIGHT + 2, chosen ? BUTTON_ON : BUTTON);
			String label = optionLabel(setting, option);
			graphics.text(font, Component.literal(label), left + (each - 2 - font.width(label)) / 2, y + 2,
					chosen ? TEXT : TEXT_DIM);
		}
	}

	/**
	 * The open list, drawn over whatever is under it.
	 *
	 * <p>Its position comes from the row that was drawn this frame, so a scrolled panel moves
	 * the list with the button rather than leaving it behind. If that row was not drawn at all
	 * — scrolled out of sight — there is nothing here to draw and nothing to click either:
	 * {@link #mouseScrolled} closes the list for exactly that reason.
	 */
	private void drawOpenList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (openChoice == null || !openChoiceOnScreen) {
			return;
		}

		EnumSetting<?> setting = openChoice;
		List<?> options = setting.options();

		dropdown.open(setting.id(), controlX(), openChoiceButtonTop, openChoiceButtonTop + TOGGLE_HEIGHT + 4,
				CONTROL_WIDTH, options.size(), settingsTop(), settingsBottom());

		Dropdown.Bounds bounds = dropdown.bounds();

		// An edge of its own: without it a list over a row of the same colour has no shape.
		graphics.fill(bounds.x() - 1, bounds.y() - 1, bounds.x() + bounds.width() + 1,
				bounds.y() + bounds.height() + 1, PANEL_EDGE);
		graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), DROPDOWN);

		for (int index = 0; index < options.size(); index++) {
			Object option = options.get(index);
			int top = dropdown.optionTop(index);
			boolean chosen = option == setting.get();
			boolean hovered = dropdown.optionAt(mouseX, mouseY).orElse(-1) == index;

			if (chosen || hovered) {
				graphics.fill(bounds.x(), top, bounds.x() + bounds.width(), top + Dropdown.ROW_HEIGHT,
						chosen ? BUTTON_ON : ROW_HOVER);
			}

			graphics.text(font, Component.literal(optionLabel(setting, option)), bounds.x() + 4, top + 2,
					chosen ? TEXT : TEXT_DIM);
		}
	}

	/**
	 * Whether the values can be shown as buttons beside each other. A translated word is
	 * longer than the id it replaced — "Tüm envanter" where "full" used to be — and two
	 * labels that overlap are worse than one button you press again.
	 */
	private boolean fitsSideBySide(EnumSetting<?> setting) {
		List<?> options = setting.options();

		if (options.size() > 3) {
			return false;
		}

		int each = CONTROL_WIDTH / options.size();
		return options.stream().allMatch(option -> font.width(optionLabel(setting, option)) <= each - 6);
	}

	/** The value's own translation — never the spelling it is stored under. */
	private static String optionLabel(EnumSetting<?> setting, Object option) {
		return option instanceof Enum<?> constant ? setting.label(constant) : String.valueOf(option);
	}

	private void drawScrollbar(GuiGraphicsExtractor graphics) {
		int viewport = settingsBottom() - settingsTop();

		if (contentHeight <= viewport) {
			return;
		}

		int trackX = width - MARGIN - 4;
		int barHeight = Math.max(16, viewport * viewport / contentHeight);
		int travel = viewport - barHeight;
		int barY = settingsTop() + (int) Math.round((double) scroll / (contentHeight - viewport) * travel);

		graphics.fill(trackX, settingsTop(), trackX + 2, settingsBottom(), TRACK);
		graphics.fill(trackX, barY, trackX + 2, barY + barHeight, SCROLLBAR);
	}

	private int wrappedHeight(String text) {
		return font.split(Component.literal(text), rightWidth() - 16).size() * 10;
	}

	// --- text boxes ---

	private void hideAllBoxes() {
		boxes.values().forEach(box -> box.visible = false);
	}

	/** Puts the box for this setting where the row is, creating it the first time. */
	private void placeBox(Setting<?> setting, int x, int y) {
		EditBox box = boxes.computeIfAbsent(setting.id(), id -> makeBox(setting));
		Rect2i frame = new Rect2i(x - 2, y, CONTROL_WIDTH + 4, SETTING_HEIGHT);
		boxFrames.put(setting.id(), frame);

		// A borderless box draws its text at its own top edge, so the box is made one line
		// tall and put where the line belongs: on the hint's line, in the frame's middle.
		// Clicks on the rest of the frame are handed to it in mouseClicked.
		box.setPosition(x, textLineY(frame));
		box.setWidth(CONTROL_WIDTH);
		box.setHeight(font.lineHeight);
		box.visible = true;
	}

	/** Where a line of text sits inside a box's frame; the typed text and the hint share it. */
	private int textLineY(Rect2i frame) {
		return frame.getY() + (frame.getHeight() - font.lineHeight) / 2 + 1;
	}

	/**
	 * A text box in the order it has to be seen: its edge and background, then the box and
	 * what is typed in it, then — while it is empty — what it means empty. The box is drawn
	 * here rather than by the screen's widget pass, which ran before this and left the
	 * background on top of the text; drawn here it also stays inside the panel's scissor and
	 * under an open drop-down list.
	 */
	private void drawBoxFrame(GuiGraphicsExtractor graphics, Setting<?> setting) {
		textBoxFrame(setting.id()).ifPresent(frame -> {
			graphics.fill(frame.getX(), frame.getY(), frame.getX() + frame.getWidth(),
					frame.getY() + frame.getHeight(), TRACK);
			graphics.outline(frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight(), PANEL_EDGE);
			boxes.get(setting.id()).extractRenderState(graphics, frameMouseX, frameMouseY, framePartial);

			String hint = textBoxHintOnScreen(setting.id());
			// Recorded rather than inferred: a test can then ask what was painted, which is
			// a different question from what there was to paint.
			painted.put(setting.id(), hint);

			if (!hint.isBlank()) {
				EditBox box = boxes.get(setting.id());
				graphics.text(font, Component.literal(hint), box.getX() + 1,
						textLineY(frame), TEXT_DIM);
			}
		});
	}

	private EditBox makeBox(Setting<?> setting) {
		EditBox box = new EditBox(font, 0, 0, CONTROL_WIDTH, 16, Component.literal(setting.displayName()));
		box.visible = false;
		// The frame is drawn with the rest of the row, so the widget's own edge would be a
		// second line just inside the first.
		box.setBordered(false);

		if (setting instanceof TextSetting text) {
			box.setMaxLength(text.maxLength());
			box.setValue(text.get());

			box.setResponder(value -> {
				text.set(value);
				onChange.run();
			});
		} else if (setting instanceof IntSetting number) {
			box.setValue(String.valueOf(number.get()));
			box.setResponder(value -> applyTyped(number, value));
		}

		// Input only; drawBoxFrame draws it, after its background.
		addWidget(box);
		return box;
	}

	/** An unreadable number is simply not applied; the box keeps whatever was typed. */
	private void applyTyped(IntSetting setting, String typed) {
		try {
			setting.set(Integer.parseInt(typed.trim()));
			onChange.run();
		} catch (NumberFormatException ignored) {
			// Half-typed or nonsense: leave the setting where it is.
		}
	}

	// --- clicks ---

	private boolean overRow(double mouseX, double mouseY, int rowY) {
		return mouseX >= MARGIN + 2 && mouseX <= MARGIN + LEFT_WIDTH - 2 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
	}

	private boolean overLabel(double mouseX, double mouseY, int labelX, int rowY, Setting<?> setting) {
		return mouseX >= labelX && mouseX <= labelX + font.width(setting.displayName())
			&& mouseY >= rowY && mouseY < rowY + SETTING_HEIGHT;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (listening != null) {
			return mouseWhileListening(event);
		}

		if (profileBar != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT && profileBar.click(font, event.x(), event.y())) {
			return true;
		}

		// Before everything, including the boxes: an open list is drawn over them, so it has
		// to answer for the pixels it covers.
		if (dropdown.isOpen()) {
			return clickedOpenList(event);
		}

		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && hudEditor != null
				&& hudEditorButton().contains((int) event.x(), (int) event.y())) {
			minecraft.gui.setScreen(hudEditor.apply(this));
			return true;
		}

		if (clickedBoxFrame(event, doubleClick)) {
			return true;
		}

		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}

		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}

		// The click landed on none of the boxes, so whichever one had the keyboard gives it
		// up before the click is handled - a keybind widget opened below must hear the keys
		// itself. Clicking a number opens its own box and takes it back.
		setFocused(null);

		return clickedModuleList(event) || clickedResult(event) || clickedSetting(event);
	}

	/**
	 * A click anywhere in a text box's frame is a click on the box. The box is a line tall so
	 * its text sits in the middle; the frame around it is what looks clickable, so the click
	 * is passed on at the same x — the cursor still lands where it was aimed — on the box's line.
	 */
	private boolean clickedBoxFrame(MouseButtonEvent event, boolean doubleClick) {
		for (Map.Entry<String, EditBox> entry : boxes.entrySet()) {
			EditBox box = entry.getValue();
			Rect2i frame = boxFrames.get(entry.getKey());

			if (!box.visible || frame == null || !frame.contains((int) event.x(), (int) event.y())) {
				continue;
			}

			double y = Math.clamp(event.y(), box.getY(), box.getY() + box.getHeight() - 1);
			double x = Math.clamp(event.x(), box.getX(), box.getX() + box.getWidth() - 1);
			setFocused(box);
			box.mouseClicked(new MouseButtonEvent(x, y, event.buttonInfo()), doubleClick);
			return true;
		}

		return false;
	}

	/**
	 * Left and right click are not offered as bindings. Left is how the widget was started
	 * in the first place, so accepting it would bind on the very click that began listening;
	 * right is the game's own use button and binding it would be a trap. Both back out
	 * instead, which leaves clicking away as the obvious escape. The middle and side buttons
	 * are fair game and bind like any key.
	 */
	private boolean mouseWhileListening(MouseButtonEvent event) {
		int button = event.button();

		if (button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT) {
			listening = null;
			return true;
		}

		assign(InputConstants.Type.MOUSE.getOrCreate(button).getName());
		return true;
	}

	private void assign(String keyName) {
		if (listening == null) {
			return;
		}

		listening.set(keyName);
		listening = null;
		onChange.run();
	}

	/**
	 * Opening a result is the end of the search, so the box is emptied and the panel opens
	 * back up whole — the setting is worth more seen among its neighbours than alone.
	 */
	private boolean clickedResult(MouseButtonEvent event) {
		int y = resultsTop();

		for (SearchHit hit : model.results()) {
			if (overResult(event.x(), event.y(), y)) {
				model.select(hit.module());
				search.setValue("");
				editingInt = null;
				scrollTo(hit.id());
				highlighted = hit.id();
				highlightUntil = System.currentTimeMillis() + HIGHLIGHT_MS;
				return true;
			}

			y += RESULT_HEIGHT;
		}

		return false;
	}

	/**
	 * Puts a setting in the middle of the panel. The height of everything is worked out here
	 * rather than read from the last frame, which was drawn while the search still narrowed
	 * the list and would clamp the scroll to a panel that no longer exists.
	 */
	private void scrollTo(String settingId) {
		int offset = 0;
		// The preset strip is above the first row.
		int total = presetStripHeight(0);
		boolean found = false;
		int targetHeight = 0;

		for (MenuRow row : model.rows()) {
			int rowHeight = rowHeight(row);

			if (!found && row.id().equals(settingId)) {
				found = true;
				offset = total;
				targetHeight = rowHeight;
			}

			total += rowHeight;
		}

		if (!found) {
			return;
		}

		contentHeight = total;
		int viewport = settingsBottom() - settingsTop();
		scroll = offset - Math.max(0, (viewport - targetHeight) / 2);
		clampScroll();
	}

	private boolean clickedModuleList(MouseButtonEvent event) {
		int y = listTop();

		for (Module module : model.visibleModules()) {
			if (overRow(event.x(), event.y(), y)) {
				// The toggle takes the click; anywhere else on the row selects the module.
				if (event.x() >= toggleX() && event.x() <= toggleX() + TOGGLE_WIDTH) {
					module.toggle();
					onChange.run();
				} else {
					model.select(module);
					scroll = 0;
					editingInt = null;

					if (presetStrip != null) {
						presetStrip.cancel();
					}
				}

				return true;
			}

			y += ROW_HEIGHT;
		}

		return false;
	}

	private boolean clickedSetting(MouseButtonEvent event) {
		if (event.x() < rightX() || event.y() < settingsTop() || event.y() > settingsBottom()) {
			return false;
		}

		if (presetStrip != null && event.y() >= presetTop && event.y() < presetTop + presetHeight) {
			return presetStrip.click(font, event.x(), event.y());
		}

		for (PlacedRow row : placed) {
			if (event.y() < row.y() || event.y() >= row.y() + row.height()) {
				continue;
			}

			if (row.row().node() instanceof SettingGroup group) {
				model.toggleCollapsed(group);
				return true;
			}

			Setting<?> setting = (Setting<?>) row.row().node();

			// A list's entries live below its own line and take their clicks first.
			if (setting instanceof RegistryListSetting list && event.y() >= row.y() + SETTING_HEIGHT) {
				return clickedListEntry(list, row.y(), event);
			}

			if (event.x() >= resetX() && event.x() <= resetX() + RESET_WIDTH && resettable(setting)) {
				if (!model.presetUndo().undo(setting)) {
					setting.reset();
				}

				refreshBox(setting);
				onChange.run();
				return true;
			}

			return clickedControl(setting, row.y(), event);
		}

		return false;
	}

	/**
	 * The cross drops an entry, the last line opens the picker, and anywhere else on an entry
	 * starts a drag. Dragging is picked up here rather than on the handle alone: a twelve
	 * pixel line is a small thing to hit, and there is nothing else on the row to confuse it
	 * with.
	 */
	private boolean clickedListEntry(RegistryListSetting list, int rowY, MouseButtonEvent event) {
		List<String> entries = list.get();
		int index = (int) ((event.y() - rowY - SETTING_HEIGHT) / ENTRY_HEIGHT);

		// Below the add line is the note, if any: nothing to click there.
		if (index < 0 || index > entries.size()) {
			return false;
		}

		if (index == entries.size()) {
			minecraft.gui.setScreen(new ItemPickerScreen(this, list, onChange));
			return true;
		}

		if (event.x() >= removeX() - 2) {
			list.remove(entries.get(index));
			onChange.run();
			return true;
		}

		draggingList = list;
		draggingIndex = index;
		return true;
	}

	private boolean clickedControl(Setting<?> setting, int rowY, MouseButtonEvent event) {
		int x = controlX();

		if (event.x() < x || event.x() > x + CONTROL_WIDTH) {
			return false;
		}

		if (setting instanceof BoolSetting bool) {
			bool.set(!bool.get());
			onChange.run();
			return true;
		}

		if (setting instanceof KeybindSetting keybind) {
			listening = keybind;
			return true;
		}

		if (setting instanceof IntSetting number) {
			return clickedNumber(number, x, event);
		}

		if (setting instanceof EnumSetting<?> choice) {
			// One rule for both sides. Drawing asks the same question, so a button drawn whole
			// is clicked whole: it opens its list instead of picking by where it was hit.
			if (!fitsSideBySide(choice)) {
				openTheList(choice, rowY);
				return true;
			}

			pick(choice, x, event);
			onChange.run();
			return true;
		}

		return false;
	}

	/** Opens the list and picks nothing. Choosing happens on the list, in {@link #clickedOpenList}. */
	private void openTheList(EnumSetting<?> setting, int rowY) {
		int buttonTop = rowY + (SETTING_HEIGHT - TOGGLE_HEIGHT) / 2 - 2;

		openChoice = setting;
		openChoiceButtonTop = buttonTop;
		openChoiceOnScreen = true;
		dropdown.open(setting.id(), controlX(), buttonTop, buttonTop + TOGGLE_HEIGHT + 4, CONTROL_WIDTH,
				setting.options().size(), settingsTop(), settingsBottom());
	}

	/**
	 * A click while a list is open belongs to that list, whether it landed on it or not.
	 *
	 * <p>Landing on an option picks it; landing anywhere else closes the list and is swallowed
	 * there. Swallowed on purpose: the list is drawn over other rows, and a click that both
	 * closed it and pressed whatever it was covering would be the same bug in a new shape.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private boolean clickedOpenList(MouseButtonEvent event) {
		OptionalInt option = event.button() == InputConstants.MOUSE_BUTTON_LEFT
			? dropdown.optionAt(event.x(), event.y())
			: OptionalInt.empty();

		if (option.isPresent() && openChoice != null) {
			List<?> options = openChoice.options();
			((EnumSetting) openChoice).set((Enum) options.get(option.getAsInt()));
			closeTheList();
			onChange.run();
			return true;
		}

		closeTheList();
		return true;
	}

	private void closeTheList() {
		dropdown.close();
		openChoice = null;
		openChoiceOnScreen = false;
	}

	/** The track drags the value; the number beside it opens a box to type one in. */
	private boolean clickedNumber(IntSetting setting, int x, MouseButtonEvent event) {
		int trackWidth = CONTROL_WIDTH - 28;

		if (event.x() > x + trackWidth) {
			editingInt = setting.id();
			EditBox box = boxes.computeIfAbsent(setting.id(), id -> makeBox(setting));
			box.setValue(String.valueOf(setting.get()));
			box.visible = true;
			setFocused(box);
			box.setFocused(true);
			return true;
		}

		dragTo(setting, x, trackWidth, event.x());
		return true;
	}

	private void dragTo(IntSetting setting, int x, int trackWidth, double mouseX) {
		double along = Math.clamp((mouseX - x) / trackWidth, 0.0, 1.0);
		setting.set((int) Math.round(setting.min() + along * (setting.max() - setting.min())));
		onChange.run();
	}

	/**
	 * Picks the value under the mouse out of buttons drawn side by side.
	 *
	 * <p>Only ever called for a setting drawn that way — {@link #fitsSideBySide} decides, for
	 * both the drawing and the click. It used to have a rule of its own here ("more than three
	 * values, cycle instead"), which agreed with the drawing for long enough to look right and
	 * disagreed for every short list of long words: two values drawn as one button, and a
	 * click that still divided that button in half.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private void pick(EnumSetting<?> setting, int x, MouseButtonEvent event) {
		List<?> options = setting.options();
		int each = CONTROL_WIDTH / options.size();
		int index = Math.clamp((int) ((event.x() - x) / each), 0, options.size() - 1);
		((EnumSetting) setting).set((Enum) options.get(index));
	}

	/** After a reset the box on screen still shows the old text, so it is told the new one. */
	private void refreshBox(Setting<?> setting) {
		EditBox box = boxes.get(setting.id());

		if (box != null) {
			box.setValue(String.valueOf(setting.get()));
		}
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingList = null;
		draggingIndex = -1;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (draggingList != null) {
			return dragEntry(event);
		}

		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (PlacedRow row : placed) {
				if (event.y() >= row.y() && event.y() < row.y() + row.height()
						&& row.row().node() instanceof IntSetting number
						&& !number.id().equals(editingInt)
						&& event.x() >= controlX() && event.x() <= controlX() + CONTROL_WIDTH - 28) {
					dragTo(number, controlX(), CONTROL_WIDTH - 28, event.x());
					return true;
				}
			}
		}

		return super.mouseDragged(event, dragX, dragY);
	}

	/** Follows the cursor a row at a time, so the list reorders under it as it moves. */
	private boolean dragEntry(MouseButtonEvent event) {
		for (PlacedRow row : placed) {
			if (row.row().node() != draggingList) {
				continue;
			}

			int target = (int) Math.floor((event.y() - row.y() - SETTING_HEIGHT) / (double) ENTRY_HEIGHT);
			target = Math.clamp(target, 0, draggingList.get().size() - 1);

			if (target != draggingIndex) {
				draggingList.move(draggingIndex, target);
				draggingIndex = target;
				onChange.run();
			}

			return true;
		}

		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= rightX()) {
			// The list is placed from the row it belongs to. Scrolling moves that row, and a
			// row scrolled out of sight is not drawn at all — so the list goes rather than
			// being left behind answering clicks where nothing is drawn.
			closeTheList();
			scroll -= (int) Math.round(scrollY * SCROLL_STEP);
			clampScroll();
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private void clampScroll() {
		int viewport = settingsBottom() - settingsTop();
		scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - viewport));
	}

	/**
	 * Right Shift opens the menu and closes it again, but only on the way back up and only
	 * when nothing was pressed while it was held - see {@link MenuKeyRule}.
	 */
	@Override
	public boolean keyPressed(KeyEvent event) {
		// A key and the character it stands for arrive as two separate events, and consuming
		// the first does nothing to the second. Each press starts out letting its character
		// through; whoever takes the key says otherwise.
		keyTaken = false;

		if (listening != null) {
			return keyWhileListening(event);
		}

		// Not marked taken: while a name is typed, the character that follows is the bar's too.
		if (profileBar != null && profileBar.key(event)) {
			return true;
		}

		if (presetStrip != null && presetStrip.key(event)) {
			return true;
		}

		// Escape closes an open list first, the way it backs out of anything else opened here.
		if (event.key() == InputConstants.KEY_ESCAPE && dropdown.isOpen()) {
			closeTheList();
			keyTaken = true;
			return true;
		}

		menuKey.pressed(event.key());

		// A search narrows what you can see, so Escape gives that back before it gives back
		// the screen. An empty box means there is nothing to undo and Escape closes as usual.
		// Putting the cursor down with the text is the way out of the box.
		if (event.key() == InputConstants.KEY_ESCAPE && !search.getValue().isEmpty()) {
			search.setValue("");
			setFocused(null);
			return true;
		}

		if (event.key() == InputConstants.KEY_RETURN && getFocused() instanceof EditBox) {
			setFocused(null);
			editingInt = null;
			return true;
		}

		return super.keyPressed(event);
	}

	/**
	 * Where typing ends up. A widget that already has the keyboard keeps it; a key that a
	 * keybind widget just took drops its character on the floor, which is what stops binding
	 * J from also leaving a "j" in the search; anything else starts a search, so the box can
	 * be typed into without being clicked while never holding the keyboard for its own sake.
	 */
	@Override
	public boolean charTyped(CharacterEvent event) {
		if (profileBar != null && profileBar.charTyped(event) || presetStrip != null && presetStrip.charTyped(event)) {
			keyTaken = false;
			return true;
		}

		if (listening != null || keyTaken) {
			keyTaken = false;
			return true;
		}

		if (super.charTyped(event)) {
			return true;
		}

		if (!event.isAllowedChatCharacter()) {
			return false;
		}

		setFocused(search);
		search.setFocused(true);
		return search.charTyped(event);
	}

	/** The menu key acts on release, so holding it to type a capital letter is safe. */
	@Override
	public boolean keyReleased(KeyEvent event) {
		if (listening == null && menuKey.released(event.key())) {
			onClose();
			return true;
		}

		return super.keyReleased(event);
	}

	/**
	 * While a keybind widget is waiting, every key belongs to it — including Right Shift,
	 * which would otherwise close the menu, and which somebody may well want as their key.
	 */
	private boolean keyWhileListening(KeyEvent event) {
		// The key belongs to the widget, and so does the character that follows it.
		keyTaken = true;

		if (event.key() == InputConstants.KEY_ESCAPE) {
			listening = null;
			return true;
		}

		if (event.key() == InputConstants.KEY_DELETE) {
			assign(KeybindSetting.UNBOUND);
			return true;
		}

		assign(InputConstants.Type.KEYBOARD.getOrCreate(event.key()).getName());
		return true;
	}

	// --- what the game test needs to aim at ---

	/** Where this module sits on screen. */
	public OptionalInt rowCenterY(Module module) {
		int y = listTop();

		for (Module listed : model.visibleModules()) {
			if (listed == module) {
				return OptionalInt.of(y + ROW_HEIGHT / 2);
			}

			y += ROW_HEIGHT;
		}

		return OptionalInt.empty();
	}

	/** The first pixel row below a row, so a test can tell whether the next one sits flush. */
	public OptionalInt rowBottomY(String id) {
		return placed.stream()
			.filter(row -> row.row().id().equals(id))
			.mapToInt(row -> row.y() + row.height())
			.findFirst();
	}

	public OptionalInt rowTopY(String id) {
		return placed.stream()
			.filter(row -> row.row().id().equals(id))
			.mapToInt(PlacedRow::y)
			.findFirst();
	}

	/** Where a setting's row ended up, or empty when it is not on screen at all. */
	public OptionalInt settingCenterY(String settingId) {
		return placed.stream()
			.filter(row -> row.row().id().equals(settingId))
			.mapToInt(row -> row.y() + row.height() / 2)
			.findFirst();
	}

	public int toggleCenterX() {
		return toggleX() + TOGGLE_WIDTH / 2;
	}

	/** A point on a row that is not the toggle, so a click there selects instead. */
	public int nameCenterX() {
		return MARGIN + 8;
	}

	/** The middle of the toggle a bool setting draws in the control column. */
	public int controlToggleCenterX() {
		return controlX() + CONTROL_WIDTH - TOGGLE_WIDTH / 2;
	}

	/** The left end of an int setting's slider track, which drags it to the minimum. */
	public int sliderTrackLeftX() {
		return controlX() + 1;
	}

	public int resetCenterX() {
		return resetX() + RESET_WIDTH / 2;
	}

	/** The fold arrow in front of a heading's name, where a player aims to fold it. */
	public int headingArrowCenterX() {
		return rightX() + 8 + font.width("▼") / 2;
	}

	/** The number printed beside an int setting's track, which opens a box to type a value in. */
	public int numberBoxCenterX() {
		return controlX() + CONTROL_WIDTH - 14;
	}

	/** Whether that setting's typing box is the one currently open. */
	public boolean numberBoxOpen(String settingId) {
		return settingId.equals(editingInt);
	}

	/** The column the cross that drops a list entry is drawn in. */
	public int listEntryRemoveX() {
		return removeX();
	}

	/** The setting a result opened, while it is still lit. */
	public Optional<String> highlightedSetting() {
		return highlighted == null || System.currentTimeMillis() >= highlightUntil
			? Optional.empty()
			: Optional.of(highlighted);
	}

	/** Fades as it runs out, so it draws the eye without staying to be tidied away. */
	private int highlightColour() {
		long left = highlightUntil - System.currentTimeMillis();
		int alpha = (int) Math.clamp(left * 150 / HIGHLIGHT_MS, 0, 150);
		return alpha << 24 | HIGHLIGHT;
	}

	/** How far the settings panel has been scrolled. */
	public int scrollOffset() {
		return scroll;
	}

	public int resultX() {
		return MARGIN + 8;
	}

	/** Where a search result sits in the left panel. */
	public OptionalInt resultCenterY(String settingId) {
		int y = resultsTop();

		for (SearchHit hit : model.results()) {
			if (hit.id().equals(settingId)) {
				return OptionalInt.of(y + RESULT_HEIGHT / 2);
			}

			y += RESULT_HEIGHT;
		}

		return OptionalInt.empty();
	}

	/** The module search box. */
	public EditBox searchBox() {
		return search;
	}

	public MenuModel model() {
		return model;
	}

	/**
	 * The right edge the settings column is laid out against: the panel edge on a narrow
	 * screen, and {@link #CONTENT_WIDTH} from the labels on a wide one.
	 */
	public int contentRight() {
		return Math.min(width - MARGIN - 8, contentLeft() + CONTENT_WIDTH);
	}

	/** Where the settings column starts: the labels' own left edge. */
	public int contentLeft() {
		return rightX() + 8;
	}

	/**
	 * The row the cursor is over, which is the row the panel lights up. Anywhere along the
	 * row counts, including the gap between a label and its control — that gap is the whole
	 * reason the highlight exists.
	 */
	public Optional<String> hoveredRowId(double mouseX, double mouseY) {
		if (mouseX < contentLeft() - 4 || mouseX > contentRight() + 4
				|| mouseY < settingsTop() || mouseY > settingsBottom()) {
			return Optional.empty();
		}

		return placed.stream()
			.filter(row -> mouseY >= row.y() && mouseY < row.y() + row.height())
			.map(row -> row.row().id())
			.findFirst();
	}

	/**
	 * The band the settings are visible in. A row placed outside it is drawn nowhere and
	 * refuses clicks, so anything driving the menu has to scroll a row in before pressing it.
	 */
	public int settingsViewTop() {
		return settingsTop();
	}

	public int settingsViewBottom() {
		return settingsBottom();
	}

	/** True for an id nothing in this build answers to. */
	public static boolean isMissing(String id) {
		return nameOf(id) == null;
	}

	/** What an entry's row reads: the game's name, or the bare id when nothing knows it. */
	public static String entryLabel(String id) {
		String name = nameOf(id);
		return name == null ? id : name;
	}

	/** Grey for an id this build cannot resolve, ordinary text otherwise. */
	public static int entryColor(String id) {
		return isMissing(id) ? TEXT_DIM : TEXT;
	}

	/** Drawn beside an id this build cannot resolve. */
	public static String missingMark() {
		return Lang.of(Keys.MENU_MISSING);
	}

	/** The colour the missing mark is drawn in. */
	public static int missingMarkColor() {
		return WARNING;
	}

	/** The column a list's entries and its add line start at. */
	public int listEntryX(String settingId) {
		return listLeft(settingId) + 4;
	}

	/**
	 * Where a list's drag handles sit: under its name, indented with it, so a list under a
	 * heading does not hang out to the left of its own label.
	 */
	private int listLeft(String settingId) {
		int depth = model.rows().stream()
			.filter(row -> row.id().equals(settingId))
			.mapToInt(MenuRow::depth)
			.findFirst()
			.orElse(0);

		return rightX() + 16 + depth * INDENT;
	}

	/** Where the nth entry of a list setting sits. */
	public OptionalInt listEntryY(String settingId, int index) {
		return placedFor(settingId).mapToInt(row -> entryY(row.y(), index) + ENTRY_HEIGHT / 2).findFirst();
	}

	/** The box a setting's note is drawn in, or empty with no note or the row not on screen. */
	public Optional<Rect2i> noteBounds(String settingId) {
		return placedFor(settingId)
			.filter(row -> noteOf(row.row()).isPresent())
			.map(row -> new Rect2i(rightX() + 8 + row.row().depth() * INDENT, row.y() + baseHeight(row.row()),
				font.width(noteOf(row.row()).orElseThrow()), font.lineHeight))
			.findFirst();
	}

	/** Where the "+ Ekle" line of a list setting sits. */
	public OptionalInt addLineY(String settingId) {
		return placedFor(settingId)
			.filter(row -> row.row().node() instanceof RegistryListSetting)
			.mapToInt(row -> entryY(row.y(), ((RegistryListSetting) row.row().node()).get().size()) + ENTRY_HEIGHT / 2)
			.findFirst();
	}

	private java.util.stream.Stream<PlacedRow> placedFor(String settingId) {
		return placed.stream().filter(row -> row.row().id().equals(settingId));
	}

	/**
	 * Delivers a click at these coordinates without going through the mouse. Used by the
	 * game test that times how long opening the picker takes, where routing through the
	 * input handler would measure the handler as well.
	 */
	public void clickFor(int x, int y) {
		mouseClicked(new MouseButtonEvent(x, y,
				new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
	}

	// --- the furniture, as the tests that assert on it see it ---

	/**
	 * The outline drawn around the search box, and the geometry the box itself is laid out
	 * inside. Stated here rather than read back off the widget, because the widget is
	 * positioned from it.
	 */
	public Rect2i searchFrame() {
		return new Rect2i(MARGIN + 5, top() + 16, LEFT_WIDTH - 10, SEARCH_HEIGHT);
	}

	/** The right edge of the magnifier, which the text has to start clear of. */
	public int searchIconRight() {
		return searchFrame().getX() + SEARCH_ICON_WIDTH - 2;
	}

	/** Whether an enum setting's list is open, and which one. */
	public boolean dropdownOpen() {
		return dropdown.isOpen();
	}

	public String openDropdownId() {
		return dropdown.openId();
	}

	/** Where the open list is on screen, so a test can look at those pixels. */
	public Rect2i openDropdownBounds() {
		Dropdown.Bounds bounds = dropdown.bounds();
		return new Rect2i(bounds.x(), bounds.y(), bounds.width(), bounds.height());
	}

	/** The middle of one option row of the open list, in GUI units. */
	public Rect2i openDropdownOption(int index) {
		Dropdown.Bounds bounds = dropdown.bounds();
		return new Rect2i(bounds.x(), dropdown.optionTop(index), bounds.width(), Dropdown.ROW_HEIGHT);
	}

	/** The colour the open list is filled with, for a test that samples the screen. */
	public int dropdownColour() {
		return DROPDOWN;
	}

	/**
	 * The button of one enum setting, as drawn, or empty when that row is not on screen.
	 *
	 * <p>Whole width: a button drawn as one button is clicked as one button, which is the
	 * thing that was wrong.
	 */
	public Optional<Rect2i> choiceButton(String settingId) {
		for (PlacedRow row : placed) {
			if (row.row().node() instanceof EnumSetting<?> choice && choice.id().equals(settingId)) {
				int top = row.y() + (SETTING_HEIGHT - TOGGLE_HEIGHT) / 2 - 2;
				return Optional.of(new Rect2i(controlX(), top, CONTROL_WIDTH, TOGGLE_HEIGHT + 4));
			}
		}

		return Optional.empty();
	}

	/** Which setting's row is at this height, as the rows were placed last frame. */
	public Optional<String> settingAt(int guiY) {
		for (PlacedRow row : placed) {
			if (guiY >= row.y() && guiY < row.y() + row.height() && row.row().node() instanceof Setting<?> setting) {
				return Optional.of(setting.id());
			}
		}

		return Optional.empty();
	}

	/** Whether that setting is drawn as one button with a list, rather than buttons side by side. */
	public boolean choiceOpensAList(String settingId) {
		for (PlacedRow row : placed) {
			if (row.row().node() instanceof EnumSetting<?> choice && choice.id().equals(settingId)) {
				return !fitsSideBySide(choice);
			}
		}

		return false;
	}

	/** The greyed-out line an empty search box shows. */
	public String searchHint() {
		return Lang.of(Keys.MENU_SEARCH_HINT);
	}

	/**
	 * The hint as it is actually on screen: the line while the box is empty, and nothing at
	 * all once something has been typed. The drawing reads this, so what a test sees here is
	 * what the panel paints.
	 *
	 * <p>Not hidden on focus, the way vanilla hides its own: this box takes typing without
	 * ever being clicked, so "focused" is not a state the player can see, and a box that
	 * emptied itself would otherwise go blank for no visible reason.
	 */
	public String searchHintOnScreen() {
		return search != null && search.getValue().isEmpty() ? searchHint() : "";
	}

	/** The square the reset arrow is painted in, for the row with this setting on it. */
	public Optional<Rect2i> resetGlyph(String settingId) {
		return placedFor(settingId).map(row -> resetGlyphBox(row.y())).findFirst();
	}

	/**
	 * The item drawn beside a list entry, or empty when the id names nothing this build has.
	 * The drawing goes through here too, so what a test reads is what the screen paints.
	 */
	public Optional<ItemStack> entryIcon(String settingId, int index) {
		return settingById(settingId)
			.filter(RegistryListSetting.class::isInstance)
			.map(RegistryListSetting.class::cast)
			.filter(list -> index >= 0 && index < list.get().size())
			.flatMap(list -> iconFor(list.get().get(index)));
	}

	/** An id the registry knows becomes a stack to draw; one it does not becomes nothing. */
	private static Optional<ItemStack> iconFor(String id) {
		try {
			Identifier key = Identifier.parse(id);

			return BuiltInRegistries.ITEM.containsKey(key)
				? Optional.of(new ItemStack(BuiltInRegistries.ITEM.getValue(key)))
				: Optional.empty();
		} catch (RuntimeException e) {
			return Optional.empty();
		}
	}

	/** The left edge of a list entry's icon column, just past the drag handle. */
	public int entryIconX(String settingId) {
		return listEntryX(settingId) + 10;
	}

	/** The right edge of a list entry's icon column. */
	public int entryIconRight(String settingId) {
		return entryIconX(settingId) + ICON;
	}

	/** Where a list entry's name starts: clear of the icon, never on top of it. */
	public int entryLabelX(String settingId) {
		return entryIconRight(settingId) + 4;
	}

	/** The box the reset arrow is drawn in, and the one a click has to land in to undo. */
	public Optional<Rect2i> resetBox(String settingId) {
		return placedFor(settingId)
			.map(row -> new Rect2i(resetX(), row.y(), RESET_WIDTH, SETTING_HEIGHT))
			.findFirst();
	}

	/**
	 * The greyed-out line an empty text box shows, from the setting's own hint key.
	 *
	 * <p>Looked up through the model rather than through the rows already placed this frame:
	 * a row is placed after it is drawn, so during drawing its own hint would not be found
	 * yet — which is exactly how this drew nothing while a test asking after the frame was
	 * told the right answer.
	 */
	public String textBoxHint(String settingId) {
		return settingById(settingId)
			.filter(TextSetting.class::isInstance)
			.map(SurvivalKitScreen::hintFor)
			.orElse("");
	}

	/** The setting a row id names, whether or not that row has been drawn yet. */
	private Optional<Setting<?>> settingById(String settingId) {
		for (MenuRow row : model.rows()) {
			if (row.id().equals(settingId) && row.node() instanceof Setting<?> setting) {
				return Optional.of(setting);
			}
		}

		return Optional.empty();
	}

	/**
	 * The hint as it is actually on screen: shown while the box is empty, gone the moment
	 * there is something in it. The drawing reads this, so a test that asks gets the answer
	 * the panel paints rather than the text it merely has ready.
	 */
	public String textBoxHintOnScreen(String settingId) {
		EditBox box = boxes.get(settingId);

		return box != null && box.visible && box.getValue().isEmpty() ? textBoxHint(settingId) : "";
	}

	/**
	 * What was actually painted into that box on the last frame drawn, as opposed to what
	 * would be painted if it were asked now. The two came apart once already.
	 */
	public String paintedHint(String settingId) {
		return painted.getOrDefault(settingId, "");
	}

	/** A hint with no translation behind it is no hint: better an empty box than a raw key. */
	private static String hintFor(Setting<?> setting) {
		String text = Lang.of(Keys.hint(setting.key()));
		return Lang.isRaw(text) ? "" : text;
	}

	/** The outline drawn around a text setting's box, while that box is on screen. */
	public Optional<Rect2i> textBoxFrame(String settingId) {
		EditBox box = boxes.get(settingId);

		return box == null || !box.visible
			? Optional.empty()
			: Optional.ofNullable(boxFrames.get(settingId));
	}
}
