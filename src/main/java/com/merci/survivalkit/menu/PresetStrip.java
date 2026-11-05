package com.merci.survivalkit.menu;

import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.preset.Preset;
import com.merci.survivalkit.preset.PresetUndo;
import com.merci.survivalkit.preset.PresetValues;
import com.merci.survivalkit.preset.Presets;
import com.merci.survivalkit.profile.ProfileNames;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/**
 * The top of a module's settings: "Presets", then one button per preset — the built-in ones,
 * then the player's own with a ✕ to delete — and "+ Save", which takes a name in the strip
 * itself and saves the module's settings as they are under it. A click applies a preset to this
 * module only; what it changed can be put back one setting at a time with the row's ↺. Hovering
 * a preset says what applying it would change.
 */
public final class PresetStrip {
	/** What the strip is doing. */
	public enum Mode {
		IDLE, NAMING
	}

	/** One preset's button as laid out; the cross is there only on the player's own. */
	public record Chip(Preset preset, Rect2i box, Rect2i cross) {
	}

	private static final int HEIGHT = 14;
	private static final int LINE = 18;
	private static final int PADDING = 6;
	private static final int GAP = 4;
	private static final int CROSS = 10;
	private static final int NAME_WIDTH = 110;
	private static final long STATUS_MS = 3000;
	private static final int BUTTON = 0xFF3A3A44;
	private static final int BUTTON_ON = 0xFF4C7AB0;
	private static final int EDGE = 0xFF2C2C34;
	private static final int TEXT = 0xFFE8E8EC;
	private static final int TEXT_DIM = 0xFF9A9AA4;
	private static final int ERROR = 0xFFE8C34C;
	private static final int BOX = 0xFF26262E;

	private final Supplier<Presets> presets;
	private final PresetUndo undo;
	private final Runnable onChange;
	private Mode mode = Mode.IDLE;
	private EditBox name;
	private String error = "";
	private String status = "";
	private long statusUntil;

	private Module module;
	private final List<Chip> chips = new ArrayList<>();
	private Rect2i save;
	private Rect2i nameBox;
	private Rect2i ok;
	private Rect2i cancel;
	private int left;
	private int top;
	private int height;

	/**
	 * @param undo     where what a preset changed is kept for the rows' ↺
	 * @param onChange called once a preset has changed settings under the menu
	 */
	public PresetStrip(Supplier<Presets> presets, PresetUndo undo, Runnable onChange) {
		this.presets = presets;
		this.undo = undo;
		this.onChange = onChange;
	}

	/** Places everything for this module at this point, as wide as given; how tall it came out. */
	public int layout(Font font, Module module, int x, int y, int width) {
		if (this.module != module) {
			cancel();
		}

		this.module = module;
		left = x;
		top = y;
		chips.clear();
		save = null;
		nameBox = null;
		ok = null;
		cancel = null;

		String heading = Lang.of(Keys.MENU_PRESETS);
		int start = x + font.width(heading) + 2 * GAP;
		int right = x + width;
		int[] at = {start, y};

		if (mode == Mode.NAMING) {
			nameBox = place(at, NAME_WIDTH, start, right);
			ok = place(at, buttonWidth(font, Lang.of(Keys.MENU_PROFILE_OK)), start, right);
			cancel = place(at, buttonWidth(font, Lang.of(Keys.MENU_PROFILE_CANCEL)), start, right);
			name.setPosition(nameBox.getX() + 4, nameBox.getY() + 3);
		} else {
			for (Preset preset : presets.get().of(module)) {
				int width0 = buttonWidth(font, preset.name()) + (preset.builtIn() ? 0 : CROSS);
				Rect2i box = place(at, width0, start, right);
				Rect2i cross = preset.builtIn() ? null : new Rect2i(box.getX() + box.getWidth() - CROSS - 2, box.getY(), CROSS + 2, HEIGHT);
				chips.add(new Chip(preset, box, cross));
			}

			save = place(at, buttonWidth(font, Lang.of(Keys.MENU_PRESET_SAVE)), start, right);
		}

		height = at[1] - y + LINE + (error.isEmpty() ? 0 : LINE) + 4;
		return height;
	}

