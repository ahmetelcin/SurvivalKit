package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.RegistryListSetting;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Otomatik Alet in a running game, with the attack key really held.
 *
 * <p>The player stands on a column of three stone blocks and looks straight down, so holding
 * attack digs through all three without the mouse moving: one run of blocks, with the
 * game's own pause between each. Every tick the selected slot is written down, and the
 * whole history is what is checked — one switch out, one switch back, nothing in between.
 */
public class AutoToolGameTest implements FabricClientGameTest {
	private static final int HAND_SLOT = 0;
	private static final int PICKAXE_SLOT = 2;
	private static final int STONES = 3;

	/** Three blocks of stone with an iron pickaxe and the falls between them, with room to spare. */
	private static final int PATIENCE_TICKS = 400;

	/** Long enough to have started breaking and, were anything going to switch, to have switched. */
	private static final int HOLD_TICKS = 30;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(5);
			world.getServer().runCommand("gamemode survival @a");

			List<Integer> run = threeBlocksInARowAreOneSwitchOutAndOneBack(context, world);
			withTheModuleOffNothingSwitches(context, world);
			aSwordInHandIsLeftThere(context, world);
			inCreativeNothingSwitches(context, world);
			aSilkTouchPickaxeInHandIsLeftThere(context, world);
			withKeepSilkTouchOffTheFasterPickaxeIsTaken(context, world);
			everyDefaultIsARealBlock(context);

