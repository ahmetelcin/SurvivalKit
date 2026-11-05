package com.merci.survivalkit.menu;

import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.preset.PresetUndo;
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.settings.SettingNode;
import com.merci.survivalkit.util.SearchText;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Everything the menu knows that is not about pixels: which modules are listed and in what
 * order, which one is selected, which lines the right-hand panel shows, and what the search
 * box has narrowed things down to.
 *
 * <p>Kept free of Minecraft so the rules can be tried without starting a game. The screen
 * asks this what to draw and does no deciding of its own.
 *
 * <p>One of these outlives any single screen, which is how the selection is still there the
 * next time the menu is opened.
 */
public final class MenuModel {
	private List<Module> modules;
	/** By the heading itself, not its id: every module has a "hands" heading of its own. */
	private final Set<SettingGroup> collapsed = Collections.newSetFromMap(new IdentityHashMap<>());
	private String query = "";
	private Module selected;
	/** What presets changed, for the rows' ↺; it lives as long as the menu's selection does. */
	private final PresetUndo presetUndo = new PresetUndo();

	public MenuModel(List<Module> modules) {
		this(modules, Collator.getInstance(Locale.ROOT));
	}

	public MenuModel(List<Module> modules, Collator collator) {
		Objects.requireNonNull(modules, "modules");
		this.modules = sort(modules, collator);
		this.selected = this.modules.isEmpty() ? null : this.modules.getFirst();
	}

	/**
	 * Re-orders the list for the language now in use, keeping the selection. Called when the
	 * menu opens, because the player may have changed language since it was built.
	 */
	public void resort(Collator collator) {
		this.modules = sort(this.modules, collator);
	}

	/**
	 * Grouped the way the categories are declared, then by name inside a group.
	 *
	 * <p>The name order comes from a {@link Collator} rather than from comparing strings,
	 * because plain comparison sorts by code point: Ç, Ş and Ü land after Z instead of next
	 * to C, S and U, which is not where a Turkish reader looks for them.
	 */
	private static List<Module> sort(List<Module> modules, Collator collator) {
		Objects.requireNonNull(collator, "collator");

		return modules.stream()
			.sorted(Comparator.comparingInt((Module module) -> module.category().ordinal())
				.thenComparing(Module::displayName, collator))
			.toList();
	}

	/** Every module, in menu order, whatever the search box says. */
	public List<Module> modules() {
		return modules;
	}

	/** The modules the search box leaves standing, in menu order. */
	public List<Module> visibleModules() {
		if (query.isBlank()) {
			return modules;
		}

		return modules.stream().filter(this::matches).toList();
	}

	/** A module stays in the list if its own name matches, or anything inside it does. */
	private boolean matches(Module module) {
		return nameMatches(module.displayName()) || nameMatches(module.id())
			|| !rowsOf(module, true).isEmpty();
	}

	/** Both sides are folded first, so Turkish spelling and keyboard layout stop mattering. */
	private boolean nameMatches(String text) {
		return SearchText.contains(text, query);
	}

	/**
	 * What the search found inside the modules, in menu order.
	 *
	 * <p>Settings only: a heading is nothing to jump to, and a module already answers for its
	 * own name in the list above. Folds are ignored, because something tucked under a closed
	 * heading is exactly what a search is for; a setting its own rule hides is left out,
	 * since jumping to a line the panel will not draw goes nowhere.
	 */
	public List<SearchHit> results() {
		if (query.isBlank()) {
			return List.of();
		}

		return modules.stream()
			.flatMap(module -> rowsOf(module, true).stream()
				.filter(row -> !row.isGroup())
				.map(row -> new SearchHit(module, row.node())))
			.toList();
	}

	public Optional<Module> selected() {
		return Optional.ofNullable(selected);
	}

	/** Ignores a module that is not in this menu, so a stale selection cannot take hold. */
	public void select(Module module) {
		if (module != null && modules.contains(module)) {
			this.selected = module;
		}
	}

	public PresetUndo presetUndo() {
		return presetUndo;
	}

	public String query() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query == null ? "" : query.trim();
	}

	/** Headings start open and fold one at a time; the state lives as long as this model, so it
	 * survives the menu being closed and opened again. */
	public boolean isCollapsed(SettingGroup group) {
		return collapsed.contains(group);
	}

	public void toggleCollapsed(SettingGroup group) {
		if (!collapsed.remove(group)) {
			collapsed.add(group);
		}
	}

	/**
	 * The lines of the right-hand panel for the selected module, headings and settings
	 * interleaved in declaration order.
	 *
	 * <p>Three things keep a line out: the setting's own visibility rule, a folded heading
	 * above it, and the search box. A hidden line is simply absent — the panel closes up
	 * around it rather than leaving a gap.
	 */
	public List<MenuRow> rows() {
		return selected == null ? List.of() : rowsOf(selected, false);
	}

	/** With {@code ignoreFolds} the tree is walked as if nothing were folded, which is what
	 * the search needs: a match must count even while it is tucked away under a closed heading. */
	private List<MenuRow> rowsOf(Module module, boolean ignoreFolds) {
		List<MenuRow> rows = new ArrayList<>();
		collect(module.settings(), 0, rows, ignoreFolds, false);
		return List.copyOf(rows);
	}

	/**
	 * @param showAll true once a heading above this one matched the search by name, which
	 *                makes everything under it a match too — a heading with nothing under it
	 *                would be a dead end.
	 */
	private void collect(SettingGroup group, int depth, List<MenuRow> rows, boolean ignoreFolds, boolean showAll) {
		for (SettingNode child : group.children()) {
			if (!child.isVisible()) {
				continue;
			}

			if (child instanceof SettingGroup nested) {
				boolean headingMatches = showAll || matchesQuery(nested);
				List<MenuRow> inside = new ArrayList<>();
				collect(nested, depth + 1, inside, ignoreFolds, headingMatches);

				if (inside.isEmpty()) {
					continue;
				}

				rows.add(new MenuRow(nested, depth));

				if (ignoreFolds || !isCollapsed(nested)) {
					rows.addAll(inside);
				}

				continue;
			}

			if (showAll || matchesQuery(child)) {
				rows.add(new MenuRow(child, depth));
			}
		}
	}

	private boolean matchesQuery(SettingNode node) {
		return query.isBlank() || nameMatches(node.displayName()) || nameMatches(node.id());
	}
}
