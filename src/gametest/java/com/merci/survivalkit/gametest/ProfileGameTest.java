package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.menu.ProfileBar;
import com.merci.survivalkit.menu.SurvivalKitScreen;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import com.merci.survivalkit.profile.ProfileStore;
import com.merci.survivalkit.profile.Profiles;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.merci.survivalkit.wdl.DownloadSession;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.renderer.Rect2i;

/**
 * Profiles from the menu's bar, with real clicks and real typing: a new one made and switched to;
 * a name already taken refused; in it, Dünya İndirici and Otomatik Yeme switched on with their
 * toggles and a number changed; back to "default" from the list — the download closed through
 * its own switch-off, its world on disk, the number back — and over again, all on as it was;
 * copied, renamed, and deleted after a "no" and a "yes". "default" cannot be deleted.
 */
public class ProfileGameTest implements FabricClientGameTest {
	private static final InputConstants.Key MENU_KEY = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT);
	private static final String FIRST = "deneme";
	private static final String COPY = "kopya";
	private static final String RENAMED = "ikinci";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(10);
			openMenu(context);

			try {
				aNewProfileIsMadeAndSwitchedTo(context);
				aTakenNameIsRefused(context);
				switchingChangesTheSettingsAndClosesWhatWasOn(context);
				copyRenameDelete(context);
				defaultCannotBeDeleted(context);
			} catch (AssertionError e) {
				SurvivalKit.LOGGER.error("[gametest-hata] {}", e.getMessage());
				throw e;
			} finally {
				cleanUp(context);
			}

			SurvivalKit.LOGGER.info("[gametest] profiller doğrulandı (çubuktan yeni profil, alınmış ad reddedildi, listeden geçişte ayarlar değişti ve "
				+ "açık Dünya İndirici kendi kapatmasıyla kapandı, geri dönünce açıldı; kopyala, yeniden adlandır, sil (önce vazgeç); default silinmez)");
		}
	}

	private static void aNewProfileIsMadeAndSwitchedTo(ClientGameTestContext context) {
		expectActive(context, ProfileStore.DEFAULT);
		click(context, ProfileBar.Part.NEW);
		type(context, FIRST);
		context.takeScreenshot("profil-ad");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(3);
		expectActive(context, FIRST);

		if (!Files.isRegularFile(file(context, FIRST))) {
			throw new AssertionError("yeni profilin dosyası olmalıydı: " + file(context, FIRST));
		}
	}

	private static void aTakenNameIsRefused(ClientGameTestContext context) {
		click(context, ProfileBar.Part.NEW);
		type(context, "DEFAULT");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(3);
		String error = context.computeOnClient(client -> bar(context).error());
		String taken = context.computeOnClient(client -> Lang.of(Keys.MENU_PROFILE_TAKEN));

		if (!taken.equals(error) || bar(context).mode() != ProfileBar.Mode.NEW) {
			throw new AssertionError("alınmış ad reddedilip söylenmeliydi: \"" + error + "\", kip " + bar(context).mode());
		}

		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(3);
		expectActive(context, FIRST);

		if (bar(context).mode() != ProfileBar.Mode.IDLE) {
			throw new AssertionError("Escape ad kutusunu kapatmalıydı, kip " + bar(context).mode());
		}
	}

	/**
	 * In the new profile two modules are switched on with their real toggles and a number set; the
	 * list takes it back to "default", where all of it is as "default" had it — the download
	 * closed by its own switch-off, its world written — and back again, it is all there.
	 */
	private static void switchingChangesTheSettingsAndClosesWhatWasOn(ClientGameTestContext context) {
		toggle(context, "wdl");
		toggle(context, "autoeat");
		context.runOnClient(client -> threshold().set(5));
		context.waitTicks(5);

		if (!context.computeOnClient(client -> downloader().session().isPresent() && module("autoeat").isEnabled())) {
			throw new AssertionError("hazırlık: düğmelerle Dünya İndirici ve Otomatik Yeme açılmalıydı");
		}

		Path savedTo = context.computeOnClient(client -> downloader().lastSavePath().orElseThrow());
		pick(context, ProfileStore.DEFAULT);
		expectActive(context, ProfileStore.DEFAULT);
		context.waitTicks(3);

		boolean closed = context.computeOnClient(client -> downloader().session().isEmpty() && !module("wdl").isEnabled());
		boolean written = Files.isRegularFile(savedTo.resolve("level.dat"));
		int number = context.computeOnClient(client -> threshold().get());

		if (!closed || !written || module("autoeat").isEnabled() || number != threshold().defaultValue()) {
			throw new AssertionError("default'a geçince: indirme kapanmalı (" + closed + "), dünyası diske yazılmalı (" + written
				+ "), Otomatik Yeme kapalı (" + !module("autoeat").isEnabled() + "), eşik varsayılan olmalıydı (" + number + ")");
		}

		pick(context, FIRST);
		context.waitTicks(3);
		boolean back = context.computeOnClient(client -> downloader().session().isPresent() && module("autoeat").isEnabled()
			&& threshold().get() == 5);

		if (!back) {
			throw new AssertionError(FIRST + "'ya dönünce Dünya İndirici ve Otomatik Yeme açık, eşik 5 olmalıydı");
		}

		SurvivalKit.LOGGER.info("[gametest] profil geçişi: indirme kapandı ve {} yazıldı, geri dönünce açıldı", savedTo.resolve("level.dat"));
	}

	/**
	 * The copy has the download on as well, so the switch to it goes from on to on: the download
	 * is still closed and opened again — a new one — rather than carried over.
	 */
	private static void copyRenameDelete(ClientGameTestContext context) {
		DownloadSession before = context.computeOnClient(client -> downloader().session().orElseThrow());
		click(context, ProfileBar.Part.COPY);
		clearName(context);
		type(context, COPY);
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(3);
		expectActive(context, COPY);

		if (context.computeOnClient(client -> threshold().get()) != 5) {
			throw new AssertionError("kopya, kopyalananın eşiğini (5) taşımalıydı");
		}

		boolean anew = context.computeOnClient(client -> downloader().session().filter(session -> session != before).isPresent());

		if (!anew) {
			throw new AssertionError("iki profilde de açık olan indirme geçişte kapanıp yeniden açılmalıydı (aynı oturum sürüyor ya da hiç yok)");
		}

		click(context, ProfileBar.Part.RENAME);
		clearName(context);
		type(context, RENAMED);
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(3);
		expectActive(context, RENAMED);

		if (Files.exists(file(context, COPY)) || !Files.isRegularFile(file(context, RENAMED))) {
			throw new AssertionError("yeniden adlandırınca dosya taşınmalıydı");
		}

		click(context, ProfileBar.Part.DELETE);

		if (bar(context).mode() != ProfileBar.Mode.ASKING) {
			throw new AssertionError("silmeden önce sorulmalıydı, kip " + bar(context).mode());
		}

		click(context, ProfileBar.Part.CANCEL);

		if (!Files.isRegularFile(file(context, RENAMED))) {
			throw new AssertionError("vazgeçince profil kalmalıydı");
		}

		expectActive(context, RENAMED);
		click(context, ProfileBar.Part.DELETE);
		click(context, ProfileBar.Part.YES);
		expectActive(context, ProfileStore.DEFAULT);

		if (Files.exists(file(context, RENAMED))) {
			throw new AssertionError("evet deyince profil silinmeliydi");
		}
	}

	private static void defaultCannotBeDeleted(ClientGameTestContext context) {
		click(context, ProfileBar.Part.DELETE);

		if (bar(context).mode() != ProfileBar.Mode.IDLE) {
			throw new AssertionError("default'ta Sil bir şey yapmamalıydı, kip " + bar(context).mode());
		}

		expectActive(context, ProfileStore.DEFAULT);
	}

	/** Back to "default", the test's own profile gone, the menu closed: as the next test expects. */
	private static void cleanUp(ClientGameTestContext context) {
		context.runOnClient(client -> {
			Profiles profiles = SurvivalKit.profiles();
			profiles.switchTo(ProfileStore.DEFAULT);

			for (String name : List.of(FIRST, COPY, RENAMED)) {
				profiles.store().delete(name);
			}

			client.gui.setScreen(null);
		});
		context.waitTicks(3);
	}

	// --- the bar, for real ---

	private static void openMenu(ClientGameTestContext context) {
		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);
		screen(context);
	}

	private static void click(ClientGameTestContext context, ProfileBar.Part part) {
		Rect2i box = context.computeOnClient(client -> bar(context).part(part));

		if (box == null) {
			throw new AssertionError("çubukta şu an yok: " + part + " (kip " + bar(context).mode() + ")");
		}

		clickAt(context, box.getX() + box.getWidth() / 2, box.getY() + box.getHeight() / 2);
	}

	/** Opens the list with a click and clicks the profile's row. */
	private static void pick(ClientGameTestContext context, String profile) {
		click(context, ProfileBar.Part.PROFILE);
		context.takeScreenshot("profil-listesi-" + profile);
		Rect2i row = context.computeOnClient(client -> bar(context).listRow(client.font, profile))
			.orElseThrow(() -> new AssertionError("listede yok: " + profile));
		clickAt(context, row.getX() + row.getWidth() / 2, row.getY() + row.getHeight() / 2);
	}

	/** A module's own toggle in the menu's list, clicked. */
	private static void toggle(ClientGameTestContext context, String id) {
		SurvivalKitScreen menu = screen(context);
		int y = context.computeOnClient(client -> menu.rowCenterY(module(id)))
			.orElseThrow(() -> new AssertionError("modül listede yok: " + id));
		clickAt(context, context.computeOnClient(client -> menu.toggleCenterX()), y);
	}

	private static void type(ClientGameTestContext context, String text) {
		context.getInput().typeChars(text);
		context.waitTicks(2);
	}

	private static void clearName(ClientGameTestContext context) {
		for (int i = 0; i < 40; i++) {
			context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
		}

		context.waitTicks(2);
	}

	private static void clickAt(ClientGameTestContext context, int guiX, int guiY) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos(guiX * scale, guiY * scale);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static void expectActive(ClientGameTestContext context, String profile) {
		String active = context.computeOnClient(client -> SurvivalKit.profiles().active());

		if (!profile.equals(active)) {
			throw new AssertionError("kullanılan profil " + profile + " olmalıydı: " + active);
		}

		Rect2i button = context.computeOnClient(client -> bar(context).part(ProfileBar.Part.PROFILE));

		if (button == null) {
			throw new AssertionError("çubukta profil düğmesi görünmeliydi");
		}
	}

	private static SurvivalKitScreen screen(ClientGameTestContext context) {
		SurvivalKitScreen menu = context.computeOnClient(client -> client.gui.screen() instanceof SurvivalKitScreen open ? open : null);

		if (menu == null) {
			throw new AssertionError("menü açık olmalıydı");
		}

		return menu;
	}

	private static ProfileBar bar(ClientGameTestContext context) {
		return screen(context).profileBar();
	}

	private static Path file(ClientGameTestContext context, String profile) {
		return context.computeOnClient(client -> SurvivalKit.profiles().store().file(profile));
	}

	private static WorldDownloaderModule downloader() {
		return (WorldDownloaderModule) module("wdl");
	}

	private static IntSetting threshold() {
		return module("autoeat").settings().<IntSetting>byId("threshold").orElseThrow();
	}

	private static Module module(String id) {
		return SurvivalKit.modules().byId(id).orElseThrow(() -> new AssertionError("modül yok: " + id));
	}
}
