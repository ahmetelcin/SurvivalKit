package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.autototem.AutoTotem;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.scaffold.BlockChoice;
import com.merci.survivalkit.settings.RegistryListSetting;
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.settings.SettingNode;
import com.merci.survivalkit.vein.OreDrops;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Every id the mod ships with names something this game version actually has.
 *
 * <p>Ids are text, so a rename between game versions breaks nothing loudly: the entry just
 * matches nothing from then on. Walking every module's settings tree, like the tooltip scan,
 * means a list added later is held to this without anybody coming back here.
 */
public class RegistryIdsGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> problems = new ArrayList<>();
		int[] checked = {0};

		// Item components are bound only once a world is loaded; the food check needs them.
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.runOnClient(client -> {
				for (Module module : SurvivalKit.modules().modules()) {
					walk(module.settings(), module.id(), problems, checked);
				}

				for (String torch : BlockChoice.TORCHES) {
					checkBlock("BlockChoice.TORCHES", torch, problems, checked);
				}

				checkItem("AutoTotem.TOTEM", AutoTotem.TOTEM, false, problems, checked);

				OreDrops.all().forEach((block, drop) -> {
					checkBlock("OreDrops", block, problems, checked);
					checkItem("OreDrops." + block, drop.item(), false, problems, checked);
				});
			});
		}

		if (!problems.isEmpty()) {
			throw new AssertionError("oyunda olmayan ya da türü tutmayan id: " + problems);
		}

		SurvivalKit.LOGGER.info("[gametest] id taraması geçti ({} id, hepsi bu sürümde var)", checked[0]);
	}

	private static void walk(SettingGroup group, String path, List<String> problems, int[] checked) {
		for (SettingNode child : group.children()) {
			String childPath = path + "." + child.id();

			if (child instanceof SettingGroup nested) {
				walk(nested, childPath, problems, checked);
			} else if (child instanceof RegistryListSetting list) {
				for (String id : list.defaultValue()) {
					switch (list.kind()) {
						case BLOCK -> checkBlock(childPath, id, problems, checked);
						case ITEM -> checkItem(childPath, id, false, problems, checked);
						case FOOD -> checkItem(childPath, id, true, problems, checked);
						// Dimensions live in the server's registry, not a built-in one; the
						// shipped lists name none.
						case DIMENSION -> problems.add(childPath + ": varsayılan boyut listesi denetlenmiyor: " + id);
					}
				}
			}
		}
	}

	/** The menu draws a block entry's icon and name from the item of the same id, so both must exist. */
	private static void checkBlock(String where, String id, List<String> problems, int[] checked) {
		checked[0]++;
		Identifier key = Identifier.parse(id);

		if (!BuiltInRegistries.BLOCK.containsKey(key)) {
			problems.add(where + ": blok yok: " + id);
		} else if (!BuiltInRegistries.ITEM.containsKey(key)) {
			problems.add(where + ": bloğun eşyası yok: " + id);
		}
	}

	private static void checkItem(String where, String id, boolean food, List<String> problems, int[] checked) {
		checked[0]++;
		Identifier key = Identifier.parse(id);

		if (!BuiltInRegistries.ITEM.containsKey(key)) {
			problems.add(where + ": eşya yok: " + id);
		} else if (food && new ItemStack(BuiltInRegistries.ITEM.getValue(key)).get(DataComponents.FOOD) == null) {
			problems.add(where + ": yenmiyor: " + id);
		}
	}
}
