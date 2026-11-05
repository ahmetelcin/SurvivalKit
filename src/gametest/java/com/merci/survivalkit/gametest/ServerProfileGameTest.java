package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.client.JoinProfileSwitch;
import com.merci.survivalkit.menu.ProfileBar;
import com.merci.survivalkit.menu.ServerRulesScreen;
import com.merci.survivalkit.menu.SurvivalKitScreen;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.profile.ProfileStore;
import com.merci.survivalkit.profile.Profiles;
import com.merci.survivalkit.profile.ServerRules;
import com.mojang.blaze3d.platform.InputConstants;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.renderer.Rect2i;

/**
 * The profile chosen by the world joined. The rules are set up on their own screen with real
 * clicks and typing — reached from the profile list's last line: singleplayer to "tek", then two
 * address rules, "*.ornek.net" to "tek" and "local*" to the built-in "safe", and a third added and
 * deleted with its ✕. Then a new singleplayer world is joined for real: "tek" is in use, its module
 * is on, and a toast says so on screen. Then a real dedicated server, at localhost: the first rule
 * does not match, the second does — "safe", every module off, and the toast again.
 */
public class ServerProfileGameTest implements FabricClientGameTest {
	private static final InputConstants.Key MENU_KEY = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT);
	private static final String TEK = "tek";

	@Override
	public void runTest(ClientGameTestContext context) {
		try {
			try (TestSingleplayerContext world = context.worldBuilder().create()) {
				context.waitTicks(10);
				prepare(context);
				theRulesAreSetOnTheirOwnScreen(context);
			}

			singleplayerChoosesItsProfile(context);
			aServerChoosesByItsAddress(context);
		} catch (AssertionError e) {
			SurvivalKit.LOGGER.error("[gametest-hata] {}", e.getMessage());
			throw e;
		} finally {
			context.runOnClient(client -> {
				Profiles profiles = SurvivalKit.profiles();
				profiles.setRules(new ServerRules());
				profiles.switchTo(ProfileStore.DEFAULT);
				profiles.store().delete(TEK);
			});
		}

		SurvivalKit.LOGGER.info("[gametest] sunucuya göre profil doğrulandı (kurallar ekranından tıklayarak kuruldu; tek oyunculu dünyada \"tek\", "
			+ "localhost sunucusunda \"local*\" kuralıyla Güvenli — her modül kapalı; ikisinde de toast ekranda)");
	}

	/** "tek", with Otomatik Yeme on in it, so a switch to it and away from it can be seen. */
	private static void prepare(ClientGameTestContext context) {
		context.runOnClient(client -> {
			Profiles profiles = SurvivalKit.profiles();
			profiles.create(TEK);
			module("autoeat").setEnabled(true);
			profiles.switchTo(ProfileStore.DEFAULT);
		});
		context.waitTicks(2);
	}

	private static void theRulesAreSetOnTheirOwnScreen(ClientGameTestContext context) {
		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);
		click(context, context.computeOnClient(client -> menu(client).profileBar().part(ProfileBar.Part.PROFILE)));
		click(context, context.computeOnClient(client -> menu(client).profileBar().rulesRow(client.font).orElseThrow()));

		if (!(context.computeOnClient(client -> client.gui.screen()) instanceof ServerRulesScreen)) {
			throw new AssertionError("listenin son satırı kurallar ekranını açmalıydı");
		}

		context.takeScreenshot("sunucu-kurallari-bos");
		clickUntil(context, screen -> screen.singleplayerButton(), rules -> TEK.equals(rules.singleplayer()), "tek oyunculu → tek");

		addRule(context, "*.ornek.net");
		clickUntil(context, screen -> screen.ruleProfile(0), rules -> rules.rules().size() == 1 && rules.rules().get(0).profile().equals(TEK), "kural 1 → tek");
		addRule(context, "local*");
		clickUntil(context, screen -> screen.ruleProfile(1), rules -> rules.rules().size() == 2 && rules.rules().get(1).profile().equals(ProfileStore.SAFE), "kural 2 → safe");
		addRule(context, "silinecek.net");

		if (context.computeOnClient(client -> SurvivalKit.profiles().rules().rules().size()) != 3) {
			throw new AssertionError("üçüncü kural yazılmalıydı: " + rulesNow(context));
		}

		context.takeScreenshot("sunucu-kurallari");
		click(context, context.computeOnClient(client -> rulesScreen(client).ruleDelete(2)));
		click(context, context.computeOnClient(client -> rulesScreen(client).doneButton()));

		if (!(context.computeOnClient(client -> client.gui.screen()) instanceof SurvivalKitScreen)) {
			throw new AssertionError("Bitti menüye dönmeliydi");
		}

		List<ServerRules.Rule> wanted = List.of(new ServerRules.Rule("*.ornek.net", TEK), new ServerRules.Rule("local*", ProfileStore.SAFE));
		ServerRules onDisk = context.computeOnClient(client -> SurvivalKit.profiles().rules());

		if (!onDisk.rules().equals(wanted) || !TEK.equals(onDisk.singleplayer()) || onDisk.fallback() != null) {
			throw new AssertionError("diskteki kurallar " + wanted + ", tek oyunculu tek, eşleşmezse kal olmalıydı: " + rulesNow(context));
		}

		context.runOnClient(client -> client.gui.setScreen(null));
		context.waitTicks(2);
	}

	private static void singleplayerChoosesItsProfile(ClientGameTestContext context) {
		expectActive(context, ProfileStore.DEFAULT);

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(2);
			expectActive(context, TEK);

			if (!context.computeOnClient(client -> module("autoeat").isEnabled())) {
				throw new AssertionError("\"tek\"te açık olan Otomatik Yeme açılmalıydı");
			}

			int yellow = toast(context, "sunucu-profil-tek");
			SurvivalKit.LOGGER.info("[gametest] tek oyunculu: profil tek, toast {} sarı piksel", yellow);
		}
	}

	private static void aServerChoosesByItsAddress(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(freePort());
			TestDedicatedServerConnection connection = server.connect()) {
			context.waitTicks(2);
			String address = context.computeOnClient(client -> client.getCurrentServer().ip);
			expectActive(context, ProfileStore.SAFE);

			List<String> on = context.computeOnClient(client -> SurvivalKit.modules().modules().stream()
				.filter(Module::isEnabled).map(Module::id).toList());

			if (!on.isEmpty()) {
				throw new AssertionError("Güvenli'de her modül kapalı olmalıydı, açık: " + on);
			}

			int yellow = toast(context, "sunucu-profil-guvenli");
			SurvivalKit.LOGGER.info("[gametest] sunucu {}: profil safe, açık modül yok, toast {} sarı piksel", address, yellow);
		}
	}

	/** A port nothing else is on, so a server the player has running on 25565 is no matter. */
	private static Properties freePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			Properties properties = new Properties();
			properties.setProperty("server-port", String.valueOf(socket.getLocalPort()));
			return properties;
		} catch (IOException e) {
			throw new AssertionError("boş port bulunamadı", e);
		}
	}

	// --- the rules screen, for real ---

	private static void addRule(ClientGameTestContext context, String address) {
		click(context, context.computeOnClient(client -> rulesScreen(client).addButton()));
		context.getInput().typeChars(address);
		context.waitTicks(2);
	}

	/** Clicks a profile button until the rules on disk say what is wanted: the buttons step one profile a click. */
	private static void clickUntil(ClientGameTestContext context, java.util.function.Function<ServerRulesScreen, Rect2i> button,
			Predicate<ServerRules> wanted, String what) {
		for (int attempt = 0; attempt < 8; attempt++) {
			if (context.computeOnClient(client -> wanted.test(SurvivalKit.profiles().rules()))) {
				return;
			}

			click(context, context.computeOnClient(client -> button.apply(rulesScreen(client))));
		}

		throw new AssertionError("düğmeyle kurulamadı: " + what + " — " + rulesNow(context));
	}

	private static String rulesNow(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			ServerRules rules = SurvivalKit.profiles().rules();
			return rules.rules() + ", tek oyunculu " + rules.singleplayer() + ", eşleşmezse " + rules.fallback();
		});
	}

	// --- what is on screen ---

	/**
	 * The toast is ours and on screen. The game puts toasts of its own up on joining a server, with
	 * yellow titles too, so ours is told apart by taking it away: once it has slid in, a real
	 * screenshot; then ours alone hidden, and another. The yellow that went is ours — yellow, so
	 * neither sky nor cloud is counted.
	 */
	private static int toast(ClientGameTestContext context, String name) {
		boolean held = context.computeOnClient(client -> client.gui.toastManager().getToast(SystemToast.class, JoinProfileSwitch.TOAST) != null);
		context.waitTicks(20);
		int shown = yellowTopRight(context, name);
		context.runOnClient(client -> SystemToast.forceHide(client.gui.toastManager(), JoinProfileSwitch.TOAST));
		context.waitTicks(30);
		int hidden = yellowTopRight(context, name + "-gizli");

		if (!held || shown - hidden < 20) {
			throw new AssertionError("profil değişince toast ekranda olmalıydı: yöneticide " + held + ", sarı piksel " + shown + " -> gizleyince " + hidden);
		}

		return shown - hidden;
	}

	/** Yellow pixels of a real screenshot in the top right, where toasts stack. */
	private static int yellowTopRight(ClientGameTestContext context, String name) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		int guiWidth = context.computeOnClient(client -> client.getWindow().getGuiScaledWidth());
		Path shot = context.takeScreenshot(name);
		BufferedImage image;

		try {
			image = ImageIO.read(shot.toFile());
		} catch (IOException unreadable) {
			throw new AssertionError("ekran görüntüsü okunamadı: " + shot, unreadable);
		}

		int yellow = 0;

		for (int y = 0; y < Math.min(image.getHeight(), (int) (160 * scale)); y++) {
			for (int x = Math.max(0, (int) ((guiWidth - 200) * scale)); x < image.getWidth(); x++) {
				int rgb = image.getRGB(x, y);

				if (((rgb >> 16) & 0xFF) >= 200 && ((rgb >> 8) & 0xFF) >= 200 && (rgb & 0xFF) < 80) {
					yellow++;
				}
			}
		}

		return yellow;
	}

	private static void expectActive(ClientGameTestContext context, String profile) {
		String active = context.computeOnClient(client -> SurvivalKit.profiles().active());

		if (!profile.equals(active)) {
			throw new AssertionError("kullanılan profil " + profile + " olmalıydı: " + active);
		}
	}

	// --- plumbing ---

	private static void click(ClientGameTestContext context, Rect2i box) {
		if (box == null) {
			throw new AssertionError("tıklanacak yer yok");
		}

		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos((box.getX() + box.getWidth() / 2.0) * scale, (box.getY() + box.getHeight() / 2.0) * scale);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static SurvivalKitScreen menu(net.minecraft.client.Minecraft client) {
		if (!(client.gui.screen() instanceof SurvivalKitScreen open)) {
			throw new AssertionError("menü açık değil, açık olan: " + client.gui.screen());
		}

		return open;
	}

	private static ServerRulesScreen rulesScreen(net.minecraft.client.Minecraft client) {
		if (!(client.gui.screen() instanceof ServerRulesScreen open)) {
			throw new AssertionError("kurallar ekranı açık değil, açık olan: " + client.gui.screen());
		}

		return open;
	}

	private static Module module(String id) {
		return SurvivalKit.modules().byId(id).orElseThrow(() -> new AssertionError("modül yok: " + id));
	}
}
