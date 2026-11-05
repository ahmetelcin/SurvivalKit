package com.merci.survivalkit.module.impl;

import com.merci.survivalkit.hud.ActiveModules;
import com.merci.survivalkit.hud.ActiveSort;
import com.merci.survivalkit.hud.Anchor;
import com.merci.survivalkit.hud.HudPanel;
import com.merci.survivalkit.hud.HudPlacement;
import com.merci.survivalkit.hud.HudSource;
import com.merci.survivalkit.hud.SourcePanel;
import com.merci.survivalkit.module.Category;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.settings.SettingGroup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * The HUD: boxes of lines on the screen, each placed by its own settings. On from the first start,
 * since all it does is show things. The drawing is {@link com.merci.survivalkit.hud.ClientHud}'s.
 */
public final class HudModule extends Module {
	private final SettingGroup activeGroup = settings()
		.add(SettingGroup.section(ActiveModules.ID, "hud.group.active_modules"));

	private final HudPlacement activePlacement = new HudPlacement(activeGroup, ActiveModules.ID, Anchor.TOP_RIGHT, 2, 2);

	private final EnumSetting<ActiveSort> activeSort = activeGroup
		.add(new EnumSetting<>("active_modules_sort", "hud.active_sort", ActiveSort.LENGTH));

	/** How far apart the modules' boxes start: two lines and a gap. */
	static final int STACK = 24;

	private final List<HudPanel> panels = new ArrayList<>();

	/** With no modules to list. For the tests that only need the settings. */
	public HudModule() {
		this(List::of, List.of());
	}

	/**
	 * @param sources the modules with a box of their own, each under its own heading here; they
	 *                start stacked down the left edge, one box's height apart
	 */
	public HudModule(Supplier<List<Module>> modules, List<HudSource> sources) {
		super("hud", Category.RENDER, KeybindSetting.UNBOUND, true);
		panels.add(new ActiveModules(modules, activeSort::get, activePlacement, id()));

		for (int i = 0; i < sources.size(); i++) {
			HudSource source = sources.get(i);
			SettingGroup group = settings().add(SettingGroup.section(source.id(), "hud.group." + source.id()));
			panels.add(new SourcePanel(source, new HudPlacement(group, source.id(), Anchor.TOP_LEFT, 2, 2 + i * STACK)));
		}
	}

	/** Every panel, in drawing order. */
	public List<HudPanel> panels() {
		return Collections.unmodifiableList(panels);
	}
}
