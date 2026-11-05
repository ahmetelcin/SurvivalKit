package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.impl.ScaffoldModule;
import com.merci.survivalkit.scaffold.ClientScaffoldContext;
import com.merci.survivalkit.settings.Setting;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.CuboidGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * İskele's stage: a stone walkway three blocks long running south, nothing past it, and a catch
 * floor {@link #DROP} blocks below so a fall is a fall and not a death. The player stands at the
 * north end looking south and walks with the real forward key.
 */
final class ScaffoldTestSupport {
	/** How far below the walkway the catch floor is: a fall of this much does no harm. */
	static final int DROP = 3;
	/** How many blocks of walkway there are before the gap. */
	static final int WALKWAY = 3;

	/** The first walkway block; the player's feet are one above it. */
	final BlockPos start;
	private final ClientGameTestContext context;
	private final TestSingleplayerContext world;

	ScaffoldTestSupport(ClientGameTestContext context, TestSingleplayerContext world) {
		this.context = context;
		this.world = world;

		context.waitTicks(5);
		BlockPos feet = context.computeOnClient(client -> client.player.blockPosition());
		this.start = new BlockPos(feet.getX(), feet.getY() + 30, feet.getZ());

		world.getServer().runOnServer(server -> ((IntegratedServer) server).setWorldAllowCommands(true));
		context.waitTicks(5);
		command("gamemode survival @a");
		command("effect give @a minecraft:saturation 600 0 true");
		command("effect give @a minecraft:resistance 600 4 true");
		command("effect give @a minecraft:fire_resistance 600 0 true");
		context.runOnClient(client -> client.options.autoJump().set(false));
	}

	/**
	 * Air all round, the walkway and the catch floor back, an empty inventory with a pickaxe in
	 * slot 0 (in hand) and {@code blocks} where asked; the player at the north end facing south.
	 */
	void stage(String blocksSlot, String blocks) {
		int x = start.getX();
		int y = start.getY();
		int z = start.getZ();
		command("fill " + (x - 4) + " " + (y - DROP - 1) + " " + (z - 2) + " " + (x + 4) + " " + (y + 4) + " " + (z + 14) + " minecraft:air");
		command("fill " + (x - 4) + " " + (y - DROP) + " " + (z - 2) + " " + (x + 4) + " " + (y - DROP) + " " + (z + 14) + " minecraft:glass");
		command("fill " + x + " " + y + " " + z + " " + x + " " + y + " " + (z + WALKWAY - 1) + " minecraft:stone");
		command("kill @e[type=minecraft:item]");
		command("clear @a");
		command("item replace entity @a hotbar.0 with minecraft:stone_pickaxe");

		if (blocks != null) {
			command("item replace entity @a " + blocksSlot + " with " + blocks);
		}

		command("tp @a " + (x + 0.5) + " " + (y + 1) + " " + (z + 0.5) + " 0 0");
		context.waitTicks(5);
		context.runOnClient(client -> {
			client.player.getInventory().setSelectedSlot(0);
			client.player.setYRot(0.0f);
			client.player.setXRot(0.0f);
		});
		context.waitTicks(5);
	}

	/** The block past the walkway's end, {@code n} = 0 being the first one over the gap. */
	BlockPos gap(int n) {
		return start.south(WALKWAY + n);
	}

	/**
	 * Holds the real forward key for {@code ticks} and lets go; the lowest the feet got, as the
	 * client saw them each tick.
	 */
	double walk(int ticks) {
		double lowest = feetY();
		context.getInput().holdKey(options -> options.keyUp);

		try {
			for (int tick = 0; tick < ticks; tick++) {
				context.waitTick();
				lowest = Math.min(lowest, feetY());
			}
		} finally {
			context.getInput().releaseKey(options -> options.keyUp);
		}

		context.waitTicks(5);
		return Math.min(lowest, feetY());
	}

	/**
	 * Holds the real forward key until the feet are past {@code z}, and lets go at once; false if
	 * they never got there in {@code ticks}.
	 */
	boolean walkUntil(double z, int ticks) {
		context.getInput().holdKey(options -> options.keyUp);

		try {
			for (int tick = 0; tick < ticks; tick++) {
				context.waitTick();

				if (feetZ() >= z) {
					return true;
				}
			}

			return false;
		} finally {
			context.getInput().releaseKey(options -> options.keyUp);
		}
	}

	/**
	 * Holds the real jump key for {@code ticks} and lets go, then waits for the player to land;
	 * the highest the feet got.
	 */
	double jump(int ticks) {
		double highest = feetY();
		context.getInput().holdKey(options -> options.keyJump);

		try {
			for (int tick = 0; tick < ticks; tick++) {
				context.waitTick();
				highest = Math.max(highest, feetY());
			}
		} finally {
			context.getInput().releaseKey(options -> options.keyJump);
		}

		for (int tick = 0; tick < 40; tick++) {
			context.waitTick();
			highest = Math.max(highest, feetY());
		}

		return highest;
	}

	/** Teleports the player, looking south, and waits for the client to be there. */
	void tp(double x, double y, double z) {
		command("tp @a " + x + " " + y + " " + z + " 0 0");
		context.waitTicks(5);
	}

	void waitTicks(int ticks) {
		context.waitTicks(ticks);
	}

	boolean sneakDown() {
		return context.computeOnClient(client -> client.options.keyShift.isDown());
	}

	boolean inLava() {
		return context.computeOnClient(client -> client.player.isInLava());
	}

	boolean onGround() {
		return context.computeOnClient(client -> client.player.onGround());
	}

	/** The block at {@code pos} as the client has it right now, prediction included. */
	String clientBlock(BlockPos pos) {
		return context.computeOnClient(client -> BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(pos).getBlock()).toString());
	}

	double feetY() {
		return context.computeOnClient(client -> client.player.getY());
	}

	double feetZ() {
		return context.computeOnClient(client -> client.player.getZ());
	}

	int selectedSlot() {
		return context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
	}

	/** The item in an inventory slot, as the server has it. */
	String serverItem(int slot) {
		return world.getServer().computeOnServer(server -> BuiltInRegistries.ITEM.getKey(
			server.getPlayerList().getPlayers().getFirst().getInventory().getItem(slot).getItem()).toString());
	}

	int serverCount(int slot) {
		return world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst().getInventory().getItem(slot).getCount());
	}

	/** The block at {@code pos} as the server has it. */
	String block(BlockPos pos) {
		return world.getServer().computeOnServer(server -> BuiltInRegistries.BLOCK.getKey(server.overworld().getBlockState(pos).getBlock()).toString());
	}

	/** What the action bar says right now, "" with nothing on it. */
	String actionBar() {
		return context.computeOnClient(client -> {
			Object shown = overlay(client.gui.hud);
			return shown instanceof Component text ? text.getString() : "";
		});
	}

	/** The action bar's text has no getter; the field is read directly. */
	private static Object overlay(Hud hud) {
		try {
			Field field = Hud.class.getDeclaredField("overlayMessageString");
			field.setAccessible(true);
			return field.get(hud);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("eylem çubuğu alanı bulunamadı", e);
		}
	}

	/** The green boxes the last client tick left to draw, as the blocks they frame. */
	Set<BlockPos> outlined() {
		return context.computeOnClient(client -> client.getPerTickGizmos().stream()
			.map(instance -> instance.gizmo())
			.filter(gizmo -> gizmo instanceof CuboidGizmo cuboid && cuboid.style().stroke() == ClientScaffoldContext.TARGET_COLOUR)
			.map(gizmo -> BlockPos.containing(((CuboidGizmo) gizmo).aabb().getCenter()))
			.collect(Collectors.toSet()));
	}

	void put(BlockPos pos, String block) {
		command("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block);
	}

	@SuppressWarnings("unchecked")
	<T> void set(String id, T value) {
		context.runOnClient(client -> module().settings().<Setting<T>>byId(id)
			.orElseThrow(() -> new AssertionError("ayar yok: " + id))
			.set(value));
	}

	void enable(boolean on) {
		context.runOnClient(client -> module().setEnabled(on));
		context.waitTicks(2);
	}

	void command(String command) {
		world.getServer().runCommand(command);
	}

	<T> T computeOnServer(Function<MinecraftServer, T> read) {
		return world.getServer().computeOnServer(read::apply);
	}

	/** Which of the first {@code count} gap blocks hold something, as the server has them. */
	List<String> gapBlocks(int count) {
		return java.util.stream.IntStream.range(0, count).mapToObj(n -> block(gap(n))).toList();
	}

	static ScaffoldModule module() {
		return (ScaffoldModule) SurvivalKit.modules().byId("scaffold")
			.orElseThrow(() -> new AssertionError("scaffold modülü kayıtlı değil"));
	}
}
