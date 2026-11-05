package com.merci.survivalkit.scaffold;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.area.Area;
import com.merci.survivalkit.client.ModuleKeys;
import com.merci.survivalkit.util.InventoryUtil;
import com.merci.survivalkit.vein.Pos;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Wires {@link Scaffold} to the running client: reads the world and the inventory, and clicks. */
public final class ClientScaffoldContext implements ScaffoldContext {
	/** Green, fully opaque: the next block. Apart from Yapı Modu's amber and Damar Kırıcı's cyan. */
	public static final int TARGET_COLOUR = 0xFF4FD86A;

	private final Minecraft minecraft;
	private final Supplier<InventoryUtil.Scope> scope;
	private final Supplier<Optional<Area>> area;
	private final Supplier<String> lockKey;
	/** The lock key's saved name and what it parsed to, parsed again only when it changes. */
	private String lockKeyName;
	private InputConstants.Key lockKeyParsed = InputConstants.UNKNOWN;

	public ClientScaffoldContext(Minecraft minecraft, Supplier<InventoryUtil.Scope> scope, Supplier<String> lockKey,
		Supplier<Optional<Area>> area) {
		this.minecraft = minecraft;
		this.scope = scope;
		this.lockKey = lockKey;
		this.area = area;
	}

	@Override
	public boolean ready() {
		return minecraft.player != null && minecraft.level != null && minecraft.gameMode != null;
	}

	@Override
	public boolean screenOpen() {
		return minecraft.gui.screen() != null;
	}

	@Override
	public Vec feet() {
		return vec(minecraft.player.position());
	}

	@Override
	public Vec motion() {
		return vec(minecraft.player.getDeltaMovement());
	}

	@Override
	public boolean onGround() {
		return minecraft.player.onGround();
	}

	@Override
	public boolean jumpHeld() {
		return minecraft.options.keyJump.isDown();
	}

	/** The physical key, not {@code keyShift.isDown()}: that one is also down while SafeWalk holds it. */
	@Override
	public boolean sneakKeyHeld() {
		return minecraft.gui.screen() == null && physicallyHeld(minecraft.options.keyShift);
	}

	@Override
	public boolean lockKeyHeld() {
		String name = lockKey.get();

		if (!name.equals(lockKeyName)) {
			lockKeyName = name;
			lockKeyParsed = ModuleKeys.parse(name);
		}

		return minecraft.gui.screen() == null && ModuleKeys.isDown(lockKeyParsed);
	}

	@Override
	public double fallDistance() {
		return minecraft.player.fallDistance;
	}

	@Override
	public boolean inLava() {
		return minecraft.player.isInLava();
	}

	/** The movement keys' direction turned by the view's yaw, the way {@code Entity.getInputVector} turns it. */
	@Override
	public Vec heading() {
		Vec2 keys = minecraft.player.input.getMoveVector();
		double length = Math.hypot(keys.x, keys.y);

		if (length < 1.0E-5) {
			return new Vec(0, 0, 0);
		}

		double yaw = Math.toRadians(minecraft.player.getYRot());
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		double x = (keys.x * cos - keys.y * sin) / length;
		double z = (keys.y * cos + keys.x * sin) / length;
		return new Vec(x, 0, z);
	}

	/**
	 * The sneak key, set the way a key press sets it. Let go, it goes back to what the physical key
	 * says, so a player holding shift keeps sneaking. With "toggle sneak" a press flips it instead.
	 */
	@Override
	public void sneak(boolean down) {
		KeyMapping key = minecraft.options.keyShift;
		boolean wanted = down || physicallyHeld(key);

		if (minecraft.options.toggleCrouch().get()) {
			if (key.isDown() != wanted) {
				key.setDown(true);
			}
		} else {
			key.setDown(wanted);
		}
	}

	private boolean physicallyHeld(KeyMapping mapping) {
		InputConstants.Key key = InputConstants.getKey(mapping.saveString());
		return key.getType() == InputConstants.Type.KEYBOARD && key.getValue() != -1
			&& InputConstants.isKeyDown(key.getValue());
	}

	@Override
	public Optional<Area> area() {
		return area.get();
	}

	@Override
	public Vec eyes() {
		return vec(minecraft.player.getEyePosition());
	}

	@Override
	public double reach() {
		return minecraft.player.blockInteractionRange();
	}

