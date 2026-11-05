package com.merci.survivalkit.menu;

import com.merci.survivalkit.util.SearchText;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * What the item picker shows: everything on offer, narrowed by the search box, with the
 * entries already on the list marked so they can be drawn faded.
 *
 * <p>The candidates are handed in already resolved, so this knows nothing about registries
 * and can be tried without a game. They are fetched once and kept: the picker is judged on
 * how fast it opens, and walking the whole item registry on every keystroke would not do.
 */
public final class PickerModel {
	private final List<ItemChoice> all;
	private final Supplier<List<String>> picked;
	private String query = "";
	private List<ItemChoice> visible;

	public PickerModel(List<ItemChoice> all, Supplier<List<String>> picked) {
		this.all = List.copyOf(Objects.requireNonNull(all, "all"));
		this.picked = Objects.requireNonNull(picked, "picked");
		this.visible = this.all;
	}

	/** Everything on offer, in the order it was given. */
	public List<ItemChoice> all() {
		return all;
	}

	/** What the search box leaves standing. */
	public List<ItemChoice> visible() {
		return visible;
	}

	public String query() {
		return query;
	}

	/**
	 * Narrowing happens here rather than while drawing, so the cost is paid once per
	 * keystroke instead of once per frame.
	 */
	public void setQuery(String query) {
		String wanted = query == null ? "" : query.trim();

		if (wanted.equals(this.query)) {
			return;
		}

		this.query = wanted;
		this.visible = wanted.isBlank()
			? all
			: all.stream().filter(choice -> matches(choice, wanted)).toList();
	}

	/** Name or id, both folded, so Turkish spelling and keyboard layout stop mattering. */
	private static boolean matches(ItemChoice choice, String query) {
		return SearchText.contains(choice.displayName(), query) || SearchText.contains(choice.id(), query);
	}

	/** True for something already on the list, which the picker draws faded. */
	public boolean isPicked(ItemChoice choice) {
		return picked.get().contains(choice.id());
	}
}
