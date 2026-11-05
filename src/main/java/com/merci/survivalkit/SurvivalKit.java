package com.merci.survivalkit;

import com.merci.survivalkit.client.JoinProfileSwitch;
import com.merci.survivalkit.client.LegacyGlfwKeys;
import com.merci.survivalkit.client.MenuKey;
import com.merci.survivalkit.client.ModuleKeys;
import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.hud.ClientHud;
import com.merci.survivalkit.hud.HudEditorScreen;
import com.merci.survivalkit.module.impl.HudModule;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import com.merci.survivalkit.menu.MenuModel;
import com.merci.survivalkit.menu.SettingsAutoSave;
import com.merci.survivalkit.module.ModuleManager;
import com.merci.survivalkit.profile.ProfileStore;
import com.merci.survivalkit.preset.PresetStore;
import com.merci.survivalkit.preset.Presets;
import com.merci.survivalkit.profile.Profiles;
import com.merci.survivalkit.module.impl.AutoEatModule;
import com.merci.survivalkit.module.impl.AutoTotemModule;
import com.merci.survivalkit.module.impl.AutoToolModule;
import com.merci.survivalkit.module.impl.AutoWeaponModule;
import com.merci.survivalkit.module.impl.ScaffoldModule;
import com.merci.survivalkit.module.impl.BuildModeModule;
import com.merci.survivalkit.module.impl.ToolPickerModule;
import com.merci.survivalkit.module.impl.VeinMinerModule;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.text.Lang;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SurvivalKit implements ClientModInitializer {
	public static final String MOD_ID = "survivalkit";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final ModuleManager MODULES = new ModuleManager();
	private static final ModuleKeys KEYS = new ModuleKeys();
	/**
	 * One lock for every module that moves what the player is holding. Shared, because the
	 * whole point of it is that two modules see each other.
	 */
	private static final Hands HANDS = new Hands();
	private static BuildModeModule BUILD_MODE;
	private static ClientHud HUD;

	private static Profiles PROFILES;
	private static Presets PRESETS;
	private static MenuKey MENU;
	private static final SettingsAutoSave AUTO_SAVE = new SettingsAutoSave();

	public static ModuleManager modules() {
		return MODULES;
	}

	/** What the HUD drew last frame, and where. */
	public static ClientHud hud() {
		return HUD;
	}

	public static Hands hands() {
		return HANDS;
	}

	/** A module changed one of its own settings, the way the menu would: written a second later. */
	public static void settingsChanged() {
		AUTO_SAVE.touch();
	}

	/**
	 * Whether another module has claimed this item and the hand-moving modules are to leave it
	 * where it is: Yapı Modu's wand, while Yapı Modu is on.
	 */
	public static boolean reserved(ItemStack stack) {
		return BUILD_MODE != null && BUILD_MODE.isWand(stack);
	}

	/** Yapı Modu's areas, per server and dimension: beside the profiles, not in one. */
	public static Path areasFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("areas.json");
	}

	@Override
	public void onInitializeClient() {
		// Everything the menu says is a translation key until this is installed, so it goes
		// in before the first module is built. The raw line is wanted, not a formatted one:
		// a line like "%s ile çakışıyor" is filled in by Lang itself, with what it has.
		Lang.use(key -> Language.getInstance().getOrDefault(key));

		AutoEatModule autoEat = new AutoEatModule();
		MODULES.register(autoEat);
		KEYS.bind(autoEat);

		AutoTotemModule autoTotem = new AutoTotemModule();
		MODULES.register(autoTotem);
		KEYS.bind(autoTotem);

		AutoToolModule autoTool = new AutoToolModule();
		MODULES.register(autoTool);
		KEYS.bind(autoTool);

		ToolPickerModule toolPicker = new ToolPickerModule(autoTool);
		MODULES.register(toolPicker);
		KEYS.bind(toolPicker);

		AutoWeaponModule autoWeapon = new AutoWeaponModule();
		MODULES.register(autoWeapon);
		KEYS.bind(autoWeapon);

		BuildModeModule buildMode = new BuildModeModule();
		MODULES.register(buildMode);
		KEYS.bind(buildMode);
		BUILD_MODE = buildMode;

		VeinMinerModule veinMiner = new VeinMinerModule(buildMode::area);
		MODULES.register(veinMiner);
		KEYS.bind(veinMiner);

		ScaffoldModule scaffold = new ScaffoldModule(buildMode::area);
		MODULES.register(scaffold);
		KEYS.bind(scaffold);

		WorldDownloaderModule downloader = new WorldDownloaderModule();
		MODULES.register(downloader);
		KEYS.bind(downloader);

		HudModule hud = new HudModule(MODULES::modules, List.of(scaffold, autoTotem, downloader, veinMiner, autoWeapon));
		MODULES.register(hud);
		KEYS.bind(hud);
		HUD = new ClientHud(hud);
		// Under the chat, over everything else of the game's own: a list in a corner must not
		// hide what is said to the player.
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("hud"), HUD::render);

		// Built once the modules are known, and kept for the life of the game so the menu
		// reopens on whatever was last being looked at.
		MenuModel menuModel = new MenuModel(MODULES.modules());
		MENU = new MenuKey(menuModel, AUTO_SAVE::touch,
			parent -> new HudEditorScreen(hud, parent, AUTO_SAVE::touch), SurvivalKit::profiles, SurvivalKit::presets);

		// Older configs stored raw GLFW codes; teach the setting how to name one before loading.
		KeybindSetting.useLegacyKeyNames(LegacyGlfwKeys::name);

		Path config = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
		PROFILES = new Profiles(new ProfileStore(config.resolve("profiles"), config.resolve("profiles.json")), MODULES, legacySettingsFile());
		PROFILES.load();
		// A profile chosen by the world just joined: what the menu's ↺ remembered belongs to the one left.
		JoinProfileSwitch.register(SurvivalKit::profiles, () -> menuModel.presetUndo().clear());
		PRESETS = new Presets(new PresetStore(config.resolve("presets.json")));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> PROFILES.save());

		// Keys are read here and nowhere else, so an open screen never toggles a module.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean screenOpen = client.gui.screen() != null;

			MENU.poll(client);

			// A menu change is written about a second after the last one, so dragging a
			// slider costs one write rather than twenty.
			if (AUTO_SAVE.tick()) {
				PROFILES.save();
			}
			KEYS.poll(screenOpen);
			MODULES.tick(screenOpen, KEYS);
			HUD.tickEnd();
		});

		LOGGER.info("SurvivalKit yüklendi ({} modül)", MODULES.modules().size());
	}

	/** Where the profile in use is written. */
	public static Path settingsFile() {
		return PROFILES.file();
	}

	/** Where settings lived before profiles; read once, never written. */
	private static Path legacySettingsFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
	}

	/** Throws away the settings in memory and takes what is on disk for the profile in use. */
	public static void reloadSettings() {
		if (PROFILES != null) {
			PROFILES.reload();
		}
	}

	/** Every module's presets: the built-in ones and the player's own. */
	public static Presets presets() {
		return PRESETS;
	}

	/** The profiles, and the switch between them. */
	public static Profiles profiles() {
		return PROFILES;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