			off(context);
			SurvivalKit.LOGGER.info("[gametest] Otomatik Alet doğrulandı (taş kırmaya başlayınca kazmaya geçti, üç blok art arda kırılırken seçim geçmişi {} — tek geçiş, bırakınca tek dönüş; modül kapalıyken, kılıç eldeyken ve yaratıcı modda hiç geçiş olmadı; elde İpeksi Dokunuşlu kazma varken daha hızlısına geçilmedi ve taş taş olarak düştü, ayar kapalıyken geçildi; varsayılan listelerdeki her id gerçek bir blok)", run);
		}
	}

	private List<Integer> threeBlocksInARowAreOneSwitchOutAndOneBack(ClientGameTestContext context, TestSingleplayerContext world) {
		BlockPos top = setUp(context, world, "minecraft:bread");
		on(context);

		List<Integer> history = new ArrayList<>();
		boolean sawThePickaxeWhileBreaking = false;
		context.getInput().holdKey(options -> options.keyAttack);

		int tick = 0;

		for (; tick < PATIENCE_TICKS && !allBroken(context, top); tick++) {
			record(context, history);

			if (context.computeOnClient(client -> client.gameMode.isDestroying() && client.player.getMainHandItem().is(Items.IRON_PICKAXE))) {
				sawThePickaxeWhileBreaking = true;
			}

			context.waitTick();
		}

		if (tick >= PATIENCE_TICKS) {
			context.getInput().releaseKey(options -> options.keyAttack);
			throw new AssertionError("üç taş " + PATIENCE_TICKS + " tick'te kırılmalıydı, seçim geçmişi: " + history);
		}

		if (!sawThePickaxeWhileBreaking) {
			context.getInput().releaseKey(options -> options.keyAttack);
			throw new AssertionError("taş kırılırken elde kazma olmalıydı, seçim geçmişi: " + history);
		}

		context.getInput().releaseKey(options -> options.keyAttack);

		for (int i = 0; i < 10; i++) {
			context.waitTick();
			record(context, history);
		}

		List<Integer> changes = changes(history);

		if (!changes.equals(List.of(HAND_SLOT, PICKAXE_SLOT, HAND_SLOT))) {
			throw new AssertionError("üç blok boyunca tek geçiş ve bırakınca tek dönüş olmalıydı (0 → 2 → 0), olan: " + changes);
		}

		if (!context.computeOnClient(client -> client.player.getMainHandItem().is(Items.BREAD))) {
			throw new AssertionError("bırakınca ekmek ele dönmeliydi, elde: " + held(context));
		}

		return changes;
	}

	/**
	 * Negative control: the same key held on the same stone with the module off. The bread
	 * stays, so the switch above is the module's doing and not something the game does.
	 */
	private void withTheModuleOffNothingSwitches(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, "minecraft:bread");
		off(context);

		List<Integer> history = holdAttack(context);

		if (!changes(history).equals(List.of(HAND_SLOT))) {
			throw new AssertionError("negatif kontrol başarısız: modül kapalıyken seçim değişmemeliydi, olan: " + changes(history));
		}
	}

	private void aSwordInHandIsLeftThere(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, "minecraft:iron_sword");
		on(context);

		List<Integer> history = holdAttack(context);

		if (!changes(history).equals(List.of(HAND_SLOT)) || !context.computeOnClient(client -> client.player.getMainHandItem().is(Items.IRON_SWORD))) {
			throw new AssertionError("kılıç eldeyken alete geçilmemeliydi, seçim geçmişi: " + changes(history) + ", elde: " + held(context));
		}
	}

	private void inCreativeNothingSwitches(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, "minecraft:bread");
		world.getServer().runCommand("gamemode creative @a");
		context.waitTicks(5);
		on(context);

		List<Integer> history = holdAttack(context);
		world.getServer().runCommand("gamemode survival @a");

		if (!changes(history).equals(List.of(HAND_SLOT))) {
			throw new AssertionError("yaratıcı modda alete geçilmemeliydi, seçim geçmişi: " + changes(history));
		}
	}

	private static final String SILK_IRON_PICKAXE = "minecraft:iron_pickaxe[minecraft:enchantments={\"minecraft:silk_touch\":1}]";

	/**
	 * A Silk Touch iron pickaxe in hand and a faster diamond one two slots over. The stone is
	 * broken with the one in hand, and the proof it was is in the backpack: stone, not
	 * cobblestone.
	 */
	private void aSilkTouchPickaxeInHandIsLeftThere(ClientGameTestContext context, TestSingleplayerContext world) {
		BlockPos top = setUp(context, world, SILK_IRON_PICKAXE, "minecraft:diamond_pickaxe");
		keepSilkTouch(context, true);
		on(context);

		List<Integer> history = breakTheTopStone(context, top);

		if (!changes(history).equals(List.of(HAND_SLOT))) {
			throw new AssertionError("elde İpeksi Dokunuşlu kazma varken daha hızlısına geçilmemeliydi, seçim geçmişi: " + changes(history));
		}

		context.waitTicks(10);

		if (!context.computeOnClient(client -> client.player.getInventory().getNonEquipmentItems().stream().anyMatch(stack -> stack.is(Items.STONE)))) {
			throw new AssertionError("taş taş olarak düşmeliydi; envanterde taş yok");
		}
	}

	/** Negative control: the same hands with the setting off, and the diamond pickaxe comes out. */
	private void withKeepSilkTouchOffTheFasterPickaxeIsTaken(ClientGameTestContext context, TestSingleplayerContext world) {
		BlockPos top = setUp(context, world, SILK_IRON_PICKAXE, "minecraft:diamond_pickaxe");
		keepSilkTouch(context, false);
		on(context);

		List<Integer> history = breakTheTopStone(context, top);
		keepSilkTouch(context, true);

		if (!changes(history).equals(List.of(HAND_SLOT, PICKAXE_SLOT, HAND_SLOT))) {
			throw new AssertionError("negatif kontrol başarısız: ayar kapalıyken elmas kazmaya geçilmeliydi, seçim geçmişi: " + changes(history));
		}
	}

	/** The shipped lists name real blocks; a typo would sit there matching nothing forever. */
	private static void everyDefaultIsARealBlock(ClientGameTestContext context) {
		List<String> unknown = context.computeOnClient(client -> {
			List<String> missing = new ArrayList<>();

			for (String list : List.of("silk_touch_blocks", "fortune_blocks")) {
				RegistryListSetting setting = module().settings().<RegistryListSetting>byId(list).orElseThrow();

				for (String id : setting.defaultValue()) {
					if (!BuiltInRegistries.BLOCK.containsKey(Identifier.parse(id))) {
						missing.add(id);
					}
				}
			}

			return missing;
		});

		if (!unknown.isEmpty()) {
			throw new AssertionError("varsayılan listelerde oyunda olmayan blok: " + unknown);
		}
	}

	// --- helpers ---

	/** Holds attack until the top stone is gone, then lets go; the selection is written down throughout. */
	private static List<Integer> breakTheTopStone(ClientGameTestContext context, BlockPos top) {
		List<Integer> history = new ArrayList<>();
		context.getInput().holdKey(options -> options.keyAttack);

		for (int tick = 0; tick < PATIENCE_TICKS && context.computeOnClient(client -> client.level.getBlockState(top).is(Blocks.STONE)); tick++) {
			record(context, history);
			context.waitTick();
		}

		context.getInput().releaseKey(options -> options.keyAttack);

		for (int i = 0; i < 5; i++) {
			context.waitTick();
			record(context, history);
		}

		return history;
	}

	private static void keepSilkTouch(ClientGameTestContext context, boolean on) {
		context.runOnClient(client -> module().settings()
				.<BoolSetting>byId("keep_silk_touch")
				.orElseThrow(() -> new AssertionError("İpeksi Dokunuş ayarı yok"))
				.set(on));
	}

	/**
	 * A fresh column of stone under the player, bedrock beneath it so the dig stops there,
	 * the given item in the selected slot and an iron pickaxe two slots over.
	 *
	 * @return the top stone
	 */
	private static BlockPos setUp(ClientGameTestContext context, TestSingleplayerContext world, String inHand) {
		return setUp(context, world, inHand, "minecraft:iron_pickaxe");
	}

	private static BlockPos setUp(ClientGameTestContext context, TestSingleplayerContext world, String inHand, String pickaxe) {
		off(context);
		// These scenarios start from bread or a sword in hand to watch the switching itself;
		// "only with a tool in hand" is on by default and is checked in ToolPairsGameTest.
		context.runOnClient(client -> module().settings().<BoolSetting>byId("only_with_tool").orElseThrow().set(false));

		BlockPos feet = context.computeOnClient(client -> client.player.blockPosition());
		BlockPos top = new BlockPos(feet.getX(), feet.getY() + 20, feet.getZ());
		int x = top.getX();
		int y = top.getY();
		int z = top.getZ();

		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("fill " + (x - 2) + " " + (y - STONES - 1) + " " + (z - 2) + " " + (x + 2) + " " + (y + 4) + " " + (z + 2) + " air");

		for (int i = 0; i < STONES; i++) {
			world.getServer().runCommand("setblock " + x + " " + (y - i) + " " + z + " minecraft:stone");
		}

		world.getServer().runCommand("setblock " + x + " " + (y - STONES) + " " + z + " minecraft:bedrock");
		world.getServer().runCommand("item replace entity @a hotbar." + HAND_SLOT + " with " + inHand);
		world.getServer().runCommand("item replace entity @a hotbar." + PICKAXE_SLOT + " with " + pickaxe);
		world.getServer().runCommand("tp @a " + (x + 0.5) + " " + (y + 1) + " " + (z + 0.5) + " 0 90");
		world.getServer().runCommand("effect give @a minecraft:saturation 60 0 true");
		context.waitTicks(10);

		context.runOnClient(client -> {
			client.player.getInventory().setSelectedSlot(HAND_SLOT);
			client.player.setXRot(90.0f);
		});
		context.waitTicks(5);

		return top;
	}

	/** Holds attack on the stone for {@link #HOLD_TICKS}, writing down the selection each tick. */
	private static List<Integer> holdAttack(ClientGameTestContext context) {
		List<Integer> history = new ArrayList<>();
		context.getInput().holdKey(options -> options.keyAttack);

		for (int i = 0; i < HOLD_TICKS; i++) {
			record(context, history);
			context.waitTick();
		}

		context.getInput().releaseKey(options -> options.keyAttack);

		for (int i = 0; i < 5; i++) {
			context.waitTick();
			record(context, history);
		}

		return history;
	}

	private static boolean allBroken(ClientGameTestContext context, BlockPos top) {
		return context.computeOnClient(client -> {
			for (int i = 0; i < STONES; i++) {
				if (client.level.getBlockState(top.below(i)).is(Blocks.STONE)) {
					return false;
				}
			}

			return true;
		});
	}

	private static void record(ClientGameTestContext context, List<Integer> history) {
		history.add(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot()));
	}

	/** The history with repeats folded: each entry is a slot the selection moved to. */
	private static List<Integer> changes(List<Integer> history) {
		List<Integer> changes = new ArrayList<>();

		for (int slot : history) {
			if (changes.isEmpty() || changes.getLast() != slot) {
				changes.add(slot);
			}
		}

		return changes;
	}

	private static String held(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getMainHandItem().getHoverName().getString());
	}

	private static void on(ClientGameTestContext context) {
		context.runOnClient(client -> module().setEnabled(true));
		context.waitTicks(2);
	}

	private static void off(ClientGameTestContext context) {
		context.runOnClient(client -> module().setEnabled(false));
		context.waitTicks(2);
	}

	private static Module module() {
		return SurvivalKit.modules().byId("autotool")
				.orElseThrow(() -> new AssertionError("autotool modülü kayıtlı değil"));
	}
}
