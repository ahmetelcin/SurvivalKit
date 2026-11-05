package com.merci.survivalkit.module.impl;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.area.Area;
import com.merci.survivalkit.hud.HudLine;
import com.merci.survivalkit.hud.HudSource;
import com.merci.survivalkit.module.Category;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.scaffold.BlockChoice;
import com.merci.survivalkit.scaffold.ClientScaffoldContext;
import com.merci.survivalkit.scaffold.Ramp;
import com.merci.survivalkit.scaffold.Scaffold;
import com.merci.survivalkit.scaffold.ScaffoldContext;
import com.merci.survivalkit.scaffold.SafeWalk;
import com.merci.survivalkit.scaffold.ScaffoldSettings;
import com.merci.survivalkit.scaffold.SupportMode;
import com.merci.survivalkit.scaffold.YLock;
import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.settings.RegistryListSetting;
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.merci.survivalkit.util.InventoryUtil;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

/** Puts a block under the next step. All of the thinking lives in {@link Scaffold}. */
public final class ScaffoldModule extends Module implements ScaffoldSettings, HudSource {
	/** Blocks nobody minds spending, in the order they pile up. */
	static final List<String> PREFERRED_BY_DEFAULT = List.of(
		"minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:dirt", "minecraft:netherrack");

	private final SettingGroup placement = settings()
		.add(SettingGroup.section("placement", "scaffold.group.placement"));

	private final IntSetting delay = placement
		.add(new IntSetting("delay", "scaffold.delay", 0, 0, 20));

	private final IntSetting perTick = placement
		.add(new IntSetting("per_tick", "scaffold.per_tick", 1, 1, 4));

	private final BoolSetting fillAir = placement
		.add(new BoolSetting("fill_air", "scaffold.fill_air", true));

	private final BoolSetting fillWater = placement
		.add(new BoolSetting("fill_water", "scaffold.fill_water", false));

	private final BoolSetting fillLava = placement
		.add(new BoolSetting("fill_lava", "scaffold.fill_lava", true));

	private final EnumSetting<SupportMode> supportMode = placement
		.add(new EnumSetting<>("support_mode", "scaffold.support_mode", SupportMode.ALL));

	private final RegistryListSetting supports = placement
		.add(new RegistryListSetting("supports", "scaffold.supports", List.of(), RegistryListSetting.Kind.BLOCK));

	private final SettingGroup extendGroup = settings()
		.add(SettingGroup.section("ahead", "scaffold.group.ahead"));

	private final IntSetting extend = extendGroup
		.add(new IntSetting("extend", "scaffold.extend", 0, 0, 5));

	private final BoolSetting keepY = extendGroup
		.add(new BoolSetting("keep_y", "scaffold.keep_y", false));

	private final EnumSetting<YLock> yLock = extendGroup
		.add(new EnumSetting<>("y_lock", "scaffold.y_lock", YLock.OFF));

	/** The world's build height, bottom to top. */
	private final IntSetting fixedY = extendGroup
		.add(new IntSetting("fixed_y", "scaffold.fixed_y", 64, -64, 319));

	private final KeybindSetting yLockKey = extendGroup
		.add(new KeybindSetting("y_lock_key", "scaffold.y_lock_key", KeybindSetting.UNBOUND));

	private final BoolSetting descend = extendGroup
		.add(new BoolSetting("descend", "scaffold.descend", false));

	private final EnumSetting<Ramp> ramp = extendGroup
		.add(new EnumSetting<>("ramp", "scaffold.ramp", Ramp.OFF));

	private final BoolSetting tower = extendGroup
		.add(new BoolSetting("tower", "scaffold.tower", true));

	private final BoolSetting towerOnlyWithJump = extendGroup
		.add(new BoolSetting("tower_only_with_jump", "scaffold.tower_only_with_jump", true));

	private final SettingGroup shape = settings()
		.add(SettingGroup.section("shape", "scaffold.group.shape"));

	private final IntSetting width = shape
		.add(new IntSetting("width", "scaffold.width", 1, 1, 3));

	private final BoolSetting diagonal = shape
		.add(new BoolSetting("diagonal", "scaffold.diagonal", true));

	private final SettingGroup blocks = settings()
		.add(SettingGroup.section("blocks", "scaffold.group.blocks"));

	private final RegistryListSetting preferred = blocks
		.add(new RegistryListSetting("preferred", "scaffold.preferred", PREFERRED_BY_DEFAULT, RegistryListSetting.Kind.BLOCK));

	private final RegistryListSetting blocked = blocks
		.add(new RegistryListSetting("blocked", "scaffold.blocked", BlockChoice.BLOCKED_BY_DEFAULT, RegistryListSetting.Kind.BLOCK));

	private final BoolSetting preferFullStack = blocks
		.add(new BoolSetting("prefer_full_stack", "scaffold.prefer_full_stack", false));

	private final EnumSetting<BlockChoice.Fallback> fallback = blocks
		.add(new EnumSetting<>("fallback", "scaffold.fallback", BlockChoice.Fallback.LARGEST_STACK));

	private final BoolSetting sameBlock = blocks
		.add(new BoolSetting("same_block", "scaffold.same_block", false));

	private final SettingGroup torchGroup = settings()
		.add(SettingGroup.section("lighting", "scaffold.group.lighting"));

	private final BoolSetting torches = torchGroup
		.add(new BoolSetting("torches", "scaffold.torches", false));

	private final IntSetting torchInterval = torchGroup
		.add(new IntSetting("torch_interval", "scaffold.torch_interval", 12, 1, 64));

	private final SettingGroup safety = settings()
		.add(SettingGroup.section("safety", "scaffold.group.safety"));

