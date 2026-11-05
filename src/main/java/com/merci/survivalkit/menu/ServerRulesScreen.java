package com.merci.survivalkit.menu;

import com.merci.survivalkit.profile.Profiles;
import com.merci.survivalkit.profile.ServerRules;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/**
 * Which profile to be in on joining a world: one for singleplayer, one for a server no rule
 * matches — each may keep the profile in use — and the address rules, top to bottom. A profile
 * button steps through the profiles: left click forward, right click back. Every change is
 * written at once; a rule with no address is dropped. Done, or Escape, goes back to the menu.
 */
public final class ServerRulesScreen extends Screen {
	private static final int WIDTH = 320;
	private static final int ROW = 20;
	private static final int HEIGHT = 14;
	private static final int ADDRESS_WIDTH = 150;
	private static final int PROFILE_WIDTH = 110;
	private static final int CROSS = 14;
	private static final int PADDING = 6;
	private static final int PANEL = 0xC0101014;
	private static final int EDGE = 0xFF2C2C34;
	private static final int BUTTON = 0xFF3A3A44;
	private static final int BUTTON_ON = 0xFF4C7AB0;
	private static final int BOX = 0xFF26262E;
	private static final int TEXT = 0xFFE8E8EC;
	private static final int TEXT_DIM = 0xFF9A9AA4;

	private final Screen parent;
	private final Profiles profiles;
	/** The rules as they are being edited: an address and the profile it chooses, in order. */
	private final List<String> addresses = new ArrayList<>();
	private final List<String> targets = new ArrayList<>();
	private final List<EditBox> boxes = new ArrayList<>();
	private String singleplayer;
	private String fallback;

	public ServerRulesScreen(Screen parent, Profiles profiles) {
		super(Component.literal(Lang.of(Keys.RULES_TITLE)));
		this.parent = parent;
		this.profiles = profiles;
		ServerRules rules = profiles.rules();

		for (ServerRules.Rule rule : rules.rules()) {
			addresses.add(rule.address());
			targets.add(rule.profile());
		}

		singleplayer = rules.singleplayer();
		fallback = rules.fallback();
	}

	@Override
	protected void init() {
		boxes.clear();

		for (int i = 0; i < addresses.size(); i++) {
			int index = i;
			Rect2i at = ruleAddress(i);
			EditBox box = new EditBox(font, at.getX() + 4, at.getY() + 3, at.getWidth() - 8, HEIGHT - 4, Component.literal(Lang.of(Keys.RULES_ADDRESS_HINT)));
			box.setBordered(false);
			box.setMaxLength(64);
			box.setValue(addresses.get(i));
			box.setHint(Component.literal(Lang.of(Keys.RULES_ADDRESS_HINT)));
			box.setResponder(value -> {
				addresses.set(index, value);
				write();
			});
			addWidget(box);
			boxes.add(box);
		}
	}

	private int left() {
		return (width - WIDTH) / 2;
	}

	private int rulesTop() {
		return 92;
	}

	// --- where things are; the tests aim here too ---

	public Rect2i singleplayerButton() {
		return new Rect2i(left() + WIDTH - PROFILE_WIDTH, 34, PROFILE_WIDTH, HEIGHT);
	}

	public Rect2i fallbackButton() {
		return new Rect2i(left() + WIDTH - PROFILE_WIDTH, 54, PROFILE_WIDTH, HEIGHT);
	}

	public Rect2i ruleAddress(int index) {
		return new Rect2i(left(), rulesTop() + index * ROW, ADDRESS_WIDTH, HEIGHT);
	}

	public Rect2i ruleProfile(int index) {
		return new Rect2i(left() + ADDRESS_WIDTH + 24, rulesTop() + index * ROW, PROFILE_WIDTH, HEIGHT);
	}

	public Rect2i ruleDelete(int index) {
		return new Rect2i(left() + ADDRESS_WIDTH + 24 + PROFILE_WIDTH + 6, rulesTop() + index * ROW, CROSS, HEIGHT);
	}

	public Rect2i addButton() {
		return new Rect2i(left(), rulesTop() + addresses.size() * ROW, font.width(Lang.of(Keys.RULES_ADD)) + 2 * PADDING, HEIGHT);
	}

	public Rect2i doneButton() {
		int labelWidth = font.width(Lang.of(Keys.RULES_DONE)) + 2 * PADDING;
		return new Rect2i(left() + WIDTH - labelWidth, height - 24, labelWidth, HEIGHT);
	}

	public int ruleCount() {
		return addresses.size();
	}

	// --- drawing ---

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		super.extractRenderState(graphics, mouseX, mouseY, partial);
		int x = left();
		graphics.fill(x - 8, 6, x + WIDTH + 8, height - 6, PANEL);
		graphics.outline(x - 8, 6, WIDTH + 16, height - 12, EDGE);
		graphics.text(font, Component.literal(Lang.of(Keys.RULES_TITLE)), x, 14, TEXT);

