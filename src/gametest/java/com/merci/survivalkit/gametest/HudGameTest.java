package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.hud.ActiveModules;
import com.merci.survivalkit.hud.ActiveSort;
import com.merci.survivalkit.hud.Anchor;
import com.merci.survivalkit.hud.HudBox;
import com.merci.survivalkit.hud.HudLine;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.module.impl.HudModule;
import com.merci.survivalkit.settings.Setting;
import com.mojang.blaze3d.platform.InputConstants;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;

/**
 * The HUD's list of modules that are on, read off the pixels of real screenshots: there with the
 * modules named in it, gone with the real F1 key and back with it, gone under the debug screen
 * (F3), moved by its corner setting, ordered by its order setting, and gone with the HUD off.
 * The player looks straight down at black concrete at night, so the list's white text is the
 * only white on the screen.
 */
public class HudGameTest implements FabricClientGameTest {
	/** Every channel at least this bright: the list's white text, and nothing of a black floor at night. */
	private static final int WHITE = 0xE8;
	/** A list of two names leaves far more lit pixels than this at any GUI scale. */
	private static final int SHOWN = 30;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			darkStage(context, world);

			try {
				context.runOnClient(client -> {
					module("hud").setEnabled(true);
					module("autoeat").setEnabled(true);
					module("autotool").setEnabled(true);
				});
				context.waitTicks(5);

				theListShowsTheModulesThatAreOn(context);
				f1HidesItAndBringsItBack(context);
				theDebugScreenHidesIt(context);
				theCornerSettingMovesIt(context);
				theOrderSettingOrdersIt(context);
				theShowSettingHidesIt(context);
				theSizeSettingScalesIt(context);
				withTheHudOffThereIsNothing(context);
			} catch (AssertionError e) {
				SurvivalKit.LOGGER.error("[gametest-hata] {}", e.getMessage());
				throw e;
			}

