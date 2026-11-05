package com.merci.survivalkit.menu;

import com.merci.survivalkit.profile.ProfileNames;
import com.merci.survivalkit.profile.Profiles;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 * The left of the menu's bar: the profile in use — a click lists them all to switch to — and New,
 * Copy, Rename, Delete. A name is typed into the bar itself, OK or Enter takes it; a delete is
 * asked about first. "default" cannot be renamed or deleted, and says so by being dimmed.
 */
public final class ProfileBar {
	/** What can be clicked in the bar; which of them are there depends on what the bar is doing. */
	public enum Part {
		PROFILE, NEW, COPY, RENAME, DELETE, OK, CANCEL, YES
	}

	/** What the bar is doing. */
	public enum Mode {
		IDLE, LISTING, NEW, COPY, RENAME, ASKING
	}

	private static final int HEIGHT = 14;
	private static final int PADDING = 6;
	private static final int GAP = 4;
	private static final int NAME_WIDTH = 110;
	private static final int ROW = 12;
	private static final int BUTTON = 0xFF3A3A44;
	private static final int BUTTON_ON = 0xFF4C7AB0;
	private static final int EDGE = 0xFF2C2C34;
	private static final int TEXT = 0xFFE8E8EC;
	private static final int TEXT_DIM = 0xFF6A6A74;
	private static final int ERROR = 0xFFE8C34C;
	private static final int LIST = 0xFF26262E;

	private final Supplier<Profiles> profiles;
	private final Runnable onSwitch;
	private final Runnable openRules;
	private Mode mode = Mode.IDLE;
	private EditBox name;
	private String error = "";
	private final Map<Part, Rect2i> parts = new EnumMap<>(Part.class);
	private int left;
	private int top;

	/**
	 * @param onSwitch  called after the settings have changed under the menu: another profile, or a new one
	 * @param openRules the list's last line: which profile to be in on joining a world
	 */
	public ProfileBar(Supplier<Profiles> profiles, Runnable onSwitch, Runnable openRules) {
		this.profiles = profiles;
		this.onSwitch = onSwitch;
		this.openRules = openRules;
	}

