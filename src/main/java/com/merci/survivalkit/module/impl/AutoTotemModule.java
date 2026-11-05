package com.merci.survivalkit.module.impl;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.autototem.AutoTotem;
import com.merci.survivalkit.autototem.ClientTotemContext;
import com.merci.survivalkit.autototem.TotemContext;
import com.merci.survivalkit.autototem.TotemMode;
import com.merci.survivalkit.autototem.TotemSettings;
import com.merci.survivalkit.hud.ClientActionBar;
import com.merci.survivalkit.hud.HudLine;
import com.merci.survivalkit.hud.HudSource;
import com.merci.survivalkit.hud.ModuleStatus;
import com.merci.survivalkit.hud.StatusLines;
import com.merci.survivalkit.module.Category;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.settings.RegistryListSetting;
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.util.InventoryUtil;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Keeps a totem in the off hand. All of the thinking lives in {@link AutoTotem}. */
public final class AutoTotemModule extends Module implements TotemSettings, HudSource {
	// The headings are sections: menu only. Every setting keeps the id it was saved under
	// before there were headings, so existing profiles load as they are.
	private final SettingGroup trigger = settings()
		.add(SettingGroup.section("trigger", "autototem.group.trigger"));

	private final EnumSetting<TotemMode> mode = trigger
		.add(new EnumSetting<>("mode", "autototem.mode", TotemMode.ALWAYS));

	private final IntSetting healthThreshold = trigger
		.add(new IntSetting("health_threshold", "autototem.health_threshold", 10, 1, 19));

	private final SettingGroup outOfTotems = settings()
		.add(SettingGroup.section("out_of_totems", "autototem.group.out_of_totems"));

	// Empty by default on purpose: an empty off hand is what a player already expects once
	// the totems run out, and anything else would be the mod choosing what goes in that hand.
	private final RegistryListSetting fallback = outOfTotems
		.add(new RegistryListSetting("fallback", "autototem.fallback", List.of(), RegistryListSetting.Kind.ITEM));

	private final BoolSetting warnWhenOut = outOfTotems
		.add(new BoolSetting("warn_when_out", "autototem.warn_when_out", true));

	private final SettingGroup hands = settings()
		.add(SettingGroup.section("hands", "autototem.group.hands"));

	private final EnumSetting<InventoryUtil.Scope> scope = hands
		.add(new EnumSetting<>("scope", "autototem.scope", InventoryUtil.Scope.HOTBAR));

	private final BoolSetting restoreOffhand = hands
		.add(new BoolSetting("restore_offhand", "autototem.restore_offhand", true));

	private final SettingGroup info = settings()
		.add(SettingGroup.section("info", "common.group.info"));

	private final BoolSetting actionBar = info
		.add(new BoolSetting("action_bar", "common.action_bar", false));

	private final ModuleStatus status = new ModuleStatus();

	private final AutoTotem autoTotem = new AutoTotem(this, SurvivalKit.hands());
	private TotemContext client;

	public AutoTotemModule() {
		super("autototem", Category.COMBAT, KeybindSetting.UNBOUND);

		// Both only mean anything when the hand is borrowed rather than taken for good.
		healthThreshold.setVisibleWhen(() -> mode.get() == TotemMode.HEALTH_THRESHOLD);
		restoreOffhand.setVisibleWhen(() -> mode.get() == TotemMode.HEALTH_THRESHOLD);
	}

	@Override
	public List<String> fallback() {
		return fallback.get();
	}

	@Override
	public boolean warnWhenOut() {
		return warnWhenOut.get();
	}

	@Override
	public TotemMode mode() {
		return mode.get();
	}

	@Override
	public int healthThreshold() {
		return healthThreshold.get();
	}

	@Override
	public boolean restoreOffhand() {
		return restoreOffhand.get();
	}

	@Override
	protected void onTick() {
		autoTotem.tick(client());

		if (Minecraft.getInstance().player != null) {
			status.update(StatusLines.totems(client().count(AutoTotem.TOTEM)), actionBar.get(), ClientActionBar::say);
		}
	}

	@Override
	protected void onDisable() {
		autoTotem.stop(client());
		status.clear();
	}

	@Override
	public List<HudLine> hudLines() {
		return status.lines();
	}

	/** Built on first use: at construction time the client is not up yet. */
	private TotemContext client() {
		if (this.client == null) {
			this.client = new ClientTotemContext(Minecraft.getInstance(), scope::get);
		}

		return this.client;
	}
}