		graphics.text(font, Component.literal(Lang.of(Keys.RULES_SINGLEPLAYER)), x, 37, TEXT);
		button(graphics, singleplayerButton(), profileLabel(singleplayer), mouseX, mouseY);
		graphics.text(font, Component.literal(Lang.of(Keys.RULES_FALLBACK)), x, 57, TEXT);
		button(graphics, fallbackButton(), profileLabel(fallback), mouseX, mouseY);

		graphics.text(font, Component.literal(Lang.of(Keys.RULES_LIST)), x, 78, TEXT_DIM);

		for (int i = 0; i < addresses.size(); i++) {
			Rect2i box = ruleAddress(i);
			graphics.fill(box.getX(), box.getY(), box.getX() + box.getWidth(), box.getY() + box.getHeight(), BOX);
			graphics.outline(box.getX(), box.getY(), box.getWidth(), box.getHeight(), boxes.get(i).isFocused() ? BUTTON_ON : EDGE);
			boxes.get(i).extractRenderState(graphics, mouseX, mouseY, partial);
			graphics.text(font, Component.literal("→"), box.getX() + box.getWidth() + 8, box.getY() + 3, TEXT_DIM);
			button(graphics, ruleProfile(i), Profiles.label(targets.get(i)), mouseX, mouseY);
			Rect2i cross = ruleDelete(i);
			graphics.text(font, Component.literal("✕"), cross.getX() + 3, cross.getY() + 3, cross.contains(mouseX, mouseY) ? TEXT : TEXT_DIM);

			if (cross.contains(mouseX, mouseY)) {
				graphics.setTooltipForNextFrame(Component.literal(Lang.of(Keys.RULES_DELETE)), mouseX, mouseY);
			}
		}

		Rect2i add = addButton();
		button(graphics, add, Lang.of(Keys.RULES_ADD), mouseX, mouseY);
		graphics.textWithWordWrap(font, Component.literal(Lang.of(Keys.RULES_HINT)), x, add.getY() + ROW + 4, WIDTH, TEXT_DIM);
		button(graphics, doneButton(), Lang.of(Keys.RULES_DONE), mouseX, mouseY);
	}

	private void button(GuiGraphicsExtractor graphics, Rect2i box, String label, int mouseX, int mouseY) {
		boolean over = box.contains(mouseX, mouseY);
		graphics.fill(box.getX(), box.getY(), box.getX() + box.getWidth(), box.getY() + box.getHeight(), over ? BUTTON_ON : BUTTON);
		graphics.outline(box.getX(), box.getY(), box.getWidth(), box.getHeight(), EDGE);
		graphics.text(font, Component.literal(label), box.getX() + PADDING, box.getY() + 3, TEXT);
	}

	/** A profile as the buttons show it; null is keeping the one in use. */
	private static String profileLabel(String profile) {
		return profile == null ? Lang.of(Keys.RULES_KEEP) : Profiles.label(profile);
	}

	// --- clicks ---

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int x = (int) event.x();
		int y = (int) event.y();
		boolean back = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;

		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT || back) {
			if (singleplayerButton().contains(x, y)) {
				singleplayer = step(singleplayer, true, back);
				write();
				return true;
			}

			if (fallbackButton().contains(x, y)) {
				fallback = step(fallback, true, back);
				write();
				return true;
			}

			for (int i = 0; i < addresses.size(); i++) {
				if (ruleProfile(i).contains(x, y)) {
					targets.set(i, step(targets.get(i), false, back));
					write();
					return true;
				}

				if (!back && ruleDelete(i).contains(x, y)) {
					addresses.remove(i);
					targets.remove(i);
					write();
					rebuildWidgets();
					return true;
				}
			}

			if (!back && addButton().contains(x, y)) {
				addresses.add("");
				targets.add(profiles.active());
				rebuildWidgets();
				setFocused(boxes.getLast());
				boxes.getLast().setFocused(true);
				return true;
			}

			if (!back && doneButton().contains(x, y)) {
				onClose();
				return true;
			}
		}

		// A click in a box's frame is the box's, as in the menu.
		for (int i = 0; i < boxes.size(); i++) {
			if (ruleAddress(i).contains(x, y)) {
				setFocused(boxes.get(i));
				boxes.get(i).setFocused(true);
				return true;
			}
		}

		setFocused(null);
		return super.mouseClicked(event, doubleClick);
	}

	/** The next profile along, or back; with {@code keep}, "keep the one in use" is one of them. */
	private String step(String current, boolean keep, boolean back) {
		List<String> options = new ArrayList<>();

		if (keep) {
			options.add(null);
		}

		options.addAll(profiles.list());
		int at = Math.max(0, options.indexOf(current));
		return options.get(Math.floorMod(at + (back ? -1 : 1), options.size()));
	}

	private void write() {
		ServerRules rules = new ServerRules();
		List<ServerRules.Rule> kept = new ArrayList<>();

		for (int i = 0; i < addresses.size(); i++) {
			if (!addresses.get(i).isBlank()) {
				kept.add(new ServerRules.Rule(addresses.get(i).strip(), targets.get(i)));
			}
		}

		rules.setRules(kept);
		rules.setSingleplayer(singleplayer);
		rules.setFallback(fallback);
		profiles.setRules(rules);
	}

	@Override
	public void onClose() {
		write();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
