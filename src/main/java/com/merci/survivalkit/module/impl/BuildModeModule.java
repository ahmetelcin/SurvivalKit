package com.merci.survivalkit.module.impl;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.area.Area;
import com.merci.survivalkit.area.AreaStore;
import com.merci.survivalkit.area.BuildMode;
import com.merci.survivalkit.area.ClientBuildContext;
import com.merci.survivalkit.area.Corners;
import com.merci.survivalkit.area.Corners.Corner;
import com.merci.survivalkit.area.Place;
import com.merci.survivalkit.module.Category;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.settings.RegistryListSetting;
import com.mojang.brigadier.Command;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * Yapı Modu. On, the wand marks an area — left click the first corner, right click the second —
 * the area is drawn, and the block-breaking modules keep to it. Off, the wand is an ordinary item,
 * nothing is drawn, and the area holds nothing back; it is kept for when the module comes back on.
 * All of the thinking lives in {@link BuildMode}; /bolge reaches it from chat.
 */
public final class BuildModeModule extends Module {
	static final List<String> WAND_BY_DEFAULT = List.of("minecraft:wooden_sword");

	private final RegistryListSetting wand = settings()
		.add(new RegistryListSetting("wand", "buildmode.wand", WAND_BY_DEFAULT, RegistryListSetting.Kind.ITEM));

	private BuildMode buildMode;
	private ClientBuildContext client;

	public BuildModeModule() {
		super("buildmode", Category.PLAYER, KeybindSetting.UNBOUND);

		// Asked every tick the attack key is down, before the game attacks or keeps breaking.
		ClientPreAttackCallback.EVENT.register((minecraft, player, clicks) ->
			isEnabled() && mode().attack(client(), clicks > 0));

		// FAIL, not SUCCESS: nothing goes to the server, so it neither places nor uses anything.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() || !isEnabled() || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}

			return mode().click(client(), Corner.SECOND) ? InteractionResult.FAIL : InteractionResult.PASS;
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) -> dispatcher.register(
			ClientCommands.literal("bolge")
				.executes(run(mode -> mode.show(client(), isEnabled())))
				.then(ClientCommands.literal("goster").executes(run(mode -> mode.show(client(), isEnabled()))))
				.then(ClientCommands.literal("temizle").executes(run(mode -> mode.clear(client()))))
				.then(ClientCommands.literal("kose1").executes(run(mode -> mode.markHere(client(), Corner.FIRST))))
				.then(ClientCommands.literal("kose2").executes(run(mode -> mode.markHere(client(), Corner.SECOND))))));
	}

	private <S> Command<S> run(Consumer<BuildMode> action) {
		return context -> {
			action.accept(mode());
			return Command.SINGLE_SUCCESS;
		};
	}

	@Override
	protected void onTick() {
		mode().tick(client());
	}

	/** The area the block-breaking modules keep to here; none while the module is off. */
	public Optional<Area> area() {
		return isEnabled() ? mode().area(client().place()) : Optional.empty();
	}

	/**
	 * Whether this is the wand while the module is on: Oto Silah and Otomatik Alet leave it where
	 * it is. Off, it is a sword like any other.
	 */
	public boolean isWand(ItemStack stack) {
		return isEnabled() && !stack.isEmpty() && wand.get().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
	}

	/** What is marked where the player is, for the gametests. */
	public Corners cornersHere() {
		return client().place().map(mode()::corners).orElse(Corners.NONE);
	}

	public Optional<Place> place() {
		return client().place();
	}

	/** Built on first use: at construction time there is no config folder or client to ask. */
	private BuildMode mode() {
		if (buildMode == null) {
			buildMode = new BuildMode(new AreaStore(SurvivalKit.areasFile(), SurvivalKit.LOGGER::warn));
		}

		return buildMode;
	}

	private ClientBuildContext client() {
		if (client == null) {
			client = new ClientBuildContext(Minecraft.getInstance(), this::isWand);
		}

		return client;
	}
}
