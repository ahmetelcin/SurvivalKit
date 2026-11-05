package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.util.InventoryUtil;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * İskele's normal mode, walked for real: the forward key held down a walkway that ends in air.
 * On, cobblestone goes down under each step and the feet never drop; the pickaxe comes back to
 * hand afterwards. Off, the same walk falls. From the backpack the stack is swapped in and back.
 * Lava is filled by default, water only when asked.
 */
public class ScaffoldGameTest implements FabricClientGameTest {
	/** About 8.6 blocks of walking: past the three-block walkway and five blocks over the gap. */
	private static final int WALK_TICKS = 40;
	private static final String COBBLE = "minecraft:cobblestone";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			ScaffoldTestSupport stage = new ScaffoldTestSupport(context, world);

			onABlockGoesUnderEachStepAndTheFeetNeverDrop(context, stage);
			offTheSameWalkFalls(stage);
			fromTheBackpackTheStackIsSwappedInAndBack(context, stage);
			lavaIsFilledWaterOnlyWhenAsked(stage);

			SurvivalKit.LOGGER.info("[gametest] İskele normal mod doğrulandı (gerçek ileri tuşuyla boşluğa yürüdü, altına parke taşı kondu, "
				+ "düşmedi, kazma ele döndü; kapalıyken düştü; sırt çantasından takas ve geri; lav dolduruldu, su yalnız istenince)");
		}
	}

	private static void onABlockGoesUnderEachStepAndTheFeetNeverDrop(ClientGameTestContext context, ScaffoldTestSupport stage) {
		stage.enable(true);
		stage.stage("hotbar.2", COBBLE + " 64");
		double level = stage.feetY();

		double lowest = stage.walk(WALK_TICKS);

		List<String> placed = stage.gapBlocks(5);

		if (!placed.equals(Collections.nCopies(5, COBBLE))) {
			throw new AssertionError("boşluğun ilk beş bloğu parke taşı olmalıydı, olan: " + placed);
		}

		if (lowest < level - 0.01) {
			throw new AssertionError("oyuncu düşmemeliydi: ayak " + level + " → en alçak " + lowest);
		}

		if (stage.feetZ() < stage.gap(4).getZ()) {
			throw new AssertionError("hazırlık: oyuncu boşluğun beşinci bloğuna varmalıydı, z = " + stage.feetZ());
		}

		context.waitTicks(20);

		if (stage.selectedSlot() != 0) {
			throw new AssertionError("bloklar bitince kazma ele dönmeliydi, seçili slot: " + stage.selectedSlot());
		}

		int used = 64 - stage.serverCount(2);

		if (used < 5) {
			throw new AssertionError("en az beş parke taşı harcanmalıydı, harcanan: " + used);
		}
	}

	/** The negative control: the same walk with the module off leaves the gap empty and falls. */
	private static void offTheSameWalkFalls(ScaffoldTestSupport stage) {
		stage.enable(false);
		stage.stage("hotbar.2", COBBLE + " 64");
		double level = stage.feetY();

		double lowest = stage.walk(WALK_TICKS);

		if (!"minecraft:air".equals(stage.block(stage.gap(0))) || lowest > level - ScaffoldTestSupport.DROP + 0.01) {
			throw new AssertionError("negatif kontrol başarısız: kapalıyken boşluk boş kalmalı ve oyuncu düşmeliydi: "
				+ stage.block(stage.gap(0)) + ", en alçak " + lowest + " (başlangıç " + level + ")");
		}
	}

	private static void fromTheBackpackTheStackIsSwappedInAndBack(ClientGameTestContext context, ScaffoldTestSupport stage) {
		stage.enable(true);
		stage.set("scope", InventoryUtil.Scope.FULL);
		// inventory.5 is slot 14: the backpack's first row.
		stage.stage("inventory.5", COBBLE + " 64");
		double level = stage.feetY();

		double lowest = stage.walk(WALK_TICKS);
		context.waitTicks(20);

		List<String> placed = stage.gapBlocks(5);

		if (!placed.equals(Collections.nCopies(5, COBBLE)) || lowest < level - 0.01) {
			throw new AssertionError("sırt çantasındaki parke taşıyla boşluk dolmalı, oyuncu düşmemeliydi: " + placed + ", en alçak " + lowest);
		}

		if (!"minecraft:stone_pickaxe".equals(stage.serverItem(0)) || !COBBLE.equals(stage.serverItem(14)) || stage.selectedSlot() != 0) {
			throw new AssertionError("takas geri alınmalıydı: slot 0 " + stage.serverItem(0) + ", slot 14 " + stage.serverItem(14)
				+ ", seçili " + stage.selectedSlot());
		}

		stage.set("scope", InventoryUtil.Scope.HOTBAR);
	}

	private static void lavaIsFilledWaterOnlyWhenAsked(ScaffoldTestSupport stage) {
		stage.enable(true);
		stage.stage("hotbar.2", COBBLE + " 64");
		stage.put(stage.gap(0), "minecraft:lava");
		double level = stage.feetY();

		double lowest = stage.walk(24);

		if (!COBBLE.equals(stage.block(stage.gap(0))) || lowest < level - 0.01) {
			throw new AssertionError("lav parke taşıyla doldurulmalı, oyuncu düşmemeliydi: " + stage.block(stage.gap(0)) + ", en alçak " + lowest);
		}

		stage.stage("hotbar.2", COBBLE + " 64");
		stage.put(stage.gap(0), "minecraft:water");
		lowest = stage.walk(24);

		if (!"minecraft:water".equals(stage.block(stage.gap(0))) || lowest > level - 0.3) {
			throw new AssertionError("su varsayılan olarak doldurulmamalıydı: " + stage.block(stage.gap(0)) + ", en alçak " + lowest);
		}

		stage.set("fill_water", true);
		stage.stage("hotbar.2", COBBLE + " 64");
		stage.put(stage.gap(0), "minecraft:water");
		lowest = stage.walk(24);

		if (!COBBLE.equals(stage.block(stage.gap(0))) || lowest < level - 0.01) {
			throw new AssertionError("\"Suyu doldur\" açıkken su doldurulmalıydı: " + stage.block(stage.gap(0)) + ", en alçak " + lowest);
		}
	}
}