			SurvivalKit.LOGGER.info("[gametest] HUD çekirdeği doğrulandı (açık modül listesi ekranda, F1 ile kayboldu ve geri geldi, "
				+ "F3 altında yok, köşe ayarıyla yer değiştirdi, sıralama ayarı uydu, Göster kapalıyken yok, boyut 200 iki kat, HUD kapalıyken yok)");
		}
	}

	private static void theListShowsTheModulesThatAreOn(ClientGameTestContext context) {
		List<String> lines = lines(context);

		if (!lines.contains(module("autoeat").displayName()) || !lines.contains(module("autotool").displayName())
			|| lines.contains(module("hud").displayName()) || lines.size() != 2) {
			throw new AssertionError("liste yalnız açık iki modülü saymalıydı: " + lines);
		}

		HudBox box = box(context);
		int lit = white(context, "hud-liste", box);

		if (lit < SHOWN) {
			throw new AssertionError("liste ekranda görünmeliydi, kutuda beyaz piksel: " + lit);
		}

		int screenWidth = context.computeOnClient(client -> client.getWindow().getGuiScaledWidth());

		if (box.right() != screenWidth - 2 || box.y() != 2) {
			throw new AssertionError("varsayılan yer sağ üst, kenarlardan 2: " + box + ", ekran genişliği " + screenWidth);
		}

		SurvivalKit.LOGGER.info("[gametest] HUD listesi {} — {} beyaz piksel", lines, lit);
	}

	private static void f1HidesItAndBringsItBack(ClientGameTestContext context) {
		HudBox box = box(context);
		context.getInput().pressKey(InputConstants.KEY_F1);
		context.waitTicks(3);
		int hidden = white(context, "hud-f1", box);
		boolean drawn = context.computeOnClient(client -> SurvivalKit.hud().drawn(ActiveModules.ID) != null);
		context.getInput().pressKey(InputConstants.KEY_F1);
		context.waitTicks(3);
		int back = white(context, "hud-f1-geri", box);

		if (hidden != 0 || drawn) {
			throw new AssertionError("F1'de liste kaybolmalıydı, kutuda beyaz piksel: " + hidden + ", çizildi: " + drawn);
		}

		if (back < SHOWN) {
			throw new AssertionError("F1'e yeniden basınca liste geri gelmeliydi, beyaz piksel: " + back);
		}
	}

	/**
	 * The debug screen's own text fills both top corners, so the list goes above the hotbar in
	 * the middle for this, where the debug screen draws nothing.
	 */
	private static void theDebugScreenHidesIt(ClientGameTestContext context) {
		set(context, "active_modules_anchor", Anchor.BOTTOM_CENTER);
		set(context, "active_modules_y", 70);
		context.waitTicks(3);
		HudBox box = box(context);
		int before = white(context, "hud-f3-once", box);
		context.getInput().pressKey(InputConstants.KEY_F3);
		context.waitTicks(3);
		boolean debug = context.computeOnClient(client -> client.gui.hud.getDebugOverlay().showDebugScreen());
		int under = white(context, "hud-f3", box);
		context.getInput().pressKey(InputConstants.KEY_F3);
		context.waitTicks(3);

		if (!debug) {
			throw new AssertionError("hazırlık: F3 hata ayıklama ekranını açmalıydı");
		}

		if (before < SHOWN || under != 0) {
			throw new AssertionError("F3 açıkken liste kaybolmalıydı: önce " + before + ", F3'te " + under + " beyaz piksel");
		}

		reset(context);
	}

	private static void theCornerSettingMovesIt(ClientGameTestContext context) {
		HudBox right = box(context);
		set(context, "active_modules_anchor", Anchor.TOP_LEFT);
		set(context, "active_modules_x", 6);
		context.waitTicks(3);
		HudBox left = box(context);
		int atRight = white(context, "hud-sol", right);
		int atLeft = white(context, "hud-sol-2", left);

		if (left.x() != 6 || left.y() != 2) {
			throw new AssertionError("sol üstte, soldan 6 olmalıydı: " + left);
		}

		if (atRight != 0 || atLeft < SHOWN) {
			throw new AssertionError("liste sağdan sola geçmeliydi: eski yerde " + atRight + ", yeni yerde " + atLeft + " beyaz piksel");
		}

		reset(context);
	}

	private static void theOrderSettingOrdersIt(ClientGameTestContext context) {
		String eat = context.computeOnClient(client -> module("autoeat").displayName());
		String tool = context.computeOnClient(client -> module("autotool").displayName());
		set(context, "active_modules_sort", ActiveSort.NAME);
		context.waitTicks(2);
		List<String> byName = lines(context);
		set(context, "active_modules_sort", ActiveSort.MENU);
		context.waitTicks(2);
		List<String> asMenu = lines(context);

		if (!byName.equals(java.util.stream.Stream.of(eat, tool).sorted(java.text.Collator.getInstance(java.util.Locale.ROOT)).toList())
			|| !asMenu.equals(List.of(eat, tool))) {
			throw new AssertionError("sıralama: ada göre " + byName + ", menü sırası " + asMenu);
		}

		reset(context);
	}

	private static void theShowSettingHidesIt(ClientGameTestContext context) {
		HudBox box = box(context);
		set(context, "active_modules_shown", false);
		context.waitTicks(3);
		int hidden = white(context, "hud-gizli", box);
		set(context, "active_modules_shown", true);
		context.waitTicks(3);

		if (hidden != 0) {
			throw new AssertionError("\"Göster\" kapalıyken liste olmamalıydı, beyaz piksel " + hidden);
		}
	}

	/** Twice the size: twice as wide and high, still in the corner. */
	private static void theSizeSettingScalesIt(ClientGameTestContext context) {
		HudBox normal = box(context);
		set(context, "active_modules_scale", 200);
		context.waitTicks(3);
		HudBox big = box(context);
		int lit = white(context, "hud-buyuk", big);
		reset(context);

		if (Math.abs(big.width() - 2 * normal.width()) > 1 || Math.abs(big.height() - 2 * normal.height()) > 1 || lit < 2 * SHOWN) {
			throw new AssertionError("boyut 200 iki kat olmalıydı: " + normal + " -> " + big + ", beyaz piksel " + lit);
		}
	}

	private static void withTheHudOffThereIsNothing(ClientGameTestContext context) {
		HudBox box = box(context);
		context.runOnClient(client -> module("hud").setEnabled(false));
		context.waitTicks(3);
		int off = white(context, "hud-kapali", box);
		context.runOnClient(client -> module("hud").setEnabled(true));
		context.waitTicks(3);

		if (off != 0) {
			throw new AssertionError("negatif kontrol: HUD kapalıyken liste olmamalıydı, beyaz piksel " + off);
		}
	}

	/** Night, a black floor, the player looking straight down at it: nothing on the screen is white but text. */
	static void darkStage(ClientGameTestContext context, TestSingleplayerContext world) {
		context.waitTicks(5);
		BlockPos feet = context.computeOnClient(client -> client.player.blockPosition());
		world.getServer().runOnServer(server -> ((IntegratedServer) server).setWorldAllowCommands(true));
		context.waitTicks(5);
		int y = feet.getY() + 20;
		world.getServer().runCommand("time set midnight");
		world.getServer().runCommand("gamerule advance_time false");
		world.getServer().runCommand("gamemode creative @a");
		world.getServer().runCommand("fill " + (feet.getX() - 12) + " " + y + " " + (feet.getZ() - 12) + " "
			+ (feet.getX() + 12) + " " + y + " " + (feet.getZ() + 12) + " minecraft:black_concrete");
		world.getServer().runCommand("gamerule send_command_feedback false");
		world.getServer().runCommand("tp @a " + (feet.getX() + 0.5) + " " + (y + 1) + " " + (feet.getZ() + 0.5) + " 0 90");
		context.waitTicks(20);
		// The chat's white lines would sit in the middle of the screen, where the F3 check looks.
		context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
		context.waitTicks(2);
	}

	private static List<String> lines(ClientGameTestContext context) {
		return context.computeOnClient(client -> hudModule().panels().getFirst().lines().stream().map(HudLine::text).toList());
	}

	private static HudBox box(ClientGameTestContext context) {
		HudBox box = context.computeOnClient(client -> SurvivalKit.hud().drawn(ActiveModules.ID));

		if (box == null) {
			throw new AssertionError("liste çizilmedi");
		}

		return box;
	}

	/** Lit white pixels of a real screenshot inside a panel's box. */
	static int white(ClientGameTestContext context, String name, HudBox box) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		Path shot = context.takeScreenshot(name);
		BufferedImage image;

		try {
			image = ImageIO.read(shot.toFile());
		} catch (IOException unreadable) {
			throw new AssertionError("ekran görüntüsü okunamadı: " + shot, unreadable);
		}

		int lit = 0;

		for (int y = (int) (box.y() * scale); y < Math.min(image.getHeight(), (int) Math.ceil(box.bottom() * scale)); y++) {
			for (int x = (int) (box.x() * scale); x < Math.min(image.getWidth(), (int) Math.ceil(box.right() * scale)); x++) {
				int rgb = image.getRGB(x, y);

				if (((rgb >> 16) & 0xFF) >= WHITE && ((rgb >> 8) & 0xFF) >= WHITE && (rgb & 0xFF) >= WHITE) {
					lit++;
				}
			}
		}

		return lit;
	}

	@SuppressWarnings("unchecked")
	private static <T> void set(ClientGameTestContext context, String id, T value) {
		context.runOnClient(client -> hudModule().settings().<Setting<T>>byId(id)
			.orElseThrow(() -> new AssertionError("ayar yok: " + id)).set(value));
	}

	private static void reset(ClientGameTestContext context) {
		context.runOnClient(client -> hudModule().panels().getFirst().placement().reset());
		context.waitTicks(3);
	}

	private static HudModule hudModule() {
		return (HudModule) module("hud");
	}

	private static Module module(String id) {
		return SurvivalKit.modules().byId(id).orElseThrow(() -> new AssertionError("modül yok: " + id));
	}
}
