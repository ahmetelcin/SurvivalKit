package com.merci.survivalkit.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.module.impl.AutoEatModule;
import com.merci.survivalkit.module.impl.AutoTotemModule;
import com.merci.survivalkit.module.impl.AutoToolModule;
import com.merci.survivalkit.module.impl.AutoWeaponModule;
import com.merci.survivalkit.module.impl.ScaffoldModule;
import com.merci.survivalkit.module.impl.HudModule;
import com.merci.survivalkit.module.impl.VeinMinerModule;
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.settings.SettingNode;
import com.merci.survivalkit.util.SearchText;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * How the settings are gathered under headings, and how those headings read.
 *
 * <p>The words are checked in both shipped languages: a heading and a line under it that say
 * the same thing ("Golden apple" under "Golden apple") waste the reader's time in either one.
 */
class SettingHeadingsTest {
	private static final List<String> LANGUAGES = List.of("en_us", "tr_tr");

	private static List<Module> grouped() {
		return List.of(new AutoEatModule(), new AutoTotemModule(), new AutoToolModule(), new AutoWeaponModule(), new VeinMinerModule(), new ScaffoldModule(), new HudModule());
	}

	@Test
	void autoWeaponIsGatheredUnderItsHeadings() {
		assertEquals(List.of(
			"enabled",
			"key",
			"weapons: types, durability_threshold",
			"selection: source, range",
			"aim: targets, auto_attack",
			"prep: prep_slot, select_prep_slot",
			"lock: lock_seconds, lock_aim",
			"info: action_bar"),
			outline(new AutoWeaponModule()));
	}

	@Test
	void autoEatIsGatheredUnderFourHeadings() {
		assertEquals(List.of(
			"enabled",
			"key",
			"trigger: threshold_mode, threshold, eat_early_when_hurt, early_eating_health",
			"food: priority, blocked, fallback",
			"golden_apples: golden_apple, health_threshold, enchanted_golden_apple",
			"hands: scope, eating_hand, keep_offhand_totem, return_to_slot"),
			outline(new AutoEatModule()));
	}

	@Test
	void autoTotemIsGatheredUnderFourHeadings() {
		assertEquals(List.of(
			"enabled",
			"key",
			"trigger: mode, health_threshold",
			"out_of_totems: fallback, warn_when_out",
			"hands: scope, restore_offhand",
			"info: action_bar"),
			outline(new AutoTotemModule()));
	}

	@Test
	void autoToolIsGatheredUnderThreeHeadings() {
		assertEquals(List.of(
			"enabled",
			"key",
			"which_tool: durability_threshold, keep_sword, keep_silk_touch, only_with_tool",
			"enchantments: silk_touch_blocks, fortune_blocks",
			"hands: scope, restore_previous"),
			outline(new AutoToolModule()));
	}

	@Test
	void veinMinerIsGatheredUnderFiveHeadings() {
		assertEquals(List.of(
			"enabled",
			"key",
			"trigger: trigger_key, mode, show_outline",
			"target: blocks, max_blocks, natural_trees_only",
			"server: drops, commands_per_second, hide_feedback",
			"limits: without_area, blocks_per_hunger_point, tool_wear",
			"info: action_bar"),
			outline(new VeinMinerModule()));
	}

	@Test
	void scaffoldIsGatheredUnderEightHeadings() {
		assertEquals(List.of(
			"enabled",
			"key",
			"placement: delay, per_tick, fill_air, fill_water, fill_lava, support_mode, supports",
			"ahead: extend, keep_y, y_lock, fixed_y, y_lock_key, descend, ramp, tower, tower_only_with_jump",
			"shape: width, diagonal",
			"blocks: preferred, blocked, prefer_full_stack, fallback, same_block",
			"lighting: torches, torch_interval",
			"safety: safe_walk, pause_with_screen, pause_while_falling, pause_in_lava",
			"info: show_count, low_warning, show_target, action_bar",
			"hands: scope, restore_previous"),
			outline(new ScaffoldModule()));
	}

	@Test
	void hudIsGatheredUnderOneHeadingPerPanel() {
		assertEquals(List.of(
			"enabled",
			"key",
			"active_modules: active_modules_shown, active_modules_anchor, active_modules_x, active_modules_y, active_modules_scale, active_modules_sort"),
			outline(new HudModule()));
	}

	/** The headings are menu furniture; the file must not grow a level for each. */
	@Test
	void everyHeadingIsASection() {
		for (Module module : grouped()) {
			for (SettingNode child : module.settings().children()) {
				if (child instanceof SettingGroup group) {
					assertTrue(group.isSection(), module.id() + "." + group.id() + " dosyada bir kat açıyor");
				}
			}
		}
	}

	@Test
	void noLineRepeatsTheHeadingItSitsUnder() {
		for (String language : LANGUAGES) {
			TestLang.install(language);

			for (Module module : grouped()) {
				for (SettingGroup heading : headings(module)) {
					String name = heading.displayName();

					for (SettingNode child : heading.children()) {
						String line = child.displayName();

						assertFalse(SearchText.contains(line, name) || SearchText.contains(name, line),
							language + ": \"" + line + "\" başlığını tekrar ediyor: \"" + name + "\"");
					}
				}
			}
		}
	}

	@Test
	void noTwoHeadingsOfAModuleShareAName() {
		for (String language : LANGUAGES) {
			TestLang.install(language);

			for (Module module : grouped()) {
				Set<String> seen = new HashSet<>();

				for (SettingGroup heading : headings(module)) {
					assertTrue(seen.add(SearchText.fold(heading.displayName())),
						language + ": " + module.id() + " modülünde iki başlık aynı: " + heading.displayName());
				}
			}
		}
	}

	private static List<SettingGroup> headings(Module module) {
		List<SettingGroup> found = new ArrayList<>();

		for (SettingNode child : module.settings().children()) {
			if (child instanceof SettingGroup group) {
				found.add(group);
			}
		}

		return found;
	}

	/** One line per top-level entry: a setting's id, or a heading's id and what is under it. */
	private static List<String> outline(Module module) {
		List<String> lines = new ArrayList<>();

		for (SettingNode child : module.settings().children()) {
			if (child instanceof SettingGroup group) {
				lines.add(group.id() + ": " + String.join(", ",
					group.children().stream().map(SettingNode::id).toList()));
			} else {
				lines.add(child.id());
			}
		}

		return lines;
	}
}
