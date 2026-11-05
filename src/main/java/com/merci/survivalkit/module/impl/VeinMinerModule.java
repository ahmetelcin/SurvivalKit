package com.merci.survivalkit.module.impl;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.hud.ClientActionBar;
import com.merci.survivalkit.hud.HudLine;
import com.merci.survivalkit.hud.HudSource;
import com.merci.survivalkit.hud.ModuleStatus;
import com.merci.survivalkit.hud.StatusLines;
import com.merci.survivalkit.area.Area;
import com.merci.survivalkit.area.WithoutArea;
import com.merci.survivalkit.module.Category;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.settings.RegistryListSetting;
import com.merci.survivalkit.settings.SettingGroup;
import com.merci.survivalkit.vein.ClientVeinContext;
import com.merci.survivalkit.vein.Drops;
import com.merci.survivalkit.vein.Pos;
import com.merci.survivalkit.vein.Trigger;
import com.merci.survivalkit.vein.VeinBreaker;
import com.merci.survivalkit.vein.VeinSettings;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Damar Kırıcı. While the module is on, its trigger key sets it off: held while a block is broken
 * the usual way, or pressed while looking at one — the menu picks which. All of the thinking
 * lives in {@link VeinBreaker}.
 */
public final class VeinMinerModule extends Module implements VeinSettings, HudSource {
	static final List<String> LOGS = List.of(
		"minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log", "minecraft:jungle_log",
		"minecraft:acacia_log", "minecraft:dark_oak_log", "minecraft:mangrove_log", "minecraft:cherry_log",
		"minecraft:pale_oak_log", "minecraft:crimson_stem", "minecraft:warped_stem");

	static final List<String> ORES = List.of(
		"minecraft:coal_ore", "minecraft:deepslate_coal_ore",
		"minecraft:iron_ore", "minecraft:deepslate_iron_ore",
		"minecraft:copper_ore", "minecraft:deepslate_copper_ore",
		"minecraft:gold_ore", "minecraft:deepslate_gold_ore",
		"minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
		"minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
		"minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
		"minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
		"minecraft:nether_gold_ore", "minecraft:nether_quartz_ore", "minecraft:ancient_debris");

	private final SettingGroup triggerGroup = settings()
		.add(SettingGroup.section("trigger", "veinminer.group.trigger"));

	private final KeybindSetting triggerKey = triggerGroup
		.add(new KeybindSetting("trigger_key", "veinminer.trigger_key", KeybindSetting.UNBOUND));

	private final EnumSetting<Trigger> trigger = triggerGroup
		.add(new EnumSetting<>("mode", "veinminer.mode", Trigger.HOLD_AND_BREAK));

	private final BoolSetting showOutline = triggerGroup
		.add(new BoolSetting("show_outline", "veinminer.show_outline", true));

	private final SettingGroup target = settings()
		.add(SettingGroup.section("target", "veinminer.group.target"));

	private final RegistryListSetting blocks = target
		.add(new RegistryListSetting("blocks", "veinminer.blocks", Stream.concat(LOGS.stream(), ORES.stream()).toList(), RegistryListSetting.Kind.BLOCK));

	private final IntSetting maxBlocks = target
		.add(new IntSetting("max_blocks", "veinminer.max_blocks", 128, 1, 1024));

	private final BoolSetting naturalTreesOnly = target
		.add(new BoolSetting("natural_trees_only", "veinminer.natural_trees_only", true));

	private final SettingGroup server = settings()
		.add(SettingGroup.section("server", "veinminer.group.server"));

	private final EnumSetting<Drops> drops = server
		.add(new EnumSetting<>("drops", "veinminer.drops", Drops.INVENTORY));

	private final IntSetting commandsPerSecond = server
		.add(new IntSetting("commands_per_second", "veinminer.commands_per_second", 10, 1, 100));

	private final BoolSetting hideFeedback = server
		.add(new BoolSetting("hide_feedback", "veinminer.hide_feedback", true));

	private final SettingGroup limits = settings()
		.add(SettingGroup.section("limits", "veinminer.group.limits"));

	private final EnumSetting<WithoutArea> withoutArea = limits
		.add(new EnumSetting<>("without_area", "veinminer.without_area", WithoutArea.EVERYWHERE));

