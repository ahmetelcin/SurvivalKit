package com.merci.survivalkit.module.impl;

import com.merci.survivalkit.SurvivalKit;
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
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.weapon.AutoWeapon;
import com.merci.survivalkit.weapon.ClientWeaponContext;
import com.merci.survivalkit.weapon.WeaponContext;
import com.merci.survivalkit.weapon.WeaponSettings;
import com.merci.survivalkit.weapon.WeaponSource;
import com.merci.survivalkit.weapon.WeaponTargets;
import com.merci.survivalkit.weapon.WeaponTypes;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Oto Silah. All of the thinking lives in {@link AutoWeapon}. */
public final class AutoWeaponModule extends Module implements WeaponSettings, HudSource {
	private final SettingGroup weapons = settings()
		.add(SettingGroup.section("weapons", "autoweapon.group.weapons"));

	private final EnumSetting<WeaponTypes> types = weapons
		.add(new EnumSetting<>("types", "autoweapon.types", WeaponTypes.SWORDS));

	private final IntSetting durabilityThreshold = weapons
		.add(new IntSetting("durability_threshold", "autoweapon.durability_threshold", 10, 0, 100));

	private final SettingGroup selection = settings()
		.add(SettingGroup.section("selection", "autoweapon.group.selection"));

	private final EnumSetting<WeaponSource> source = selection
		.add(new EnumSetting<>("source", "autoweapon.source", WeaponSource.AIM_THEN_CROWD));

	/** For the crowd and the ready slot alike. */
	private final IntSetting range = selection
		.add(new IntSetting("range", "autoweapon.range", 8, 2, 32));

	private final SettingGroup aim = settings()
		.add(SettingGroup.section("aim", "autoweapon.group.aim"));

	private final EnumSetting<WeaponTargets> targets = aim
		.add(new EnumSetting<>("targets", "autoweapon.targets", WeaponTargets.MOBS));

	private final BoolSetting autoAttack = aim
		.add(new BoolSetting("auto_attack", "autoweapon.auto_attack", false));

	private final SettingGroup prep = settings()
		.add(SettingGroup.section("prep", "autoweapon.group.prep"));

	/** 1-9 is the hotbar slot as the game numbers it; 0 is off. */
	private final IntSetting prepSlot = prep
		.add(new IntSetting("prep_slot", "autoweapon.prep_slot", 0, 0, 9));

	private final BoolSetting selectPrepSlot = prep
		.add(new BoolSetting("select_prep_slot", "autoweapon.select_prep_slot", false));


	private final SettingGroup lock = settings()
		.add(SettingGroup.section("lock", "autoweapon.group.lock"));

	private final IntSetting lockSeconds = lock
		.add(new IntSetting("lock_seconds", "autoweapon.lock_seconds", 3, 0, 10));

	private final BoolSetting lockAim = lock
		.add(new BoolSetting("lock_aim", "autoweapon.lock_aim", true));

	private final AutoWeapon autoWeapon = new AutoWeapon(this, SurvivalKit.hands(), SurvivalKit.LOGGER::warn);
	private final SettingGroup info = settings()
		.add(SettingGroup.section("info", "common.group.info"));

	private final BoolSetting actionBar = info
		.add(new BoolSetting("action_bar", "common.action_bar", false));

	private final ModuleStatus status = new ModuleStatus();
	private WeaponContext client;

	public AutoWeaponModule() {
		super("autoweapon", Category.COMBAT, KeybindSetting.UNBOUND);
	}

	@Override
	public WeaponTypes types() {
		return types.get();
	}

	@Override
	public int durabilityThreshold() {
		return durabilityThreshold.get();
	}

	@Override
	public WeaponTargets targets() {
		return targets.get();
	}

	@Override
	public boolean autoAttack() {
		return autoAttack.get();
	}

	@Override
	public int prepSlot() {
		return prepSlot.get();
	}

	@Override
	public boolean selectPrepSlot() {
		return selectPrepSlot.get();
	}

	@Override
	public WeaponSource source() {
		return source.get();
	}

	@Override
	public int range() {
		return range.get();
	}

	@Override
	public int lockSeconds() {
		return lockSeconds.get();
	}

	@Override
	public boolean lockAim() {
		return lockAim.get();
	}

	@Override
	protected void onTick() {
		autoWeapon.tick(client());

		if (client().ready()) {
			status.update(StatusLines.weaponLock(autoWeapon.lockTicksLeft(client().gameTime())), actionBar.get(), ClientActionBar::say);
		}
	}

	/** Only once there is a game to ask: settings are loaded, and may switch this on, before one. */
	@Override
	protected void onEnable() {
		if (Minecraft.getInstance() != null && Minecraft.getInstance().player != null) {
			autoWeapon.start(client());
		}
	}

	@Override
	protected void onDisable() {
		status.clear();
	}

	@Override
	public List<HudLine> hudLines() {
		return status.lines();
	}

	/** Built on first use: at construction time the client is not up yet. */
	private WeaponContext client() {
		if (this.client == null) {
			this.client = new ClientWeaponContext(Minecraft.getInstance());
		}

		return this.client;
	}
}
