package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.scaffold.SafeWalk;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.lang.reflect.Field;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Items;

/**
 * What İskele shows, after a real walk over the gap: the blocks left on the action bar, a red
 * warning below the threshold and when they run out, nothing with the count off; and the green
 * box round the block still to fill, gone with the outline off.
 */
public class ScaffoldInfoGameTest implements FabricClientGameTest {
	private static final String COBBLE = "minecraft:cobblestone";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			ScaffoldTestSupport stage = new ScaffoldTestSupport(context, world);
			// These scenes read the action bar, which since the HUD hears Scaffold only when asked to.
			stage.set("action_bar", true);
			stage.enable(true);

			try {
				theCountIsOnTheActionBar(context, stage);
				fewLeftItIsAWarning(context, stage);
				noneLeftItSaysSo(context, stage);
				withTheCountOffNothingIsSaid(context, stage);
				theNextBlockIsOutlined(context, stage);
			} catch (AssertionError e) {
				SurvivalKit.LOGGER.error("[gametest-hata] {}", e.getMessage());
				throw e;
			}

			SurvivalKit.LOGGER.info("[gametest] İskele bilgi doğrulandı (eylem çubuğunda kalan blok, eşiğin altında ve bitince kırmızı uyarı, "
				+ "kapalıyken yok; sonraki blok yeşil kutuda, kapalıyken yok)");
		}
	}

	private static void theCountIsOnTheActionBar(ClientGameTestContext context, ScaffoldTestSupport stage) {
		stage.stage("hotbar.2", COBBLE + " 40");
		stage.command("item replace entity @a hotbar.3 with minecraft:dirt 20");
		clearActionBar(context);
		stage.walk(40);

		int left = context.computeOnClient(client -> client.player.getInventory().countItem(Items.COBBLESTONE)
			+ client.player.getInventory().countItem(Items.DIRT));
		Component shown = actionBar(context);
		String expected = context.computeOnClient(client -> Lang.of(Keys.SCAFFOLD_COUNT, left));

		if (left >= 60 || shown == null || !expected.equals(shown.getString()) || red(shown)) {
			throw new AssertionError("eylem çubuğunda \"" + expected + "\" yazmalıydı (beyaz), olan: " + describe(shown) + "; kalan " + left);
		}
	}

	private static void fewLeftItIsAWarning(ClientGameTestContext context, ScaffoldTestSupport stage) {
		stage.stage("hotbar.2", COBBLE + " 10");
		clearActionBar(context);
		stage.walk(24);

		int left = context.computeOnClient(client -> client.player.getInventory().countItem(Items.COBBLESTONE));
		Component shown = actionBar(context);
		String expected = context.computeOnClient(client -> Lang.of(Keys.SCAFFOLD_LOW, left));

		if (left >= 10 || shown == null || !expected.equals(shown.getString()) || !red(shown)) {
			throw new AssertionError("16'nın altında kırmızı \"" + expected + "\" yazmalıydı, olan: " + describe(shown));
		}
	}

	/** Three blocks for a longer gap: SafeWalk stops the player at the end of them and the bar says none are left. */
	private static void noneLeftItSaysSo(ClientGameTestContext context, ScaffoldTestSupport stage) {
		stage.set("safe_walk", SafeWalk.AT_EDGE);
		stage.stage("hotbar.2", COBBLE + " 3");
		clearActionBar(context);
		stage.walk(40);

		Component shown = actionBar(context);
		String expected = context.computeOnClient(client -> Lang.of(Keys.SCAFFOLD_EMPTY));

		if (shown == null || !expected.equals(shown.getString()) || !red(shown)) {
			throw new AssertionError("bloklar bitince kırmızı \"" + expected + "\" yazmalıydı, olan: " + describe(shown));
		}

		stage.set("safe_walk", SafeWalk.OFF);
	}

	private static void withTheCountOffNothingIsSaid(ClientGameTestContext context, ScaffoldTestSupport stage) {
		stage.set("show_count", false);
		stage.stage("hotbar.2", COBBLE + " 10");
		clearActionBar(context);
		stage.walk(24);

		Component shown = actionBar(context);

		if (shown != null) {
			throw new AssertionError("negatif kontrol başarısız: sayaç kapalıyken eylem çubuğuna bir şey yazılmamalıydı: " + describe(shown));
		}

		stage.set("show_count", true);
	}

	/**
	 * On the walkway's last block with the middle of the feet over the gap and no blocks: the gap
	 * block is boxed in green. With the outline off, nothing is.
	 */
	private static void theNextBlockIsOutlined(ClientGameTestContext context, ScaffoldTestSupport stage) {
		BlockPos gap = stage.gap(0);

		for (boolean show : new boolean[] {true, false}) {
			stage.set("show_target", show);
			stage.stage(null, null);
			stage.tp(gap.getX() + 0.5, gap.getY() + 1, gap.getZ() + 0.2);
			stage.waitTicks(3);
			Set<BlockPos> boxed = stage.outlined();

			if (show && !boxed.equals(Set.of(gap))) {
				throw new AssertionError("boşluk bloğu yeşil kutuda olmalıydı (" + gap + "), çizilen: " + boxed);
			}

			if (!show && !boxed.isEmpty()) {
				throw new AssertionError("negatif kontrol başarısız: çerçeve kapalıyken kutu çizilmemeliydi: " + boxed);
			}
		}

		stage.set("show_target", true);
	}

	private static Component actionBar(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			try {
				return (Component) overlay().get(client.gui.hud);
			} catch (IllegalAccessException e) {
				throw new AssertionError(e);
			}
		});
	}

	private static void clearActionBar(ClientGameTestContext context) {
		context.runOnClient(client -> {
			try {
				overlay().set(client.gui.hud, null);
			} catch (IllegalAccessException e) {
				throw new AssertionError(e);
			}
		});
	}

	/** The action bar's text has no getter; the field is read directly. */
	private static Field overlay() {
		try {
			Field field = Hud.class.getDeclaredField("overlayMessageString");
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("eylem çubuğu alanı bulunamadı", e);
		}
	}

	private static boolean red(Component shown) {
		return TextColor.fromLegacyFormat(ChatFormatting.RED).equals(shown.getStyle().getColor());
	}

	private static String describe(Component shown) {
		return shown == null ? "yok" : "\"" + shown.getString() + "\" (" + shown.getStyle().getColor() + ")";
	}
}