	@Override
	public Gap gap(Pos pos) {
		BlockState state = minecraft.level.getBlockState(blockPos(pos));

		if (!state.canBeReplaced()) {
			return Gap.NONE;
		}

		if (state.getFluidState().is(FluidTags.LAVA)) {
			return Gap.LAVA;
		}

		return state.getFluidState().is(FluidTags.WATER) ? Gap.WATER : Gap.AIR;
	}

	@Override
	public Optional<String> clickable(Pos pos) {
		BlockPos at = blockPos(pos);
		BlockState state = minecraft.level.getBlockState(at);

		if (state.isAir() || state.canBeReplaced() || state.getShape(minecraft.level, at).isEmpty() || usedWhenClicked(state, at)) {
			return Optional.empty();
		}

		return Optional.of(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
	}

	/**
	 * Blocks that do something of their own when used with a block in hand — open, toggle, eat —
	 * instead of having it put down beside them.
	 */
	private boolean usedWhenClicked(BlockState state, BlockPos at) {
		Block block = state.getBlock();
		return state.hasBlockEntity() || state.getMenuProvider(minecraft.level, at) != null
			|| block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
			|| block instanceof ButtonBlock || block instanceof LeverBlock || block instanceof NoteBlock
			|| block instanceof DiodeBlock || block instanceof CakeBlock || block instanceof BedBlock
			|| block instanceof ComposterBlock || block instanceof DaylightDetectorBlock || block instanceof RespawnAnchorBlock;
	}

	@Override
	public List<BlockStack> blocks() {
		Inventory inventory = minecraft.player.getInventory();
		int limit = scope.get() == InventoryUtil.Scope.HOTBAR ? Inventory.getSelectionSize() : Inventory.INVENTORY_SIZE;
		List<BlockStack> stacks = new ArrayList<>();

		for (int slot = 0; slot < limit; slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!stack.isEmpty()) {
				stacks.add(new BlockStack(slot, InventoryUtil.idOf(stack), stack.getCount(), stack.getMaxStackSize(), wholeBlock(stack)));
			}
		}

		return stacks;
	}

	/** A block that goes down as a whole cube to stand on; never another module's item. */
	private static boolean wholeBlock(ItemStack stack) {
		return stack.getItem() instanceof BlockItem item && !SurvivalKit.reserved(stack)
			&& item.getBlock().defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	@Override
	public int selectedSlot() {
		return minecraft.player.getInventory().getSelectedSlot();
	}

	@Override
	public void selectSlot(int slot) {
		if (minecraft.player != null) {
			minecraft.player.getInventory().setSelectedSlot(slot);
		}
	}

	@Override
	public void swap(int inventorySlot, int hotbarSlot) {
		InventoryUtil.swap(minecraft, inventorySlot, hotbarSlot);
	}

	@Override
	public void actionBar(String text, boolean warning) {
		minecraft.gui.hud.setOverlayMessage(Component.literal(text).withStyle(warning ? ChatFormatting.RED : ChatFormatting.WHITE), false);
	}

	/** Vanilla's gizmos, like Yapı Modu's frame: called during the client tick, drawn until the next. */
	@Override
	public void outline(Pos pos) {
		Gizmos.cuboid(new AABB(blockPos(pos)).inflate(0.004), GizmoStyle.stroke(TARGET_COLOUR, 2.0f));
	}

	/** What a right click on that face does in {@code Minecraft.startUseItem}, swing included. */
	@Override
	public boolean place(Placement placement) {
		BlockHitResult hit = new BlockHitResult(
			new Vec3(placement.hit().x(), placement.hit().y(), placement.hit().z()),
			direction(placement.face()), blockPos(placement.support()), false);
		InteractionResult result = minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);

		if (result instanceof InteractionResult.Success success) {
			if (success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
				minecraft.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
			}

			return true;
		}

		return false;
	}

	private static Direction direction(Face face) {
		return switch (face) {
			case DOWN -> Direction.DOWN;
			case UP -> Direction.UP;
			case NORTH -> Direction.NORTH;
			case SOUTH -> Direction.SOUTH;
			case WEST -> Direction.WEST;
			case EAST -> Direction.EAST;
		};
	}

	private static BlockPos blockPos(Pos pos) {
		return new BlockPos(pos.x(), pos.y(), pos.z());
	}

	private static Vec vec(Vec3 vec) {
		return new Vec(vec.x(), vec.y(), vec.z());
	}
}
