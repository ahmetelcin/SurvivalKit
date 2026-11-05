package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.autoeat.EatingHand;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.util.InventoryUtil;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.AABB;

/**
 * Eating in the main hand never touches what the crosshair is on. The bite is started
 * directly rather than with the use key, because the use key goes to the target first: a
 * chest opens, a door swings, a frame turns its item.
 *
 * <p>Each target comes with its control: the same setup, the use key held for real, and the
 * target does act. So "nothing happened" is about how the bite was started, not about a
 * crosshair that was pointing at nothing.
 */
public class AutoEatHandsOffGameTest implements FabricClientGameTest {
	private static final int HUNGRY = 14;
	private static final int HELD = 0;
	private static final int FOOD_SLOT = 3;
	private static final int PATIENCE = 200;

	/** Where the player stands; everything is built relative to it. */
	private BlockPos base;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(5);
			world.getServer().runCommand("gamemode survival @a");
			base = world.getServer().computeOnServer(server -> player(server).blockPosition());
			mainHand(context);

			aChestInSightIsNotOpened(context, world);
			theUseKeyDoesOpenIt(context, world);

			aDoorInSightIsNotSwung(context, world);
			theUseKeyDoesSwingIt(context, world);

			aFrameInSightIsNotTurned(context, world);
			theUseKeyDoesTurnIt(context, world);

