package com.merci.survivalkit.module.impl;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.autotool.ClientPickContext;
import com.merci.survivalkit.autotool.PickContext;
import com.merci.survivalkit.autotool.PickMode;
import com.merci.survivalkit.autotool.PickSettings;
import com.merci.survivalkit.autotool.ToolPicker;
import com.merci.survivalkit.module.Category;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.util.InventoryUtil;
import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Alet Seçici. All of the thinking lives in {@link ToolPicker}; the block lists and the
 * durability threshold are Otomatik Alet's, so they are kept in one place.
 */
public final class ToolPickerModule extends Module implements PickSettings {
	private final EnumSetting<PickMode> mode = settings()
		.add(new EnumSetting<>("mode", "toolpicker.mode", PickMode.ALWAYS));

	private final EnumSetting<InventoryUtil.Scope> scope = settings()
		.add(new EnumSetting<>("scope", "toolpicker.scope", InventoryUtil.Scope.HOTBAR));

	private final AutoToolModule autoTool;
	private final ToolPicker picker = new ToolPicker(this, SurvivalKit.hands());
	private PickContext client;

	public ToolPickerModule(AutoToolModule autoTool) {
		super("toolpicker", Category.PLAYER, KeybindSetting.UNBOUND);
		this.autoTool = Objects.requireNonNull(autoTool, "autoTool");

		// At the start of the tick, not with the other modules at its end: the game reads the
		// pick-block click in between, and the click has to be taken before it does.
		ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
			if (isEnabled()) {
				picker.tick(client());
			}
		});
	}

	@Override
	public PickMode mode() {
		return mode.get();
	}

	@Override
	public List<String> silkTouchBlocks() {
		return autoTool.silkTouchBlocks();
	}

	@Override
	public List<String> fortuneBlocks() {
		return autoTool.fortuneBlocks();
	}

	@Override
	public int durabilityThreshold() {
		return autoTool.durabilityThreshold();
	}

	/** Built on first use: at construction time the client is not up yet. */
	private PickContext client() {
		if (this.client == null) {
			this.client = new ClientPickContext(Minecraft.getInstance(), scope::get);
		}

		return this.client;
	}
}
