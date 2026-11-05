package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.impl.VeinMinerModule;
import com.merci.survivalkit.settings.Setting;
import com.merci.survivalkit.vein.ClientVeinContext;
import com.merci.survivalkit.vein.Trigger;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.tree.CommandNode;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.CuboidGizmo;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Damar Kırıcı's gametests share a stage: an empty box of air high above the spawn, a block
 * for the player to stand on three blocks north of the work, and the player looking south
 * straight at {@link #target}. The module is driven by its real key.
 */
final class VeinTestSupport {
	static final String KEY = "key.keyboard.j";

	/** How long a job may take; the slowest one here is about two seconds. */
	private static final int PATIENCE_TICKS = 600;

	private static final Set<GuiMessage> CHAT = new LinkedHashSet<>();

	/** The corner the scenes are built around; the player's eyes are level with {@code base.above()}. */
	final BlockPos base;
	private final ClientGameTestContext context;
	private final TestSingleplayerContext world;

	VeinTestSupport(ClientGameTestContext context, TestSingleplayerContext world) {
		this.context = context;
		this.world = world;

		context.waitTicks(5);
		BlockPos feet = context.computeOnClient(client -> client.player.blockPosition());
		this.base = new BlockPos(feet.getX(), feet.getY() + 30, feet.getZ());

		// The test world is made with cheats off, which is a player with no /setblock or /loot:
		// switched on the way a world's "allow cheats" is, so the server sends the commands.
		allowCommands(true);
		command("gamemode survival @a");
		command("effect give @a minecraft:saturation 600 0 true");
		// Most scenes press once while looking, which needs no digging; the one that digs
		// switches the mode for itself.
		set("trigger_key", KEY);
		set("mode", Trigger.LOOK_AND_PRESS);
		// The hunger cost has a test of its own (VeinMinerHungerGameTest); the other scenes count
		// commands and time jobs, and an /effect or a wait for the drain is not what they measure.
		set("blocks_per_hunger_point", 0);
		// Tool wear too (VeinMinerWearGameTest): it sends an /item first, which the counts would see.
		set("tool_wear", false);
		context.runOnClient(client -> module().setEnabled(true));
		context.waitTicks(2);
	}

	/**
	 * Takes a command out of the tree the client was sent — what a server that withholds it
	 * (/loot on some servers) sends. Only the client's copy changes: the point is what the module
	 * chooses to send when the tree lacks it. Brigadier has no remove, hence the reflection.
	 */
	void dropFromCommandTree(String name) {
		context.runOnClient(client -> {
			CommandNode<?> root = client.getConnection().getCommands().getRoot();

			try {
				for (String field : List.of("children", "literals")) {
					Field map = CommandNode.class.getDeclaredField(field);
					map.setAccessible(true);
					((Map<?, ?>) map.get(root)).remove(name);
				}
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("komut ağacından " + name + " çıkarılamadı", e);
			}

			if (root.getChild(name) != null) {
				throw new AssertionError("komut ağacında " + name + " hâlâ var");
			}
		});
	}

	/** The server sends the player's whole command tree again, undoing {@link #dropFromCommandTree}. */
	void resendCommandTree(String expected) {
		world.getServer().runOnServer(server -> server.getCommands().sendCommands(server.getPlayerList().getPlayers().getFirst()));

		for (int tick = 0; tick < 100; tick++) {
			if (context.computeOnClient(client -> client.getConnection().getCommands().getRoot().getChild(expected) != null)) {
				return;
			}

			context.waitTick();
		}

		throw new AssertionError("komut ağacı " + expected + " ile geri gelmedi");
	}

	/** The integrated server's "allow cheats"; the server sends a new command tree either way. */
	void allowCommands(boolean allow) {
		world.getServer().runOnServer(server -> ((IntegratedServer) server).setWorldAllowCommands(allow));

		for (int tick = 0; tick < 100; tick++) {
			boolean has = context.computeOnClient(client -> client.getConnection().getCommands().getRoot().getChild("setblock") != null);

			if (has == allow) {
				return;
			}

			context.waitTick();
		}

		throw new AssertionError("komut ağacı " + (allow ? "/setblock'u almadı" : "/setblock'u düşürmedi"));
	}

	/** Air all round, no loose items, the platform back, an empty inventory with {@code tool} in hand. */
	void clear(String tool) {
		int x = base.getX();
		int y = base.getY();
		int z = base.getZ();
		command("fill " + (x - 7) + " " + (y - 3) + " " + (z - 7) + " " + (x + 7) + " " + (y + 10) + " " + (z + 7) + " minecraft:air");
		command("kill @e[type=minecraft:item]");
		command("kill @e[type=minecraft:experience_orb]");
		command("clear @a");
		command("item replace entity @a hotbar.0 with " + tool);
		context.waitTicks(3);
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
	}

	/** Every slot but the one in hand (and armour and off hand) filled with {@code stack}. */
	void fillInventoryExceptHand(String stack) {
		for (int slot = 1; slot < 9; slot++) {
			command("item replace entity @a hotbar." + slot + " with " + stack);
		}

		for (int slot = 0; slot < 27; slot++) {
			command("item replace entity @a inventory." + slot + " with " + stack);
		}

		context.waitTicks(3);
	}

	/** Stands on a block three north of {@code x, z}, looking south at the block one above {@link #base}'s level. */
	void standFacing(int x, int z) {
		int y = base.getY();
		command("setblock " + x + " " + (y - 1) + " " + (z - 3) + " minecraft:stone");
		command("tp @a " + (x + 0.5) + " " + y + " " + (z - 3 + 0.5) + " 0 0");
		context.waitTicks(5);
		context.runOnClient(client -> {
			client.player.setYRot(0.0f);
			client.player.setXRot(0.0f);
		});
		context.waitTicks(3);
	}

	/** The block the player looks at after {@link #standFacing}. */
	BlockPos target(int x, int z) {
		return new BlockPos(x, base.getY() + 1, z);
	}

	void put(BlockPos pos, String block) {
		command("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block);
	}

	/**
	 * A tree: a six-log oak trunk from the base level at {@code x, z}, a seventh log off its top
	 * corner, and grown (non-persistent) leaves round the top that touch only the tree.
	 *
	 * @return the logs
	 */
	List<BlockPos> tree(int x, int z) {
		int y = base.getY();
		List<BlockPos> logs = new ArrayList<>();

		for (int i = 0; i < 6; i++) {
			logs.add(new BlockPos(x, y + i, z));
		}

		logs.add(new BlockPos(x + 1, y + 6, z + 1));
		command("fill " + (x - 1) + " " + (y + 5) + " " + (z - 1) + " " + (x + 1) + " " + (y + 7) + " " + (z + 1)
			+ " minecraft:oak_leaves[persistent=false,distance=1]");

		for (BlockPos log : logs) {
			put(log, "minecraft:oak_log");
		}

		context.waitTicks(3);
		return logs;
	}

	/** Presses the module's key for real and waits until the job is done and the server has answered. */
	void pressAndWait() {
		press();

		for (int tick = 0; tick < PATIENCE_TICKS; tick++) {
			if (!busy()) {
				context.waitTicks(10);
				return;
			}

			context.waitTick();
		}

		throw new AssertionError("Damar Kırıcı " + PATIENCE_TICKS + " tick'te bitmedi");
	}

	boolean busy() {
		return context.computeOnClient(client -> module().busy());
	}

	/** Waits for the commands in flight to go and the server to answer. */
	void waitUntilDone() {
		for (int tick = 0; tick < PATIENCE_TICKS && busy(); tick++) {
			context.waitTick();
		}

		if (busy()) {
			throw new AssertionError("Damar Kırıcı " + PATIENCE_TICKS + " tick'te bitmedi");
		}

		context.waitTicks(10);
	}

	/**
	 * Holds attack, for real, until the block at {@code pos} is gone, then lets go. With
	 * {@code withTrigger} the trigger key is held down the whole time and released after.
	 */
	void dig(BlockPos pos, boolean withTrigger) {
		InputConstants.Key trigger = InputConstants.getKey(KEY);

		if (withTrigger) {
			context.getInput().holdKey(trigger);
			context.waitTicks(2);
		}

		context.getInput().holdKey(options -> options.keyAttack);

		try {
			for (int tick = 0; tick < PATIENCE_TICKS; tick++) {
				if (context.computeOnClient(client -> client.level.getBlockState(pos).isAir())) {
					return;
				}

				context.waitTick();
			}

			throw new AssertionError(pos + " " + PATIENCE_TICKS + " tick'te kırılmadı");
		} finally {
			context.getInput().releaseKey(options -> options.keyAttack);
			context.waitTicks(2);

			if (withTrigger) {
				context.getInput().releaseKey(trigger);
			}
		}
	}

	void holdTrigger(boolean down) {
		if (down) {
			context.getInput().holdKey(InputConstants.getKey(KEY));
		} else {
			context.getInput().releaseKey(InputConstants.getKey(KEY));
		}

		context.waitTicks(3);
	}

	/**
	 * The outlines the game is really about to draw: the gizmos the last client tick left
	 * behind, in the module's colour, as the blocks they frame.
	 */
	Set<BlockPos> outlinedOnScreen() {
		return context.computeOnClient(client -> client.getPerTickGizmos().stream()
			.map(instance -> instance.gizmo())
			.filter(gizmo -> gizmo instanceof CuboidGizmo cuboid && cuboid.style().stroke() == ClientVeinContext.OUTLINE_COLOUR)
			.map(gizmo -> BlockPos.containing(((CuboidGizmo) gizmo).aabb().getCenter()))
			.collect(Collectors.toSet()));
	}

	void press() {
		context.getInput().holdKeyFor(InputConstants.getKey(KEY), 2);
		context.waitTicks(2);
	}

	void assertLookingAt(BlockPos expected) {
		BlockPos actual = context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit ? hit.getBlockPos() : null);

		if (!expected.equals(actual)) {
			throw new AssertionError("nişangâh " + expected + " üstünde olmalıydı, olan: " + actual);
		}
	}

	/** The positions among {@code positions} that still hold {@code block}, read on the server. */
	List<BlockPos> stillThere(List<BlockPos> positions, Block block) {
		return world.getServer().computeOnServer(server -> positions.stream()
			.filter(pos -> server.overworld().getBlockState(pos).is(block))
			.toList());
	}

	/** How many of {@code item} the player carries, as the client sees it. */
	int carried(Item item) {
		return context.computeOnClient(client -> client.player.getInventory().getNonEquipmentItems().stream()
			.filter(stack -> stack.is(item))
			.mapToInt(ItemStack::getCount)
			.sum());
	}

	/** How many of {@code item} lie on the ground around the stage, read on the server. */
	int onTheGround(Item item) {
		AABB stage = new AABB(base).inflate(8, 12, 8);

		return world.getServer().computeOnServer(server -> server.overworld()
			.getEntitiesOfClass(ItemEntity.class, stage, entity -> entity.getItem().is(item))
			.stream()
			.mapToInt(entity -> entity.getItem().getCount())
			.sum());
	}

	int sentSoFar() {
		return context.computeOnClient(client -> module().sentCommands().size());
	}

	/** The commands sent after the first {@code count}. */
	List<String> sentSince(int count) {
		return context.computeOnClient(client -> {
			List<String> all = module().sentCommands();
			return List.copyOf(all.subList(count, all.size()));
		});
	}

	@SuppressWarnings("unchecked")
	<T> void set(String id, T value) {
		context.runOnClient(client -> module().settings().<Setting<T>>byId(id)
			.orElseThrow(() -> new AssertionError("ayar yok: " + id))
			.set(value));
	}

	void reset(String id) {
		context.runOnClient(client -> module().settings().<Setting<?>>byId(id).orElseThrow().reset());
	}

	void command(String command) {
		world.getServer().runCommand(command);
	}

	<T> T computeOnServer(Function<MinecraftServer, T> read) {
		return world.getServer().computeOnServer(read::apply);
	}

	/**
	 * Sets the hunger bar on the server, exhaustion included, which has no setter: the field is
	 * written directly so a test starts from exactly nothing towards the next point.
	 */
	void setHunger(int food, float saturation) {
		world.getServer().runOnServer(server -> {
			FoodData data = server.getPlayerList().getPlayers().getFirst().getFoodData();
			data.setFoodLevel(food);
			data.setSaturation(saturation);

			try {
				Field exhaustion = FoodData.class.getDeclaredField("exhaustionLevel");
				exhaustion.setAccessible(true);
				exhaustion.setFloat(data, 0.0f);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("açlık yorgunluğu sıfırlanamadı", e);
			}
		});

		// The server sends the bar on its next tick; the module reads the client's copy.
		for (int tick = 0; tick < 40; tick++) {
			context.waitTick();

			if (context.computeOnClient(client -> client.player.getFoodData().getFoodLevel() == food
				&& client.player.getFoodData().getSaturationLevel() == saturation)) {
				return;
			}
		}

		throw new AssertionError("hazırlık: istemci açlık " + food + " / doygunluk " + saturation + " görmedi");
	}

	/** The food bar and saturation as the server has them. */
	float[] hunger() {
		return world.getServer().computeOnServer(server -> {
			FoodData data = server.getPlayerList().getPlayers().getFirst().getFoodData();
			return new float[] {data.getFoodLevel(), data.getSaturationLevel()};
		});
	}

	/**
	 * Starts recording the chat. See AutoTotemGameTest: the filter is the one hook that sees every
	 * line that reaches the chat, and a line the mod hides never reaches it.
	 */
	void watchChat() {
		CHAT.clear();
		context.runOnClient(client -> {
			client.gui.hud.getChat().clearMessages(true);
			client.gui.hud.getChat().setVisibleMessageFilter(message -> {
				CHAT.add(message);
				return true;
			});
		});
	}

	/** Lines in the recorded chat that are a /setblock, /fill or /loot success reply. */
	int successReplies() {
		return (int) CHAT.stream()
			.filter(message -> message.content().getContents() instanceof TranslatableContents reply
				&& (reply.getKey().equals("commands.setblock.success")
					|| reply.getKey().equals("commands.fill.success")
					|| reply.getKey().startsWith("commands.drop.success.")))
			.count();
	}

	int linesSaying(String text) {
		return (int) CHAT.stream().filter(message -> message.content().getString().equals(text)).count();
	}

	static VeinMinerModule module() {
		return (VeinMinerModule) SurvivalKit.modules().byId("veinminer")
			.orElseThrow(() -> new AssertionError("veinminer modülü kayıtlı değil"));
	}
}