			clearArena(context, world);
			SurvivalKit.LOGGER.info("[gametest] ana elde yeme nişangâhtakine dokunmadı (sandık açılmadı, kapı dönmedi, "
					+ "çerçeve dönmedi); üçünde de kullanma tuşu aynı hedefte etkileşti");
		}
	}

	// --- the chest ---

	private void aChestInSightIsNotOpened(ClientGameTestContext context, TestSingleplayerContext world) {
		buildChest(context, world);

		int before = bread(world);
		eatWatching(context, world, "sandık", () -> context.computeOnClient(
				client -> client.gui.screen() instanceof ContainerScreen));

		if (bread(world) >= before) {
			throw new AssertionError("sandığa bakarken yemek yenmeliydi, ekmek " + before + " -> " + bread(world));
		}
	}

	private void theUseKeyDoesOpenIt(ClientGameTestContext context, TestSingleplayerContext world) {
		buildChest(context, world);

		holdUse(context);
		boolean opened = context.computeOnClient(client -> client.gui.screen() instanceof ContainerScreen);
		context.runOnClient(client -> client.player.closeContainer());
		context.waitTicks(5);

		if (!opened) {
			throw new AssertionError("negatif kontrol başarısız: kullanma tuşu sandığı açmalıydı");
		}
	}

	private void buildChest(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world);
		setblock(world, base.south(2), "minecraft:chest[facing=north]");
		look(context, world, 30);
	}

	// --- the door ---

	private void aDoorInSightIsNotSwung(ClientGameTestContext context, TestSingleplayerContext world) {
		buildDoor(context, world);
		boolean before = doorOpen(world);

		eatWatching(context, world, "kapı", () -> doorOpen(world) != before);
	}

	private void theUseKeyDoesSwingIt(ClientGameTestContext context, TestSingleplayerContext world) {
		buildDoor(context, world);
		boolean before = doorOpen(world);

		holdUse(context);

		if (doorOpen(world) == before) {
			throw new AssertionError("negatif kontrol başarısız: kullanma tuşu kapıyı çevirmeliydi");
		}
	}

	private void buildDoor(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world);
		setblock(world, base.south(2), "minecraft:oak_door[half=lower,facing=north]");
		setblock(world, base.south(2).above(), "minecraft:oak_door[half=upper,facing=north]");
		look(context, world, 30);
	}

	private boolean doorOpen(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server ->
				player(server).level().getBlockState(base.south(2)).getValue(DoorBlock.OPEN));
	}

	// --- the frame, which is an entity: the other half of what the key reaches ---

	private void aFrameInSightIsNotTurned(ClientGameTestContext context, TestSingleplayerContext world) {
		buildFrame(context, world);
		int before = frameRotation(world);

		eatWatching(context, world, "çerçeve", () -> frameRotation(world) != before);
	}

	private void theUseKeyDoesTurnIt(ClientGameTestContext context, TestSingleplayerContext world) {
		buildFrame(context, world);
		int before = frameRotation(world);

		holdUse(context);

		if (frameRotation(world) == before) {
			throw new AssertionError("negatif kontrol başarısız: kullanma tuşu çerçevedeki eşyayı döndürmeliydi");
		}
	}

	private void buildFrame(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world);
		BlockPos wall = base.south(3);
		setblock(world, wall, "minecraft:stone");
		BlockPos at = base.south(2);
		world.getServer().runCommand("summon minecraft:item_frame " + at.getX() + " " + at.getY() + " " + at.getZ()
				+ " {Facing:2b,Item:{id:\"minecraft:diamond\",count:1}}");
		look(context, world, 24);
	}

	private int frameRotation(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> player(server).level()
				.getEntitiesOfClass(ItemFrame.class, new AABB(base).inflate(5))
				.stream()
				.findFirst()
				.orElseThrow(() -> new AssertionError("çerçeve yok"))
				.getRotation());
	}

	// --- plumbing ---

	/**
	 * One meal with the module on, checking every tick that the target has not acted — a
	 * chest that opened and was shut again by the meal's end would not show afterwards.
	 */
	private static void eatWatching(ClientGameTestContext context, TestSingleplayerContext world, String target,
			java.util.function.BooleanSupplier acted) {
		setEnabled(context, true);

		for (int tick = 0; tick < PATIENCE; tick++) {
			if (acted.getAsBoolean()) {
				setEnabled(context, false);
				throw new AssertionError(target + " yemek sırasında etkileşti (tick " + tick + ")");
			}

			if (world.getServer().computeOnServer(server -> player(server).getFoodData().getFoodLevel() > HUNGRY)) {
				context.waitTicks(10);

				if (acted.getAsBoolean()) {
					throw new AssertionError(target + " yemekten hemen sonra etkileşti");
				}

				setEnabled(context, false);
				context.waitTicks(10);
				return;
			}

			context.waitTick();
		}

		setEnabled(context, false);
		throw new AssertionError(target + "a bakarken yemek yenmedi");
	}

	/** The use key held for real, the way a player would, for long enough to act. */
	private static void holdUse(ClientGameTestContext context) {
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(5);
		context.getInput().releaseKey(options -> options.keyUse);
		context.waitTicks(10);
	}

	/** An empty room with a floor, a sword in hand, bread on the hotbar, an empty stomach. */
	private void setUp(ClientGameTestContext context, TestSingleplayerContext world) {
		clearArena(context, world);
		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("item replace entity @a hotbar." + HELD + " with minecraft:diamond_sword");
		world.getServer().runCommand("item replace entity @a hotbar." + FOOD_SLOT + " with minecraft:bread 5");
		world.getServer().runOnServer(server -> player(server).getFoodData().setFoodLevel(HUNGRY));
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(HELD));
		context.waitTicks(5);
	}

	private void clearArena(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("kill @e[type=minecraft:item_frame]");
		world.getServer().runCommand("kill @e[type=minecraft:item]");
		fill(world, base.offset(-2, 0, -2), base.offset(2, 3, 4), "minecraft:air");
		fill(world, base.offset(-2, -1, -2), base.offset(2, -1, 4), "minecraft:stone");
		context.waitTicks(2);
	}

	private void look(ClientGameTestContext context, TestSingleplayerContext world, int pitch) {
		world.getServer().runCommand("tp @a " + (base.getX() + 0.5) + " " + base.getY() + " " + (base.getZ() + 0.5)
				+ " 0 " + pitch);
		context.waitTicks(10);
	}

	private static void setblock(TestSingleplayerContext world, BlockPos pos, String block) {
		world.getServer().runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block);
	}

	private static void fill(TestSingleplayerContext world, BlockPos from, BlockPos to, String block) {
		world.getServer().runCommand("fill " + from.getX() + " " + from.getY() + " " + from.getZ() + " "
				+ to.getX() + " " + to.getY() + " " + to.getZ() + " " + block);
	}

	@SuppressWarnings("unchecked")
	private static void mainHand(ClientGameTestContext context) {
		context.runOnClient(client -> {
			autoEat().settings().<EnumSetting<EatingHand>>byId("eating_hand").orElseThrow().set(EatingHand.MAIN_HAND);
			autoEat().settings().<EnumSetting<InventoryUtil.Scope>>byId("scope").orElseThrow().set(InventoryUtil.Scope.HOTBAR);
		});
	}

	private static int bread(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> player(server).getInventory().getNonEquipmentItems()
				.stream()
				.filter(stack -> stack.is(Items.BREAD))
				.mapToInt(ItemStack::getCount)
				.sum());
	}

	private static void setEnabled(ClientGameTestContext context, boolean enabled) {
		context.runOnClient(client -> autoEat().setEnabled(enabled));
	}

	private static Module autoEat() {
		return SurvivalKit.modules().byId("autoeat")
				.orElseThrow(() -> new AssertionError("autoeat modülü kayıtlı değil"));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}
}