	/** 0 is free. */
	private final IntSetting blocksPerHungerPoint = limits
		.add(new IntSetting("blocks_per_hunger_point", "veinminer.blocks_per_hunger_point", 10, 0, 1024));

	private final BoolSetting toolWear = limits
		.add(new BoolSetting("tool_wear", "veinminer.tool_wear", true));

	private final VeinBreaker breaker = new VeinBreaker(this);
	private final Supplier<Optional<Area>> area;
	private final SettingGroup info = settings()
		.add(SettingGroup.section("info", "common.group.info"));

	private final BoolSetting actionBar = info
		.add(new BoolSetting("action_bar", "common.action_bar", false));

	private final ModuleStatus status = new ModuleStatus();
	private ClientVeinContext client;

	/** With no Yapı Modu to ask: never an area. For the tests that only need the settings. */
	public VeinMinerModule() {
		this(Optional::empty);
	}

	/** @param area Yapı Modu's area for where the player is, empty while it is off */
	public VeinMinerModule(Supplier<Optional<Area>> area) {
		super("veinminer", Category.PLAYER, KeybindSetting.UNBOUND);
		this.area = area;

		// Every tick, on or off: what was already sent finishes, and the replies to the last
		// commands still have to be recognised for a moment after.
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			VeinBreaker.Outcome before = breaker.lastOutcome();
			breaker.tick(client(), isEnabled());

			if (isEnabled()) {
				status.update(StatusLines.vein(breaker.progress(), breaker.jobBlocks()), actionBar.get(), ClientActionBar::say);
			} else {
				status.clear();
			}

			log(before);
		});

		ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> {
			if (isEnabled()) {
				VeinBreaker.Outcome outcome = breaker.broken(client(), ClientVeinContext.broken(pos, state));

				if (outcome != VeinBreaker.Outcome.NOT_TRIGGERED) {
					SurvivalKit.LOGGER.info("[veinminer] {}", outcome);
				}
			}
		});

		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
			!(message.getContents() instanceof TranslatableContents reply && breaker.hides(reply.getKey())));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> breaker.forgetWarning());
	}

	/** The job being sent, and how far it has got. */
	@Override
	public List<HudLine> hudLines() {
		return status.lines();
	}

	/** A press in "look and press" mode is decided inside the tick; it is logged here. */
	private void log(VeinBreaker.Outcome before) {
		if (breaker.lastOutcome() != before && breaker.lastOutcome() != null) {
			SurvivalKit.LOGGER.info("[veinminer] {}", breaker.lastOutcome());
		}
	}

	@Override
	protected void onDisable() {
		breaker.cancel();
		breaker.forgetHunger();
	}

	@Override
	public Trigger trigger() {
		return trigger.get();
	}

	@Override
	public List<String> blocks() {
		return blocks.get();
	}

	@Override
	public int maxBlocks() {
		return maxBlocks.get();
	}

	@Override
	public boolean naturalTreesOnly() {
		return naturalTreesOnly.get();
	}

	@Override
	public boolean showOutline() {
		return showOutline.get();
	}

	@Override
	public Drops drops() {
		return drops.get();
	}

	@Override
	public int commandsPerSecond() {
		return commandsPerSecond.get();
	}

	@Override
	public boolean hideFeedback() {
		return hideFeedback.get();
	}

	@Override
	public WithoutArea withoutArea() {
		return withoutArea.get();
	}

	@Override
	public int blocksPerHungerPoint() {
		return blocksPerHungerPoint.get();
	}

	@Override
	public boolean toolWear() {
		return toolWear.get();
	}

	/** Every command sent so far, for the gametests. */
	public List<String> sentCommands() {
		return client().sent();
	}

	/** What is outlined this tick, for the gametests. */
	public List<Pos> outlined() {
		return breaker.outline();
	}

	/** Whether commands are still waiting to go. */
	public boolean busy() {
		return breaker.busy();
	}

	public VeinBreaker.Outcome lastOutcome() {
		return breaker.lastOutcome();
	}

	/** Built on first use: at construction time the client is not up yet. */
	private ClientVeinContext client() {
		if (this.client == null) {
			this.client = new ClientVeinContext(Minecraft.getInstance(), triggerKey::get, area);
		}

		return this.client;
	}
}
