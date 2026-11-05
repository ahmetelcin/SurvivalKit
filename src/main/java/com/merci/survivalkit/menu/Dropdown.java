package com.merci.survivalkit.menu;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * The open list of an enum setting whose values will not fit beside each other: where it sits
 * and which option the mouse is over.
 *
 * <p>All of it is arithmetic, with no screen and no font, so the rule that decides what is
 * clicked is the same object the screen asks where to draw. That is the whole point of it
 * existing: the first version of this control had a drawing rule and a clicking rule that
 * disagreed, and the button ended up dividing its own width between values it was no longer
 * drawing as separate buttons.
 */
public final class Dropdown {
	/** Height of one option row, in GUI units — tall enough to aim at with a mouse. */
	public static final int ROW_HEIGHT = 12;

	/** Where the open list is, in GUI units. */
	public record Bounds(int x, int y, int width, int height) {
		public boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private String openId = "";
	private Bounds bounds = new Bounds(0, 0, 0, 0);
	private int options;

	/**
	 * Opens the list for one setting.
	 *
	 * <p>It hangs under the button when there is room for it inside the panel and stands on
	 * top of the button when there is not. A list running off the end of the panel would have
	 * options that cannot be seen, and clicking where they are not drawn is exactly the bug
	 * this class was written to stop.
	 */
	public void open(String id, int x, int buttonTop, int buttonBottom, int width, int options,
			int panelTop, int panelBottom) {
		this.openId = Objects.requireNonNull(id, "id");
		this.options = options;

		int height = options * ROW_HEIGHT;
		int below = buttonBottom;
		int above = buttonTop - height;
		int y = below + height <= panelBottom || above < panelTop ? below : above;

		this.bounds = new Bounds(x, y, width, height);
	}

	public void close() {
		openId = "";
		options = 0;
		bounds = new Bounds(0, 0, 0, 0);
	}

	public boolean isOpen() {
		return !openId.isEmpty();
	}

	public boolean isOpen(String settingId) {
		return isOpen() && openId.equals(settingId);
	}

	public String openId() {
		return openId;
	}

	public Bounds bounds() {
		return bounds;
	}

	/** Top of one option row, so labels are drawn where clicks are answered. */
	public int optionTop(int index) {
		return bounds.y() + index * ROW_HEIGHT;
	}

	/** Which option the mouse is over, or empty when it is not over the list at all. */
	public OptionalInt optionAt(double mouseX, double mouseY) {
		if (!contains(mouseX, mouseY)) {
			return OptionalInt.empty();
		}

		int index = (int) ((mouseY - bounds.y()) / ROW_HEIGHT);
		return index >= 0 && index < options ? OptionalInt.of(index) : OptionalInt.empty();
	}

	public boolean contains(double mouseX, double mouseY) {
		return isOpen() && bounds.contains(mouseX, mouseY);
	}
}
