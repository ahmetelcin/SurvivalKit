package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.settings.RegistryListSetting;
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.settings.SettingsStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Proves the settings file is where we say it is, that the running game will take a profile
 * straight off disk, and that an id this build has never heard of comes back unharmed even
 * with every registry loaded.
 */
public class SettingsGameTest implements FabricClientGameTest {
	private static final Path EXPECTED_TAIL = Path.of("survivalkit", "profiles", "default.json");
	/** No mod in this run provides it, so any registry lookup would lose it. */
	private static final String UNKNOWN_ID = "bazımod:efsanevi_meyve";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitTicks(5);

		Path file = SurvivalKit.settingsFile();

		if (!file.endsWith(EXPECTED_TAIL)) {
			throw new AssertionError("ayar dosyası " + EXPECTED_TAIL + " ile bitmeliydi, bulunan " + file);
		}

		profileIsTakenFromDisk(context, file);
		unknownIdSurvivesTheGamesOwnStorage(file.getParent());
		aProfileThatSwitchesAModuleOffLetsItCleanUp(context, file);
		restore(context, file);

		SurvivalKit.LOGGER.info("[gametest] ayarlar doğrulandı ({} diskten okundu, {} korundu)", file, UNKNOWN_ID);
	}

	/** Writes a profile by hand, then makes the running client adopt it. */
	private static void profileIsTakenFromDisk(ClientGameTestContext context, Path file) {
		// Something in memory that the file will not mention, so we can watch it be dropped.
		context.runOnClient(client -> goldenApple().set(true));

		int wanted = threshold(context).get() == 12 ? 13 : 12;
		write(file, "{\"autoeat\": {\"threshold\": " + wanted + ", \"key\": \"key.keyboard.j\"}}");

		context.runOnClient(client -> SurvivalKit.reloadSettings());

		int loaded = context.computeOnClient(client -> threshold(client).get());

		if (loaded != wanted) {
			throw new AssertionError("eşik diskten gelmeliydi, beklenen " + wanted + ", bulunan " + loaded);
		}

		String key = context.computeOnClient(client -> autoEatKey().get());

		if (!"key.keyboard.j".equals(key)) {
			throw new AssertionError("tuş diskten gelmeliydi, bulunan " + key);
		}

		boolean apple = context.computeOnClient(client -> goldenApple().get());

		if (apple) {
			throw new AssertionError("dosyada geçmeyen ayar varsayılana dönmeliydi");
		}
	}

	/**
	 * A full save and load through {@link SettingsStorage}, inside the running game where
	 * every registry is populated, on a list holding an id that is in none of them.
	 */
	private static void unknownIdSurvivesTheGamesOwnStorage(Path directory) {
		Path file = directory.resolve("bilinmeyen-id-testi.json");
		SettingGroup saved = listGroup();
		RegistryListSetting savedList = saved.<RegistryListSetting>byId("foods").orElseThrow();
		savedList.add("minecraft:bread");
		savedList.add(UNKNOWN_ID);
		savedList.add("minecraft:apple");

		new SettingsStorage(file).save(saved);

		SettingGroup loaded = listGroup();
		new SettingsStorage(file).load(loaded);
		List<String> ids = loaded.<RegistryListSetting>byId("foods").orElseThrow().get();

		if (!List.of("minecraft:bread", UNKNOWN_ID, "minecraft:apple").equals(ids)) {
			throw new AssertionError("bilinmeyen id sırasıyla korunmalıydı, bulunan " + ids);
		}

		String written = read(file);

		if (!written.contains(UNKNOWN_ID)) {
			throw new AssertionError("bilinmeyen id dosyaya metin olarak yazılmalıydı: " + written);
		}

		delete(file);
	}

	/**
	 * A profile can switch a module off while it is in the middle of something. The module has
	 * to be told, or AutoEat leaves the use key held down forever.
	 */
	private static void aProfileThatSwitchesAModuleOffLetsItCleanUp(ClientGameTestContext context, Path file) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(5);

			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("clear @a");
			world.getServer().runCommand("give @a minecraft:bread 64");
			world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
					.getFoodData().setFoodLevel(10));
			context.waitTicks(5);

			// Nothing edible in hand, so a slot swap has to happen and then be undone.
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(8));
			context.runOnClient(client -> autoEat().setEnabled(true));
			context.waitTicks(10);

			if (!context.computeOnClient(client -> client.options.keyUse.isDown())) {
				throw new AssertionError("hazırlık başarısız: modül yemeye başlamalıydı");
			}

			write(file, "{\"autoeat\": {\"enabled\": false}}");
			context.runOnClient(client -> SurvivalKit.reloadSettings());
			context.waitTicks(3);

			if (context.computeOnClient(client -> autoEat().isEnabled())) {
				throw new AssertionError("profil modülü kapatmalıydı");
			}

			if (context.computeOnClient(client -> client.options.keyUse.isDown())) {
				throw new AssertionError("profil değişince kullanma tuşu bırakılmalıydı");
			}

			int slot = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());

			if (slot != 8) {
				throw new AssertionError("eski slota dönülmeliydi, bulunan " + slot);
			}
		}
	}

	/** Leaves the profile as the run started, so the other tests see what they expect. */
	private static void restore(ClientGameTestContext context, Path file) {
		delete(file);
		context.runOnClient(client -> SurvivalKit.reloadSettings());
	}

	private static SettingGroup listGroup() {
		SettingGroup group = new SettingGroup("autoeat", "test.autoeat");
		group.add(new RegistryListSetting("foods", "test.foods", List.of()));
		return group;
	}

	private static IntSetting threshold(ClientGameTestContext context) {
		return context.computeOnClient(SettingsGameTest::threshold);
	}

	private static IntSetting threshold(net.minecraft.client.Minecraft client) {
		return autoEatSettings().<IntSetting>byId("threshold").orElseThrow();
	}

	private static BoolSetting goldenApple() {
		return autoEatSettings().<BoolSetting>byId("golden_apple").orElseThrow();
	}

	private static KeybindSetting autoEatKey() {
		return autoEatSettings().<KeybindSetting>byId("key").orElseThrow();
	}

	private static Module autoEat() {
		return SurvivalKit.modules()
				.byId("autoeat")
				.orElseThrow(() -> new AssertionError("autoeat modülü kayıtlı değil"));
	}

	private static SettingGroup autoEatSettings() {
		return autoEat().settings();
	}

	private static void write(Path file, String json) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, json);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void delete(Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