	public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY, float partial) {
		left = x;
		top = y;
		layout(font);
		Profiles in = profiles.get();

		switch (mode) {
			case IDLE, LISTING -> {
				button(graphics, font, Part.PROFILE, Lang.of(Keys.MENU_PROFILE, Profiles.label(in.active())) + " ▾", true, mouseX, mouseY);
				button(graphics, font, Part.NEW, Lang.of(Keys.MENU_PROFILE_NEW), true, mouseX, mouseY);
				button(graphics, font, Part.COPY, Lang.of(Keys.MENU_PROFILE_COPY), true, mouseX, mouseY);
				button(graphics, font, Part.RENAME, Lang.of(Keys.MENU_PROFILE_RENAME), Profiles.canChange(in.active()), mouseX, mouseY);
				button(graphics, font, Part.DELETE, Lang.of(Keys.MENU_PROFILE_DELETE), Profiles.canChange(in.active()), mouseX, mouseY);
			}
			case NEW, COPY, RENAME -> {
				Rect2i box = parts.get(Part.PROFILE);
				graphics.fill(box.getX(), box.getY(), box.getX() + box.getWidth(), box.getY() + box.getHeight(), LIST);
				graphics.outline(box.getX(), box.getY(), box.getWidth(), box.getHeight(), BUTTON_ON);
				name.extractRenderState(graphics, mouseX, mouseY, partial);
				button(graphics, font, Part.OK, Lang.of(Keys.MENU_PROFILE_OK), true, mouseX, mouseY);
				button(graphics, font, Part.CANCEL, Lang.of(Keys.MENU_PROFILE_CANCEL), true, mouseX, mouseY);

				if (!error.isEmpty()) {
					Rect2i cancel = parts.get(Part.CANCEL);
					graphics.text(font, Component.literal(error), cancel.getX() + cancel.getWidth() + GAP, top + 3, ERROR);
				}
			}
			case ASKING -> {
				String question = Lang.of(Keys.MENU_PROFILE_DELETE_ASK, Profiles.label(in.active()));
				graphics.text(font, Component.literal(question), left, top + 3, ERROR);
				button(graphics, font, Part.YES, Lang.of(Keys.MENU_PROFILE_DELETE_YES), true, mouseX, mouseY);
				button(graphics, font, Part.CANCEL, Lang.of(Keys.MENU_PROFILE_CANCEL), true, mouseX, mouseY);
			}
		}
	}

	/** The open list, drawn last so it lies over the panels below the bar. */
	public void drawList(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		if (mode != Mode.LISTING) {
			return;
		}

		List<String> names = profiles.get().list();
		Rect2i under = listBounds(font, names);
		graphics.fill(under.getX(), under.getY(), under.getX() + under.getWidth(), under.getY() + under.getHeight(), LIST);
		graphics.outline(under.getX(), under.getY(), under.getWidth(), under.getHeight(), EDGE);

		for (int i = 0; i < names.size(); i++) {
			int y = under.getY() + i * ROW;
			boolean active = names.get(i).equals(profiles.get().active());
			boolean over = mouseX >= under.getX() && mouseX < under.getX() + under.getWidth() && mouseY >= y && mouseY < y + ROW;

			if (active || over) {
				graphics.fill(under.getX() + 1, y, under.getX() + under.getWidth() - 1, y + ROW, active ? BUTTON_ON : BUTTON);
			}

			graphics.text(font, Component.literal(Profiles.label(names.get(i))), under.getX() + PADDING, y + 2, TEXT);
		}

		// The way to the rules, last, under a line and dimmed: it is not a profile.
		Rect2i rules = rulesBounds(under, names);

		if (contains(rules, mouseX, mouseY)) {
			graphics.fill(rules.getX() + 1, rules.getY(), rules.getX() + rules.getWidth() - 1, rules.getY() + ROW, BUTTON);
		}

		graphics.fill(rules.getX() + 1, rules.getY(), rules.getX() + rules.getWidth() - 1, rules.getY() + 1, EDGE);
		graphics.text(font, Component.literal(Lang.of(Keys.MENU_PROFILE_RULES)), rules.getX() + PADDING, rules.getY() + 2, TEXT_DIM);
	}

	/** A click: true if the bar or its list took it. */
	public boolean click(Font font, double x, double y) {
		if (mode == Mode.LISTING) {
			List<String> names = profiles.get().list();
			Rect2i under = listBounds(font, names);
			mode = Mode.IDLE;

			if (contains(under, x, y)) {
				int index = (int) ((y - under.getY()) / ROW);

				if (index >= 0 && index < names.size()) {
					profiles.get().switchTo(names.get(index));
					onSwitch.run();
				} else if (index == names.size()) {
					openRules.run();
				}

				return true;
			}

			// Clicking anywhere else closes the list, and the click goes on to whatever is there.
			return contains(parts.get(Part.PROFILE), x, y);
		}

		Optional<Part> part = parts.entrySet().stream()
			.filter(entry -> shownIn(entry.getKey()) && contains(entry.getValue(), x, y))
			.map(Map.Entry::getKey)
			.findFirst();

		if (part.isEmpty()) {
			// A click in the name box is the box's; the cursor stays at the end.
			return naming() && contains(parts.get(Part.PROFILE), x, y);
		}

		Profiles in = profiles.get();

		switch (part.get()) {
			case PROFILE -> mode = Mode.LISTING;
			case NEW -> startNaming(font, Mode.NEW, "");
			case COPY -> startNaming(font, Mode.COPY, in.active());
			case RENAME -> {
				if (Profiles.canChange(in.active())) {
					startNaming(font, Mode.RENAME, in.active());
				}
			}
			case DELETE -> {
				if (Profiles.canChange(in.active())) {
					mode = Mode.ASKING;
				}
			}
			case OK -> finishNaming();
			case CANCEL -> cancel();
			case YES -> {
				in.delete();
				mode = Mode.IDLE;
				onSwitch.run();
			}
		}

		return true;
	}

	/** Keys while a name is being typed: Enter takes it, Escape gives up, the rest edit it. */
	public boolean key(KeyEvent event) {
		if (mode == Mode.LISTING && event.key() == InputConstants.KEY_ESCAPE || mode == Mode.ASKING && event.key() == InputConstants.KEY_ESCAPE) {
			cancel();
			return true;
		}

		if (!naming()) {
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
		return naming() && name.charTyped(event);
	}

	/** Whatever the bar was doing, it stops: the menu is closing. */
	public void cancel() {
		mode = Mode.IDLE;
		error = "";
		name = null;
	}

	private void startNaming(Font font, Mode naming, String start) {
		mode = naming;
		error = "";
		Rect2i box = parts.get(Part.PROFILE);
		name = new EditBox(font, box.getX() + 4, box.getY() + 3, NAME_WIDTH - 8, HEIGHT - 4, Component.literal(Lang.of(Keys.MENU_PROFILE_NEW)));
		name.setBordered(false);
		name.setMaxLength(ProfileNames.MAX_LENGTH);
		name.setValue(start);
		name.setFocused(true);
	}

	private void finishNaming() {
		Profiles in = profiles.get();
		String typed = name.getValue();
		Optional<ProfileNames.Problem> problem = switch (mode) {
			case NEW -> in.create(typed);
			case COPY -> in.copy(typed);
			case RENAME -> in.rename(typed);
			default -> Optional.empty();
		};

		if (problem.isPresent()) {
			error = Lang.of(switch (problem.get()) {
				case BLANK -> Keys.MENU_PROFILE_BLANK;
				case TOO_LONG -> Keys.MENU_PROFILE_TOO_LONG;
				case BAD_CHARACTER -> Keys.MENU_PROFILE_BAD_CHARACTER;
				case TAKEN -> Keys.MENU_PROFILE_TAKEN;
			});
			return;
		}

		cancel();
		onSwitch.run();
	}

	private boolean naming() {
		return mode == Mode.NEW || mode == Mode.COPY || mode == Mode.RENAME;
	}

	private boolean shownIn(Part part) {
		return switch (mode) {
			case IDLE, LISTING -> part == Part.PROFILE || part == Part.NEW || part == Part.COPY || part == Part.RENAME || part == Part.DELETE;
			case NEW, COPY, RENAME -> part == Part.OK || part == Part.CANCEL;
			case ASKING -> part == Part.YES || part == Part.CANCEL;
		};
	}

	/** Each part's place, left to right, as wide as its words; the name box where the profile button is. */
	private void layout(Font font) {
		parts.clear();
		Profiles in = profiles.get();
		int x = left;

		if (mode == Mode.ASKING) {
			x += font.width(Lang.of(Keys.MENU_PROFILE_DELETE_ASK, Profiles.label(in.active()))) + GAP;
			x = place(font, Part.YES, Lang.of(Keys.MENU_PROFILE_DELETE_YES), x);
			place(font, Part.CANCEL, Lang.of(Keys.MENU_PROFILE_CANCEL), x);
			return;
		}

		if (naming()) {
			parts.put(Part.PROFILE, new Rect2i(x, top, NAME_WIDTH, HEIGHT));
			x += NAME_WIDTH + GAP;
			x = place(font, Part.OK, Lang.of(Keys.MENU_PROFILE_OK), x);
			place(font, Part.CANCEL, Lang.of(Keys.MENU_PROFILE_CANCEL), x);
			return;
		}

		x = place(font, Part.PROFILE, Lang.of(Keys.MENU_PROFILE, Profiles.label(in.active())) + " ▾", x);
		x = place(font, Part.NEW, Lang.of(Keys.MENU_PROFILE_NEW), x);
		x = place(font, Part.COPY, Lang.of(Keys.MENU_PROFILE_COPY), x);
		x = place(font, Part.RENAME, Lang.of(Keys.MENU_PROFILE_RENAME), x);
		place(font, Part.DELETE, Lang.of(Keys.MENU_PROFILE_DELETE), x);
	}

	private int place(Font font, Part part, String label, int x) {
		int width = font.width(label) + 2 * PADDING;
		parts.put(part, new Rect2i(x, top, width, HEIGHT));
		return x + width + GAP;
	}

	private void button(GuiGraphicsExtractor graphics, Font font, Part part, String label, boolean enabled, int mouseX, int mouseY) {
		Rect2i box = parts.get(part);
		boolean over = enabled && contains(box, mouseX, mouseY);
		graphics.fill(box.getX(), box.getY(), box.getX() + box.getWidth(), box.getY() + box.getHeight(), over || part == Part.PROFILE && mode == Mode.LISTING ? BUTTON_ON : BUTTON);
		graphics.outline(box.getX(), box.getY(), box.getWidth(), box.getHeight(), EDGE);
		graphics.text(font, Component.literal(label), box.getX() + PADDING, box.getY() + 3, enabled ? TEXT : TEXT_DIM);
	}

	private Rect2i listBounds(Font font, List<String> names) {
		Rect2i under = parts.get(Part.PROFILE);
		int width = under.getWidth();

		for (String profile : names) {
			width = Math.max(width, font.width(Profiles.label(profile)) + 2 * PADDING);
		}

		width = Math.max(width, font.width(Lang.of(Keys.MENU_PROFILE_RULES)) + 2 * PADDING);
		// One line more than there are profiles: the way to the rules.
		return new Rect2i(under.getX(), under.getY() + under.getHeight(), width, (names.size() + 1) * ROW);
	}

	private static Rect2i rulesBounds(Rect2i list, List<String> names) {
		return new Rect2i(list.getX(), list.getY() + names.size() * ROW, list.getWidth(), ROW);
	}

	/** Where the list's last line, the way to the rules, is while the list is open. */
	public Optional<Rect2i> rulesRow(Font font) {
		return mode == Mode.LISTING ? Optional.of(rulesBounds(listBounds(font, profiles.get().list()), profiles.get().list())) : Optional.empty();
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

	/** Where a part is now, or null while the bar is not showing it. */
	public Rect2i part(Part part) {
		return shownIn(part) ? parts.get(part) : null;
	}

	/** Where a profile's row is in the open list. */
	public Optional<Rect2i> listRow(Font font, String profile) {
		List<String> names = profiles.get().list();
		int index = names.indexOf(profile);

		if (mode != Mode.LISTING || index < 0) {
			return Optional.empty();
		}

		Rect2i under = listBounds(font, names);
		return Optional.of(new Rect2i(under.getX(), under.getY() + index * ROW, under.getWidth(), ROW));
	}
}
