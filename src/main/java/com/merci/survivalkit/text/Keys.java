package com.merci.survivalkit.text;

import com.merci.survivalkit.module.Category;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.settings.Setting;
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.settings.SettingNode;
import com.merci.survivalkit.settings.TextSetting;
import com.merci.survivalkit.preset.BuiltInPresets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * How a translation key is spelled, and which ones a build needs.
 *
 * <p>Keys are built here rather than written out at each setting, so a module cannot invent
 * its own shape and quietly fall outside what the language test checks. {@link #needed} is
 * that test's other half: it walks the real modules and says exactly which keys both language
 * files have to carry, which is why adding a setting fails the build until it is translated.
 */
public final class Keys {
	private static final String ROOT = "survivalkit.";

	/** The left-hand panel's heading. */
	public static final String MENU_MODULES = ROOT + "menu.modules";
	/** What the module search box is called, which is what a screen reader reads out. */
	public static final String MENU_SEARCH = ROOT + "menu.search";
	/** The greyed line in the empty search box, saying what there is to search. */
	public static final String MENU_SEARCH_HINT = ROOT + "menu.search_hint";
	/** What a keybind with no key shows. */
	public static final String MENU_UNBOUND = ROOT + "menu.unbound";
	/** Shown in the keybind widget while it waits for a key. */
	public static final String MENU_PRESS_KEY = ROOT + "menu.press_key";
	/** The line under a list that opens the picker. */
	public static final String MENU_ADD = ROOT + "menu.add";
	/** Drawn beside an id this build cannot resolve. */
	public static final String MENU_MISSING = ROOT + "menu.missing";
	/** Warning under a key that something else already uses; takes the other action's name. */
	public static final String MENU_CONFLICT = ROOT + "menu.conflict";
	/** Tooltip on the arrow that puts a setting back to its default. */
	public static final String MENU_RESET = ROOT + "menu.reset";

	/** The item picker's own title. */
	public static final String PICKER_TITLE = ROOT + "picker.title";
	/** Placeholder in the picker's search box. */
	public static final String PICKER_SEARCH = ROOT + "picker.search";
	/** The picker's footer; takes the number of results. */
	public static final String PICKER_FOOTER = ROOT + "picker.footer";
	/** Shown in place of the grid when the search finds nothing. */
	public static final String PICKER_EMPTY = ROOT + "picker.empty";
	/** Said on the tooltip of something the list already holds. */
	public static final String PICKER_PICKED = ROOT + "picker.picked";

	/** The one line in chat when AutoTotem reaches for a totem and there is none left. */
	public static final String TOTEM_OUT = ROOT + "autototem.out_of_totems";

	/** The one line in chat when Damar Kırıcı finds the server will not let it run its commands. */
	public static final String VEIN_NO_PERMISSION = ROOT + "veinminer.no_permission";

	/** Damar Kırıcı has a hunger cost to pay and the server will not let it run /effect. */
	public static final String VEIN_NO_EFFECT_PERMISSION = ROOT + "veinminer.no_effect_permission";

	/** Damar Kırıcı's job would cost more hunger than the bar can spare; it names the points. */
	public static final String VEIN_TOO_HUNGRY = ROOT + "veinminer.too_hungry";

	/** Damar Kırıcı would wear the tool and the server will not let it run /item. */
	public static final String VEIN_NO_ITEM_PERMISSION = ROOT + "veinminer.no_item_permission";

	/** The job would leave the tool in hand with nothing; it names the wear and what is left. */
	public static final String VEIN_TOOL_TOO_WORN = ROOT + "veinminer.tool_too_worn";

	/** Every block the job would take is outside Yapı Modu's area. */
	public static final String VEIN_OUTSIDE_AREA = ROOT + "veinminer.outside_area";

	/** No area is in force and Damar Kırıcı is set to work nowhere without one. */
	public static final String VEIN_NO_AREA = ROOT + "veinminer.no_area";

	/** Yapı Modu: a corner was marked; its number and where. */
	public static final String BUILD_CORNER = ROOT + "buildmode.corner";

	/** Yapı Modu: both corners, the size and the volume. */
	public static final String BUILD_AREA = ROOT + "buildmode.area";

	public static final String BUILD_NO_AREA = ROOT + "buildmode.no_area";

	public static final String BUILD_CLEARED = ROOT + "buildmode.cleared";

	/** Under /bolge goster while the module is off. */
	public static final String BUILD_OFF = ROOT + "buildmode.off";

	public static final String BUILD_NOT_IN_WORLD = ROOT + "buildmode.not_in_world";

	/** İskele on the action bar: how many blocks are left to build with. */
	public static final String SCAFFOLD_COUNT = ROOT + "scaffold.count";

	/** The same, once fewer are left than the warning says. */
	public static final String SCAFFOLD_LOW = ROOT + "scaffold.low";

	/** A block was needed and there was none to put down. */
	public static final String SCAFFOLD_EMPTY = ROOT + "scaffold.empty";

	/** The count, with the y the lock holds after it. */
	public static final String SCAFFOLD_WITH_Y = ROOT + "scaffold.with_y";

	/** The locked y alone, with the count off. */
	public static final String SCAFFOLD_Y = ROOT + "scaffold.y";

	/** The lock key: the lock now holds this y. */
	public static final String SCAFFOLD_Y_LOCKED = ROOT + "scaffold.y_locked";

	/** The lock key with the lock off. */
	public static final String SCAFFOLD_Y_LOCK_OFF = ROOT + "scaffold.y_lock_off";

	/** A torch was due and there were none left; said once until there are some again. */
	public static final String SCAFFOLD_NO_TORCHES = ROOT + "scaffold.no_torches";

	/** A block was wanted and the support list allows nothing there to build against; said once until one goes down. */
	public static final String SCAFFOLD_NO_SUPPORT = ROOT + "scaffold.no_support";

	/** Under the support list while "only the listed" is chosen and nothing is listed. */
	public static final String SCAFFOLD_SUPPORTS_EMPTY = ROOT + "scaffold.supports_empty";

	/** HUD lines: totems carried; Oto Silah's lock, on with seconds left or off; Damar Kırıcı's job; Dünya İndirici. */
	/** The profile bar: the profile in use, its four buttons, the name box's two, the delete question, and why a name will not do. */
	public static final String MENU_PROFILE = ROOT + "menu.profile";
	public static final String MENU_PROFILE_NEW = ROOT + "menu.profile.new";
	public static final String MENU_PROFILE_COPY = ROOT + "menu.profile.copy";
	public static final String MENU_PROFILE_RENAME = ROOT + "menu.profile.rename";
	public static final String MENU_PROFILE_DELETE = ROOT + "menu.profile.delete";
	public static final String MENU_PROFILE_OK = ROOT + "menu.profile.ok";
	public static final String MENU_PROFILE_CANCEL = ROOT + "menu.profile.cancel";
	public static final String MENU_PROFILE_DELETE_ASK = ROOT + "menu.profile.delete_ask";
	public static final String MENU_PROFILE_DELETE_YES = ROOT + "menu.profile.delete_yes";
	public static final String MENU_PROFILE_BLANK = ROOT + "menu.profile.blank";
	public static final String MENU_PROFILE_TOO_LONG = ROOT + "menu.profile.too_long";
	public static final String MENU_PROFILE_BAD_CHARACTER = ROOT + "menu.profile.bad_character";
	public static final String MENU_PROFILE_TAKEN = ROOT + "menu.profile.taken";

	/** A module's presets: the heading, saving the module's own, what applying did, a name taken, ↺ and ✕, and on/off in a preview. */
	public static final String MENU_PRESETS = ROOT + "menu.presets";
	public static final String MENU_PRESET_SAVE = ROOT + "menu.preset.save";
	public static final String MENU_PRESET_APPLIED = ROOT + "menu.preset.applied";
	public static final String MENU_PRESET_NO_CHANGE = ROOT + "menu.preset.no_change";
	public static final String MENU_PRESET_TAKEN = ROOT + "menu.preset.taken";
	public static final String MENU_PRESET_UNDO = ROOT + "menu.preset.undo";
	public static final String MENU_PRESET_DELETE = ROOT + "menu.preset.delete";
	public static final String MENU_ON = ROOT + "menu.on";
	public static final String MENU_OFF = ROOT + "menu.off";

	/** The built-in "safe" profile's name; the bar's way to the rules; the rules screen; the notice on joining. */
	public static final String PROFILE_SAFE = ROOT + "profile.safe";
	public static final String MENU_PROFILE_RULES = ROOT + "menu.profile.rules";
	public static final String RULES_TITLE = ROOT + "rules.title";
	public static final String RULES_SINGLEPLAYER = ROOT + "rules.singleplayer";
	public static final String RULES_FALLBACK = ROOT + "rules.fallback";
	public static final String RULES_KEEP = ROOT + "rules.keep";
	public static final String RULES_LIST = ROOT + "rules.list";
	public static final String RULES_ADD = ROOT + "rules.add";
	public static final String RULES_HINT = ROOT + "rules.hint";
	public static final String RULES_ADDRESS_HINT = ROOT + "rules.address_hint";
	public static final String RULES_DELETE = ROOT + "rules.delete";
	public static final String RULES_DONE = ROOT + "rules.done";
	public static final String TOAST_PROFILE = ROOT + "toast.profile";
	public static final String TOAST_RULE = ROOT + "toast.rule";
	public static final String TOAST_SINGLEPLAYER = ROOT + "toast.singleplayer";
	public static final String TOAST_FALLBACK = ROOT + "toast.fallback";

	/** The HUD editor: its button in the menu, its title, what it says to do, and its two buttons. */
	public static final String MENU_EDIT_HUD = ROOT + "menu.edit_hud";
	public static final String HUD_EDITOR_TITLE = ROOT + "hud.editor.title";
	public static final String HUD_EDITOR_HINT = ROOT + "hud.editor.hint";
	public static final String HUD_EDITOR_RESET_ALL = ROOT + "hud.editor.reset_all";
	public static final String HUD_EDITOR_DONE = ROOT + "hud.editor.done";

	public static final String HUD_TOTEMS = ROOT + "hud.totems";
	public static final String HUD_WEAPON_LOCKED = ROOT + "hud.weapon_locked";
	public static final String HUD_WEAPON_FREE = ROOT + "hud.weapon_free";
	public static final String HUD_VEIN = ROOT + "hud.vein";
	public static final String HUD_WDL_ON = ROOT + "hud.wdl_on";
	public static final String HUD_WDL_OFF = ROOT + "hud.wdl_off";
	public static final String HUD_WDL_REFUSED = ROOT + "hud.wdl_refused";

	private static final List<String> CHROME = List.of(
		MENU_MODULES, MENU_SEARCH, MENU_SEARCH_HINT, MENU_UNBOUND, MENU_PRESS_KEY, MENU_ADD, MENU_MISSING,
		MENU_CONFLICT, MENU_RESET, PICKER_TITLE, PICKER_SEARCH, PICKER_FOOTER, PICKER_EMPTY,
		PICKER_PICKED, TOTEM_OUT, VEIN_NO_PERMISSION, VEIN_NO_EFFECT_PERMISSION, VEIN_TOO_HUNGRY, VEIN_NO_ITEM_PERMISSION, VEIN_TOOL_TOO_WORN, VEIN_OUTSIDE_AREA,
		VEIN_NO_AREA, BUILD_CORNER, BUILD_AREA, BUILD_NO_AREA, BUILD_CLEARED, BUILD_OFF, BUILD_NOT_IN_WORLD,
		SCAFFOLD_COUNT, SCAFFOLD_LOW, SCAFFOLD_EMPTY, SCAFFOLD_WITH_Y, SCAFFOLD_Y, SCAFFOLD_Y_LOCKED, SCAFFOLD_Y_LOCK_OFF,
		SCAFFOLD_NO_TORCHES, SCAFFOLD_NO_SUPPORT, SCAFFOLD_SUPPORTS_EMPTY,
		MENU_PROFILE, MENU_PROFILE_NEW, MENU_PROFILE_COPY, MENU_PROFILE_RENAME, MENU_PROFILE_DELETE, MENU_PROFILE_OK,
		MENU_PROFILE_CANCEL, MENU_PROFILE_DELETE_ASK, MENU_PROFILE_DELETE_YES, MENU_PROFILE_BLANK, MENU_PROFILE_TOO_LONG,
		MENU_PROFILE_BAD_CHARACTER, MENU_PROFILE_TAKEN,
		MENU_PRESETS, MENU_PRESET_SAVE, MENU_PRESET_APPLIED, MENU_PRESET_NO_CHANGE, MENU_PRESET_TAKEN, MENU_PRESET_UNDO,
		MENU_PRESET_DELETE, MENU_ON, MENU_OFF,
		PROFILE_SAFE, MENU_PROFILE_RULES, RULES_TITLE, RULES_SINGLEPLAYER, RULES_FALLBACK, RULES_KEEP, RULES_LIST, RULES_ADD,
		RULES_HINT, RULES_ADDRESS_HINT, RULES_DELETE, RULES_DONE, TOAST_PROFILE, TOAST_RULE, TOAST_SINGLEPLAYER, TOAST_FALLBACK,
		MENU_EDIT_HUD, HUD_EDITOR_TITLE, HUD_EDITOR_HINT, HUD_EDITOR_RESET_ALL, HUD_EDITOR_DONE,
		HUD_TOTEMS, HUD_WEAPON_LOCKED, HUD_WEAPON_FREE, HUD_VEIN, HUD_WDL_ON, HUD_WDL_OFF, HUD_WDL_REFUSED);

	private Keys() {
	}

	public static String module(String moduleId) {
		return ROOT + "module." + moduleId;
	}

	public static String moduleDescription(String moduleId) {
		return module(moduleId) + ".desc";
	}

	public static String category(Category category) {
		return ROOT + "category." + lower(category.name());
	}

	/** A setting's own name, from the short key it was declared with ("autoeat.threshold"). */
	public static String setting(String key) {
		return ROOT + "setting." + key;
	}

	public static String tooltip(String key) {
		return setting(key) + ".tooltip";
	}

	/** One value of an enum setting, so two settings sharing an enum can word it differently. */
	public static String option(String key, Enum<?> value) {
		return setting(key) + "." + lower(value.name());
	}

	/** A built-in preset's name. */
	public static String preset(String moduleId, String presetId) {
		return ROOT + "preset." + moduleId + "." + presetId;
	}

	/** The greyed-out text a text box shows while it is empty. */
	public static String hint(String key) {
		return setting(key) + ".hint";
	}

	/** A dimension in the picker, which vanilla has no name of its own for. */
	public static String dimension(String path) {
		return ROOT + "dimension." + path;
	}

	/**
	 * Every key this build will ask for: the menu's own words, the categories, and whatever
	 * the modules declare all the way down their settings trees.
	 */
	public static Set<String> needed(List<Module> modules) {
		Set<String> keys = new LinkedHashSet<>(CHROME);

		for (String dimension : List.of("overworld", "the_nether", "the_end")) {
			keys.add(dimension(dimension));
		}

		for (Category category : Category.values()) {
			keys.add(category(category));
		}

		for (Module module : modules) {
			keys.add(module(module.id()));
			keys.add(moduleDescription(module.id()));
			walk(module.settings(), keys);
			keys.addAll(BuiltInPresets.keys(module.id()));
		}

		return keys;
	}

	private static void walk(SettingGroup group, Set<String> keys) {
		// A module's root group is a container with no heading, so it needs no words.
		if (group.key() != null) {
			keys.add(setting(group.key()));
		}

		for (SettingNode child : group.children()) {
			if (child instanceof SettingGroup nested) {
				walk(nested, keys);
				continue;
			}

			Setting<?> setting = (Setting<?>) child;
			keys.add(setting(setting.key()));
			keys.add(tooltip(setting.key()));

			if (setting instanceof EnumSetting<?> choice) {
				choice.options().forEach(option -> keys.add(option(setting.key(), option)));
			}

			if (setting instanceof TextSetting) {
				keys.add(hint(setting.key()));
			}
		}
	}

	private static String lower(String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
