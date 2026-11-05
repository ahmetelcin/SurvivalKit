package com.merci.survivalkit.wdl;

import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Scrapes container contents when a container screen is open and saves them into the
 * world downloader's block entity overlay.
 */
public final class ContainerCapture {
	private final WorldDownloaderModule module;
	private BlockPos lastInteractedPos;

	public ContainerCapture(WorldDownloaderModule module) {
		this.module = module;

		UseBlockCallback.EVENT.register(this::onUseBlock);
		ScreenEvents.AFTER_INIT.register(this::onScreenInit);
	}

	private InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
		if (module.isEnabled() && level.isClientSide()) {
			lastInteractedPos = hitResult.getBlockPos();
		}
		return InteractionResult.PASS;
	}

	private void onScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
		if (!module.isEnabled()) {
			return;
		}

		Optional<DownloadSession> optSession = module.session();
		if (optSession.isEmpty()) {
			return;
		}

		if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
			return;
		}

		if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
			return;
		}

		ClientLevel level = client.level;
		if (level == null) {
			return;
		}

		if (isEnderChestScreen(containerScreen, client, level)) {
			lastInteractedPos = null;
			ScreenEvents.afterTick(containerScreen).register(s -> captureEnderChest(containerScreen, client));
			ScreenEvents.remove(containerScreen).register(s -> captureEnderChest(containerScreen, client));
			return;
		}

		BlockPos targetPos = resolveTargetPos(client, level);
		if (targetPos == null) {
			return;
		}

		BlockState state = level.getBlockState(targetPos);
		boolean isChest = state.getBlock() instanceof ChestBlock;
		ChestType chestType = isChest ? state.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
		boolean isDoubleChest = isChest && chestType != ChestType.SINGLE;

		BlockPos rightPos;
		BlockPos leftPos;
		if (isDoubleChest) {
			BlockPos connected = ChestBlock.getConnectedBlockPos(targetPos, state);
			boolean clickedIsRight = (chestType == ChestType.RIGHT);
			DoubleChestSplitter.SplitPositions<BlockPos> positions =
					DoubleChestSplitter.resolvePositions(targetPos, connected, clickedIsRight);
			rightPos = positions.rightPos();
			leftPos = positions.leftPos();
		} else {
			rightPos = targetPos;
			leftPos = null;
		}

		ScreenEvents.afterTick(containerScreen).register(s ->
				capture(containerScreen, level, isDoubleChest, rightPos, leftPos));

		ScreenEvents.remove(containerScreen).register(s -> {
			capture(containerScreen, level, isDoubleChest, rightPos, leftPos);
			saveTargetChunks(level, rightPos, leftPos);
		});
	}

	private BlockPos resolveTargetPos(Minecraft client, ClientLevel level) {
		BlockPos candidate = lastInteractedPos;
		lastInteractedPos = null;

		if (candidate != null && isContainerAt(level, candidate)) {
			return candidate;
		}

		if (client.hitResult instanceof BlockHitResult hitResult) {
			BlockPos hitPos = hitResult.getBlockPos();
			if (isContainerAt(level, hitPos)) {
				return hitPos;
			}
		}

		return null;
	}

	public static boolean isContainerAt(ClientLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock
				|| state.getBlock() instanceof BarrelBlock
				|| state.getBlock() instanceof ShulkerBoxBlock
				|| state.getBlock() instanceof AbstractFurnaceBlock
				|| state.getBlock() instanceof HopperBlock
				|| state.getBlock() instanceof DispenserBlock
				|| state.getBlock() instanceof BrewingStandBlock
				|| state.getBlock() instanceof CrafterBlock) {
			return true;
		}

		BlockEntity entity = level.getBlockEntity(pos);
		return entity instanceof Container;
	}

	private void capture(AbstractContainerScreen<?> screen, ClientLevel level,
						 boolean isDoubleChest, BlockPos rightPos, BlockPos leftPos) {
		Optional<DownloadSession> optSession = module.session();
		if (optSession.isEmpty()) {
			return;
		}
		DownloadSession session = optSession.get();

		AbstractContainerMenu menu = screen.getMenu();
		List<ItemStack> items = extractContainerItems(menu);
		if (items.isEmpty()) {
			return;
		}

		HolderLookup.Provider registries = level.registryAccess();

		if (isDoubleChest && items.size() == DoubleChestSplitter.FULL_SIZE) {
			DoubleChestSplitter.SplitResult<ItemStack> split = DoubleChestSplitter.splitItems(items);
			CompoundTag rightTag = serializeItems(split.rightHalf(), registries);
			CompoundTag leftTag = serializeItems(split.leftHalf(), registries);
			session.overlay().capture(level.dimension(), rightPos, rightTag);
			if (leftPos != null) {
				session.overlay().capture(level.dimension(), leftPos, leftTag);
			}
		} else {
			CompoundTag tag = serializeItems(items, registries);
			session.overlay().capture(level.dimension(), rightPos, tag);
		}
	}

	public static List<ItemStack> extractContainerItems(AbstractContainerMenu menu) {
		int maxSlot = -1;
		for (Slot slot : menu.slots) {
			if (!(slot.container instanceof Inventory)) {
				maxSlot = Math.max(maxSlot, slot.getContainerSlot());
			}
		}

		if (maxSlot < 0) {
			return List.of();
		}

		NonNullList<ItemStack> items = NonNullList.withSize(maxSlot + 1, ItemStack.EMPTY);
		for (Slot slot : menu.slots) {
			if (!(slot.container instanceof Inventory)) {
				int idx = slot.getContainerSlot();
				if (idx >= 0 && idx < items.size()) {
					items.set(idx, slot.getItem().copy());
				}
			}
		}

		return items;
	}

	public static CompoundTag serializeItems(List<ItemStack> items, HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		NonNullList<ItemStack> nonNullList = NonNullList.withSize(items.size(), ItemStack.EMPTY);
		for (int i = 0; i < items.size(); i++) {
			nonNullList.set(i, items.get(i));
		}
		ContainerHelper.saveAllItems(output, nonNullList);
		return output.buildResult();
	}

	private void saveTargetChunks(ClientLevel level, BlockPos rightPos, BlockPos leftPos) {
		Optional<DownloadSession> optSession = module.session();
		if (optSession.isEmpty()) {
			return;
		}
		DownloadSession session = optSession.get();

		LevelChunk rightChunk = level.getChunkSource().getChunk(rightPos.getX() >> 4, rightPos.getZ() >> 4, false);
		if (rightChunk != null) {
			session.saveChunk(level, rightChunk);
		}

		if (leftPos != null) {
			int leftChunkX = leftPos.getX() >> 4;
			int leftChunkZ = leftPos.getZ() >> 4;
			if (leftChunkX != (rightPos.getX() >> 4) || leftChunkZ != (rightPos.getZ() >> 4)) {
				LevelChunk leftChunk = level.getChunkSource().getChunk(leftChunkX, leftChunkZ, false);
				if (leftChunk != null) {
					session.saveChunk(level, leftChunk);
				}
			}
		}
	}

	private boolean isEnderChestScreen(AbstractContainerScreen<?> screen, Minecraft client, ClientLevel level) {
		if (screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("container.enderchest").getString())) {
			return true;
		}
		if (lastInteractedPos != null && level.getBlockState(lastInteractedPos).getBlock() instanceof net.minecraft.world.level.block.EnderChestBlock) {
			return true;
		}
		if (client.hitResult instanceof BlockHitResult hitResult && level.getBlockState(hitResult.getBlockPos()).getBlock() instanceof net.minecraft.world.level.block.EnderChestBlock) {
			return true;
		}
		return false;
	}

	private void captureEnderChest(AbstractContainerScreen<?> screen, Minecraft client) {
		Optional<DownloadSession> optSession = module.session();
		if (optSession.isEmpty()) {
			return;
		}

		AbstractContainerMenu menu = screen.getMenu();
		NonNullList<ItemStack> enderItems = NonNullList.withSize(27, ItemStack.EMPTY);
		for (int i = 0; i < 27 && i < menu.slots.size(); i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			if (!stack.isEmpty()) {
				enderItems.set(i, stack.copy());
			}
		}

		optSession.get().setEnderChestItems(enderItems);

		if (client.player != null) {
			for (int i = 0; i < 27; i++) {
				client.player.getEnderChestInventory().setItem(i, enderItems.get(i).copy());
			}
		}
	}
}
