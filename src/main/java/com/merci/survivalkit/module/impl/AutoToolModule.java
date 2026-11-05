package com.merci.survivalkit.module.impl;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.autotool.AutoTool;
import com.merci.survivalkit.autotool.ClientToolContext;
import com.merci.survivalkit.autotool.ToolContext;
import com.merci.survivalkit.autotool.ToolSettings;
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
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;

/** Takes out the best tool while a block breaks. All of the thinking lives in {@link AutoTool}. */
public final class AutoToolModule extends Module implements ToolSettings {
	/** The sixteen dye colours, in the game's own order. */
	private static final List<String> COLOURS = List.of(
		"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
		"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

	/**
	 * Blocks that are lost without Silk Touch. An ender chest turns into obsidian; glass,
	 * panes and ice drop nothing at all. Tinted glass is left out: it drops itself anyway.
	 */
	static final List<String> SILK_TOUCH_BY_DEFAULT = Stream.of(
			Stream.of("minecraft:ender_chest", "minecraft:glass", "minecraft:glass_pane"),
			COLOURS.stream().map(colour -> "minecraft:" + colour + "_stained_glass"),
			COLOURS.stream().map(colour -> "minecraft:" + colour + "_stained_glass_pane"),
			Stream.of("minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice"))
		.flatMap(ids -> ids)
		.toList();

	/**
	 * The ores whose drop Fortune multiplies. Iron, gold and copper are in: since 1.17 they
	 * drop raw metal, and Fortune multiplies that too.
	 */
	static final List<String> FORTUNE_BY_DEFAULT = List.of(
		"minecraft:coal_ore", "minecraft:deepslate_coal_ore",
		"minecraft:iron_ore", "minecraft:deepslate_iron_ore",
		"minecraft:gold_ore", "minecraft:deepslate_gold_ore",
		"minecraft:copper_ore", "minecraft:deepslate_copper_ore",
		"minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
		"minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
		"minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
		"minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
		"minecraft:nether_quartz_ore", "minecraft:nether_gold_ore");

	private final SettingGroup whichTool = settings()
		.add(SettingGroup.section("which_tool", "autotool.group.which_tool"));

	private final IntSetting durabilityThreshold = whichTool
		.add(new IntSetting("durability_threshold", "autotool.durability_threshold", 10, 0, 100));

	private final BoolSetting keepSword = whichTool
		.add(new BoolSetting("keep_sword", "autotool.keep_sword", true));

	private final BoolSetting keepSilkTouch = whichTool
		.add(new BoolSetting("keep_silk_touch", "autotool.keep_silk_touch", true));

	private final BoolSetting onlyWithToolInHand = whichTool
		.add(new BoolSetting("only_with_tool", "autotool.only_with_tool", true));

	private final SettingGroup enchantments = settings()
		.add(SettingGroup.section("enchantments", "autotool.group.enchantments"));

	private final RegistryListSetting silkTouchBlocks = enchantments
		.add(new RegistryListSetting("silk_touch_blocks", "autotool.silk_touch_blocks", SILK_TOUCH_BY_DEFAULT, RegistryListSetting.Kind.BLOCK));

	private final RegistryListSetting fortuneBlocks = enchantments
		.add(new RegistryListSetting("fortune_blocks", "autotool.fortune_blocks", FORTUNE_BY_DEFAULT, RegistryListSetting.Kind.BLOCK));

	private final SettingGroup hands = settings()
		.add(SettingGroup.section("hands", "autotool.group.hands"));

	private final EnumSetting<InventoryUtil.Scope> scope = hands
		.add(new EnumSetting<>("scope", "autotool.scope", InventoryUtil.Scope.HOTBAR));

	private final BoolSetting restorePrevious = hands
		.add(new BoolSetting("restore_previous", "autotool.restore_previous", true));

	private final AutoTool autoTool = new AutoTool(this, SurvivalKit.hands());
	private ToolContext client;

	public AutoToolModule() {
		super("autotool", Category.PLAYER, KeybindSetting.UNBOUND);
	}

	@Override
	public boolean restorePrevious() {
		return restorePrevious.get();
	}

	@Override
	public List<String> silkTouchBlocks() {
		return silkTouchBlocks.get();
	}

	@Override
	public List<String> fortuneBlocks() {
		return fortuneBlocks.get();
	}

	@Override
	public int durabilityThreshold() {
		return durabilityThreshold.get();
	}

	@Override
	public boolean keepSword() {
		return keepSword.get();
	}

	@Override
	public boolean keepSilkTouch() {
		return keepSilkTouch.get();
	}

	@Override
	public boolean onlyWithToolInHand() {
		return onlyWithToolInHand.get();
	}

	@Override
	protected void onTick() {
		autoTool.tick(client());
	}

	@Override
	protected void onDisable() {
		autoTool.stop(client());
	}

	/** Built on first use: at construction time the client is not up yet. */
	private ToolContext client() {
		if (this.client == null) {
			this.client = new ClientToolContext(Minecraft.getInstance(), scope::get);
		}

		return this.client;
	}
}
