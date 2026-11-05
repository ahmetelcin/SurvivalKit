package com.merci.survivalkit.vein;

import com.merci.survivalkit.area.Area;
import com.merci.survivalkit.client.ModuleKeys;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Wires {@link VeinBreaker} to the running client: reads blocks, keys and the command tree; sends; draws. */
public final class ClientVeinContext implements VeinContext {
	/** Cyan, fully opaque: apart from vanilla's black outline and readable on stone and bark alike. */
	public static final int OUTLINE_COLOUR = 0xFF2FD8F0;

	private final Minecraft minecraft;
	private final Supplier<String> triggerKey;
	private final Supplier<Optional<Area>> area;
	/** Every command that went out, in order; the gametests read this to time them. */
	private final List<String> sent = new ArrayList<>();
	private String keyName;
	private InputConstants.Key key = InputConstants.UNKNOWN;

	public ClientVeinContext(Minecraft minecraft, Supplier<String> triggerKey, Supplier<Optional<Area>> area) {
		this.minecraft = minecraft;
		this.triggerKey = triggerKey;
		this.area = area;
	}

	/** The block the player just broke, as {@link VeinBreaker} wants it. */
	public static VeinBreaker.Origin broken(BlockPos pos, BlockState state) {
		return new VeinBreaker.Origin(pos(pos), BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), state.is(BlockTags.LOGS), true);
	}

	@Override
	public boolean ready() {
		return minecraft.player != null && minecraft.level != null && minecraft.getConnection() != null;
	}

	@Override
	public Optional<Pos> target() {
		if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			return Optional.of(pos(hit.getBlockPos()));
		}

		return Optional.empty();
	}

	@Override
	public String blockAt(Pos pos) {
		return BuiltInRegistries.BLOCK.getKey(state(pos).getBlock()).toString();
	}

	@Override
	public boolean isLog(Pos pos) {
		return state(pos).is(BlockTags.LOGS);
	}

	@Override
	public boolean isNaturalFoliage(Pos pos) {
		BlockState state = state(pos);

		if (state.is(BlockTags.LEAVES)) {
			return state.hasProperty(BlockStateProperties.PERSISTENT) && !state.getValue(BlockStateProperties.PERSISTENT);
		}

		return state.is(BlockTags.WART_BLOCKS);
	}

	/** The tree the server sent decides: a command the player may not run is not in it. */
	@Override
	public boolean canRun(String command) {
		ClientPacketListener connection = minecraft.getConnection();
		return connection != null && connection.getCommands().getRoot().getChild(command) != null;
	}

	@Override
	public boolean triggerHeld() {
		String name = triggerKey.get();

		if (!name.equals(keyName)) {
			keyName = name;
			key = ModuleKeys.parse(name);
		}

		return minecraft.gui.screen() == null && ModuleKeys.isDown(key);
	}

	@Override
	public boolean holdsSilkTouch() {
		return levelInHand(Enchantments.SILK_TOUCH) > 0;
	}

	@Override
	public int fortuneLevel() {
		return levelInHand(Enchantments.FORTUNE);
	}

	/** A tool by its tool component, as the game's own block breaking sees it; nothing that cannot wear. */
	@Override
	public Optional<HeldTool> heldTool() {
		ItemStack stack = minecraft.player.getMainHandItem();
		Tool tool = stack.get(DataComponents.TOOL);

		if (tool == null || tool.damagePerBlock() <= 0 || !stack.isDamageableItem()) {
			return Optional.empty();
		}

		return Optional.of(new HeldTool(minecraft.player.getInventory().getSelectedSlot(), stack.getDamageValue(), stack.getMaxDamage(),
			tool.damagePerBlock(), levelInHand(Enchantments.UNBREAKING)));
	}

	private int levelInHand(ResourceKey<Enchantment> key) {
		Holder<Enchantment> enchantment = minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
		return EnchantmentHelper.getItemEnchantmentLevel(enchantment, minecraft.player.getMainHandItem());
	}

	@Override
	public Optional<Area> area() {
		return area.get();
	}

	@Override
	public int foodLevel() {
		return minecraft.player.getFoodData().getFoodLevel();
	}

	@Override
	public float saturation() {
		return minecraft.player.getFoodData().getSaturationLevel();
	}

	@Override
	public void send(String command) {
		sent.add(command);
		minecraft.getConnection().sendCommand(command);
	}

	@Override
	public void say(String message) {
		minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
	}

	/**
	 * Vanilla's gizmos: called during the client tick, they are drawn until the next one, so
	 * one call a tick keeps the outline steady. A hair larger than the block, so it is not
	 * swallowed by the block's own faces.
	 */
	@Override
	public void outline(List<Pos> blocks) {
		for (Pos pos : blocks) {
			Gizmos.cuboid(new AABB(new BlockPos(pos.x(), pos.y(), pos.z())).inflate(0.004), GizmoStyle.stroke(OUTLINE_COLOUR, 2.0f));
		}
	}

	public List<String> sent() {
		return List.copyOf(sent);
	}

	private static Pos pos(BlockPos pos) {
		return new Pos(pos.getX(), pos.getY(), pos.getZ());
	}

	private BlockState state(Pos pos) {
		return minecraft.level.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z()));
	}
}