	private final EnumSetting<SafeWalk> safeWalk = safety
		.add(new EnumSetting<>("safe_walk", "scaffold.safe_walk", SafeWalk.OFF));

	private final BoolSetting pauseWithScreen = safety
		.add(new BoolSetting("pause_with_screen", "scaffold.pause_with_screen", true));

	private final BoolSetting pauseWhileFalling = safety
		.add(new BoolSetting("pause_while_falling", "scaffold.pause_while_falling", true));

	private final BoolSetting pauseInLava = safety
		.add(new BoolSetting("pause_in_lava", "scaffold.pause_in_lava", true));

	private final SettingGroup info = settings()
		.add(SettingGroup.section("info", "scaffold.group.info"));

	private final BoolSetting showCount = info
		.add(new BoolSetting("show_count", "scaffold.show_count", true));

	private final IntSetting lowWarning = info
		.add(new IntSetting("low_warning", "scaffold.low_warning", 16, 0, 256));

	private final BoolSetting showTarget = info
		.add(new BoolSetting("show_target", "scaffold.show_target", true));

	private final BoolSetting actionBar = info
		.add(new BoolSetting("action_bar", "common.action_bar", false));

	private final SettingGroup hands = settings()
		.add(SettingGroup.section("hands", "scaffold.group.hands"));

	private final EnumSetting<InventoryUtil.Scope> scope = hands
		.add(new EnumSetting<>("scope", "scaffold.scope", InventoryUtil.Scope.HOTBAR));

	private final BoolSetting restorePrevious = hands
		.add(new BoolSetting("restore_previous", "scaffold.restore_previous", true));

	private final Scaffold scaffold = new Scaffold(this, SurvivalKit.hands());
	private final Supplier<Optional<Area>> area;
	private ScaffoldContext client;

	/** With no Yapı Modu to ask: never an area. For the tests that only need the settings. */
	public ScaffoldModule() {
		this(Optional::empty);
	}

	/** @param area Yapı Modu's area for where the player is, empty while it is off */
	public ScaffoldModule(Supplier<Optional<Area>> area) {
		super("scaffold", Category.MOVEMENT, KeybindSetting.UNBOUND);
		this.area = area;

		// "Only the listed" with nothing listed is no limit; said where the list would be.
		supports.setNote(() -> supportMode.get() == SupportMode.LISTED && supports.get().isEmpty()
			? Optional.of(Lang.of(Keys.SCAFFOLD_SUPPORTS_EMPTY))
			: Optional.empty());
	}

	@Override
	public int delay() {
		return delay.get();
	}

	@Override
	public int perTick() {
		return perTick.get();
	}

	@Override
	public boolean fillAir() {
		return fillAir.get();
	}

	@Override
	public boolean fillWater() {
		return fillWater.get();
	}

	@Override
	public boolean fillLava() {
		return fillLava.get();
	}

	@Override
	public SupportMode supportMode() {
		return supportMode.get();
	}

	@Override
	public List<String> supports() {
		return supports.get();
	}

	@Override
	public List<String> preferred() {
		return preferred.get();
	}

	@Override
	public List<String> blocked() {
		return blocked.get();
	}

	@Override
	public boolean preferFullStack() {
		return preferFullStack.get();
	}

	@Override
	public BlockChoice.Fallback fallback() {
		return fallback.get();
	}

	@Override
	public int extend() {
		return extend.get();
	}

	@Override
	public boolean keepY() {
		return keepY.get();
	}

	@Override
	public YLock yLock() {
		return yLock.get();
	}

	@Override
	public int fixedY() {
		return fixedY.get();
	}

	@Override
	public void fixedY(int y) {
		fixedY.set(y);
		SurvivalKit.settingsChanged();
	}

	@Override
	public int width() {
		return width.get();
	}

	@Override
	public boolean diagonal() {
		return diagonal.get();
	}

	@Override
	public Ramp ramp() {
		return ramp.get();
	}

	@Override
	public boolean descend() {
		return descend.get();
	}

	@Override
	public boolean sameBlock() {
		return sameBlock.get();
	}

	@Override
	public boolean torches() {
		return torches.get();
	}

	@Override
	public int torchInterval() {
		return torchInterval.get();
	}

	@Override
	public boolean tower() {
		return tower.get();
	}

	@Override
	public boolean towerOnlyWithJump() {
		return towerOnlyWithJump.get();
	}

	@Override
	public SafeWalk safeWalk() {
		return safeWalk.get();
	}

	@Override
	public boolean pauseWithScreen() {
		return pauseWithScreen.get();
	}

	@Override
	public boolean pauseWhileFalling() {
		return pauseWhileFalling.get();
	}

	@Override
	public boolean pauseInLava() {
		return pauseInLava.get();
	}

	@Override
	public boolean showCount() {
		return showCount.get();
	}

	@Override
	public int lowWarning() {
		return lowWarning.get();
	}

	@Override
	public boolean showTarget() {
		return showTarget.get();
	}

	@Override
	public boolean actionBar() {
		return actionBar.get();
	}

	@Override
	public List<HudLine> hudLines() {
		return scaffold.hudLines();
	}

	@Override
	public boolean restorePrevious() {
		return restorePrevious.get();
	}

	@Override
	protected void onTick() {
		scaffold.tick(client());
	}

	@Override
	protected void onDisable() {
		scaffold.stop(client());
	}

	/** Built on first use: at construction time the client is not up yet. */
	private ScaffoldContext client() {
		if (client == null) {
			client = new ClientScaffoldContext(Minecraft.getInstance(), scope::get, yLockKey::get, area);
		}

		return client;
	}
}