	/** The next place along the line, or the start of the next line when it does not fit. */
	private static Rect2i place(int[] at, int width, int start, int right) {
		if (at[0] + width > right && at[0] > start) {
			at[0] = start;
			at[1] += LINE;
		}

		Rect2i box = new Rect2i(at[0], at[1], width, HEIGHT);
		at[0] += width + GAP;
		return box;
	}

	private static int buttonWidth(Font font, String label) {
		return font.width(label) + 2 * PADDING;
	}

	/** Draws what {@link #layout} placed. */
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float partial) {
		graphics.text(font, Component.literal(Lang.of(Keys.MENU_PRESETS)), left, top + 3, TEXT_DIM);

		if (mode == Mode.NAMING) {
			graphics.fill(nameBox.getX(), nameBox.getY(), nameBox.getX() + nameBox.getWidth(), nameBox.getY() + nameBox.getHeight(), BOX);
			graphics.outline(nameBox.getX(), nameBox.getY(), nameBox.getWidth(), nameBox.getHeight(), BUTTON_ON);
			name.extractRenderState(graphics, mouseX, mouseY, partial);
			button(graphics, font, ok, Lang.of(Keys.MENU_PROFILE_OK), mouseX, mouseY);
			button(graphics, font, cancel, Lang.of(Keys.MENU_PROFILE_CANCEL), mouseX, mouseY);

			if (!error.isEmpty()) {
				graphics.text(font, Component.literal(error), nameBox.getX(), nameBox.getY() + LINE + 3, ERROR);
			}

			return;
		}

		for (Chip chip : chips) {
			Rect2i box = chip.box();
			boolean overCross = contains(chip.cross(), mouseX, mouseY);
			boolean over = contains(box, mouseX, mouseY) && !overCross;
			graphics.fill(box.getX(), box.getY(), box.getX() + box.getWidth(), box.getY() + box.getHeight(), over ? BUTTON_ON : BUTTON);
			graphics.outline(box.getX(), box.getY(), box.getWidth(), box.getHeight(), EDGE);
			graphics.text(font, Component.literal(chip.preset().name()), box.getX() + PADDING, box.getY() + 3, TEXT);

			if (chip.cross() != null) {
				graphics.text(font, Component.literal("✕"), chip.cross().getX() + 2, box.getY() + 3, overCross ? ERROR : TEXT_DIM);
			}

			if (over) {
				graphics.setComponentTooltipForNextFrame(font, preview(chip.preset()), mouseX, mouseY);
			} else if (overCross) {
				graphics.setTooltipForNextFrame(Component.literal(Lang.of(Keys.MENU_PRESET_DELETE)), mouseX, mouseY);
			}
		}

		button(graphics, font, save, Lang.of(Keys.MENU_PRESET_SAVE), mouseX, mouseY);
	}

	/** What applying the preset would change now, a line each; or that it would change nothing. */
	private List<Component> preview(Preset preset) {
		List<PresetValues.Change> changes = presets.get().preview(module, preset);

		if (changes.isEmpty()) {
			return List.of(Component.literal(Lang.of(Keys.MENU_PRESET_NO_CHANGE)));
		}

		return changes.stream().map(change -> (Component) Component.literal(PresetValues.describe(change))).toList();
	}

	private void button(GuiGraphicsExtractor graphics, Font font, Rect2i box, String label, int mouseX, int mouseY) {
		boolean over = contains(box, mouseX, mouseY);
		graphics.fill(box.getX(), box.getY(), box.getX() + box.getWidth(), box.getY() + box.getHeight(), over ? BUTTON_ON : BUTTON);
		graphics.outline(box.getX(), box.getY(), box.getWidth(), box.getHeight(), EDGE);
		graphics.text(font, Component.literal(label), box.getX() + PADDING, box.getY() + 3, TEXT);
	}

	/** A click: true if it landed on something of the strip's. */
	public boolean click(Font font, double x, double y) {
		if (module == null) {
			return false;
		}

		if (mode == Mode.NAMING) {
			if (contains(ok, x, y)) {
				finishNaming();
				return true;
			}

			if (contains(cancel, x, y)) {
				cancel();
				return true;
			}

			// A click in the name box is the box's; the cursor stays at the end.
			return contains(nameBox, x, y);
		}

		for (Chip chip : chips) {
			if (contains(chip.cross(), x, y)) {
				presets.get().delete(module, chip.preset());
				return true;
			}

			if (contains(chip.box(), x, y)) {
				apply(chip.preset());
				return true;
			}
		}

		if (contains(save, x, y)) {
			startNaming(font);
			return true;
		}

		return false;
	}

	private void apply(Preset preset) {
		List<PresetValues.Change> changes = presets.get().apply(module, preset);
		undo.record(changes);
		status = changes.isEmpty()
			? Lang.of(Keys.MENU_PRESET_NO_CHANGE)
			: Lang.of(Keys.MENU_PRESET_APPLIED, preset.name(), changes.size());
		statusUntil = System.currentTimeMillis() + STATUS_MS;
		onChange.run();
	}

	/** Keys while a name is being typed: Enter takes it, Escape gives up, the rest edit it. */
	public boolean key(KeyEvent event) {
		if (mode != Mode.NAMING) {
			return false;
		}

		switch (event.key()) {
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> finishNaming();
			case InputConstants.KEY_ESCAPE -> cancel();
			default -> name.keyPressed(event);
		}

		return true;
	}

	public boolean charTyped(CharacterEvent event) {
		return mode == Mode.NAMING && name.charTyped(event);
	}

	/** Whatever the strip was doing, it stops: another module, or the menu closing. */
	public void cancel() {
		mode = Mode.IDLE;
		error = "";
		name = null;
	}

	private void startNaming(Font font) {
		mode = Mode.NAMING;
		error = "";
		name = new EditBox(font, 0, 0, NAME_WIDTH - 8, HEIGHT - 4, Component.literal(Lang.of(Keys.MENU_PRESET_SAVE)));
		name.setBordered(false);
		name.setMaxLength(ProfileNames.MAX_LENGTH);
		name.setFocused(true);
	}

	private void finishNaming() {
		Optional<ProfileNames.Problem> problem = presets.get().save(module, name.getValue());

		if (problem.isPresent()) {
			error = Lang.of(switch (problem.get()) {
				case BLANK -> Keys.MENU_PROFILE_BLANK;
				case TOO_LONG -> Keys.MENU_PROFILE_TOO_LONG;
				case BAD_CHARACTER -> Keys.MENU_PROFILE_BAD_CHARACTER;
				case TAKEN -> Keys.MENU_PRESET_TAKEN;
			});
			return;
		}

		cancel();
	}

	/** What the last preset applied did, for a few seconds after; empty otherwise. */
	public String status() {
		return System.currentTimeMillis() < statusUntil ? status : "";
	}

	private static boolean contains(Rect2i box, double x, double y) {
		return box != null && x >= box.getX() && x < box.getX() + box.getWidth() && y >= box.getY() && y < box.getY() + box.getHeight();
	}

	// --- where things are, for the tests ---

	public Mode mode() {
		return mode;
	}

	public String error() {
		return error;
	}

	/** Where a preset's button is now, by the name it shows. */
	public Optional<Rect2i> chip(String presetName) {
		return chips.stream().filter(chip -> chip.preset().name().equals(presetName)).map(Chip::box).findFirst();
	}

	/** Where the ✕ of one of the player's own is now. */
	public Optional<Rect2i> cross(String presetName) {
		return chips.stream().filter(chip -> chip.preset().name().equals(presetName) && chip.cross() != null).map(Chip::cross).findFirst();
	}

	/** The names in the strip, in order. */
	public List<String> names() {
		return chips.stream().map(chip -> chip.preset().name()).toList();
	}

	public Optional<Rect2i> saveButton() {
		return Optional.ofNullable(save);
	}

	public Optional<Rect2i> okButton() {
		return Optional.ofNullable(ok);
	}

	/** The strip's whole area as last laid out. */
	public Rect2i bounds(int width) {
		return new Rect2i(left, top, width, height);
	}
}
