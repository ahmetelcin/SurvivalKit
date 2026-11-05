package com.merci.survivalkit.menu;

import com.merci.survivalkit.settings.RegistryListSetting;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The candidates the picker offers, resolved from the registries once and kept.
 *
 * <p>Walking the item registry and asking each item for its translated name is not free, and
 * the picker is judged on how fast it opens. So each kind is built the first time it is asked
 * for and reused after that. The names come from the game, so this has to be built after the
 * language is loaded — the first time a picker opens is exactly right.
 */
public final class ItemCatalogue {
	private static final Map<RegistryListSetting.Kind, List<ItemChoice>> CACHE =
		new EnumMap<>(RegistryListSetting.Kind.class);

	private ItemCatalogue() {
	}

	public static List<ItemChoice> forKind(RegistryListSetting.Kind kind) {
		return CACHE.computeIfAbsent(kind, ItemCatalogue::build);
	}

	/** Thrown away when the language changes, so names do not go stale. */
	public static void forget() {
		CACHE.clear();
	}

	private static List<ItemChoice> build(RegistryListSetting.Kind kind) {
		return switch (kind) {
			case DIMENSION -> dimensions();
			case FOOD -> items(true);
			case ITEM -> items(false);
			case BLOCK -> blocks();
		};
	}

	/**
	 * Blocks listed by their block id, which is what the game reports for the block under
	 * the crosshair. Only those with an item of the same id are offered: the menu draws a
	 * list entry's icon and name from the item, and a block with no item of its own — fire,
	 * a wall torch, water — is not something one sets out to mine anyway.
	 */
	private static List<ItemChoice> blocks() {
		return BuiltInRegistries.BLOCK.stream()
			.map(block -> BuiltInRegistries.BLOCK.getKey(block))
			.filter(BuiltInRegistries.ITEM::containsKey)
			.map(id -> new ItemChoice(id.toString(),
				new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getHoverName().getString()))
			.sorted(java.util.Comparator.comparing(ItemChoice::displayName, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	private static List<ItemChoice> items(boolean edibleOnly) {
		return BuiltInRegistries.ITEM.stream()
			.filter(item -> !edibleOnly || isEdible(item))
			.map(item -> new ItemChoice(
				BuiltInRegistries.ITEM.getKey(item).toString(),
				new ItemStack(item).getHoverName().getString()))
			.sorted(java.util.Comparator.comparing(ItemChoice::displayName, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	private static boolean isEdible(Item item) {
		return new ItemStack(item).get(DataComponents.FOOD) != null;
	}

	/** Only the three that always exist; a server's own dimensions are typed in by hand. */
	private static List<ItemChoice> dimensions() {
		return Stream.of(Level.OVERWORLD, Level.NETHER, Level.END)
			.map(level -> level.identifier())
			.map(id -> new ItemChoice(id.toString(), Lang.of(Keys.dimension(id.getPath()))))
			.toList();
	}
}
