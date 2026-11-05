package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
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
 * Damar Kırıcı on ores, with the enchantments of the pickaxe in hand: a plain one gets the
 * ore's usual drop, Silk Touch the ore block itself, Fortune more coal than one each.
 */
public class VeinMinerOreGameTest implements FabricClientGameTest {
	private static final String PICKAXE = "minecraft:iron_pickaxe";
	private static final String SILK_PICKAXE = "minecraft:iron_pickaxe[minecraft:enchantments={\"minecraft:silk_touch\":1}]";
	private static final String FORTUNE_PICKAXE = "minecraft:iron_pickaxe[minecraft:enchantments={\"minecraft:fortune\":3}]";

	/**
	 * Coal ore under Fortune III drops one coal times a multiplier of 1 to 4, and the
	 * multiplier is 1 with chance 2/5. Thirty ores all landing on 1 — the only way to get no
	 * more than thirty coal — has chance (2/5)^30, about one in 860 billion.
	 */
	private static final int COAL_ORES = 30;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			VeinTestSupport stage = new VeinTestSupport(context, world);

			everyDefaultIsARealBlock(context);
			theWholeVeinGoesAndNothingElse(context, stage);
			int silk = silkTouchGivesTheOreItself(context, stage);
			int plainCoal = coalVein(context, stage, PICKAXE);
			int fortuneCoal = coalVein(context, stage, FORTUNE_PICKAXE);

			if (plainCoal != COAL_ORES) {
				throw new AssertionError("negatif kontrol başarısız: Servetsiz kazmayla " + COAL_ORES + " cevherden tam " + COAL_ORES + " kömür gelmeliydi, gelen: " + plainCoal);
			}

			if (fortuneCoal <= COAL_ORES) {
				throw new AssertionError("Servet III'lü kazmayla " + COAL_ORES + " cevherden " + COAL_ORES + "'dan fazla kömür gelmeliydi, gelen: " + fortuneCoal);
			}

			stage.dropFromCommandTree("loot");

			try {
				withoutLootThePlainPickaxeDestroysTheVein(context, stage);
				withoutLootSilkTouchGivesTheOresThemselves(context, stage);
			} finally {
				stage.resendCommandTree("loot");
			}

			SurvivalKit.LOGGER.info("[gametest] Damar Kırıcı cevherde doğrulandı (demir damarı derin kayrak ikiziyle birlikte gitti, yanındaki kömür ve taş kaldı, "
				+ "düz kazmayla ham demir geldi; İpeksi Dokunuşla {} cevher bloğu geldi, ham demir gelmedi; {} kömür cevherinden düz kazmayla {}, Servet III ile {} kömür; "
				+ "/loot'suz damarda düz kazmayla 6 ham demir yere düştü, İpeksi Dokunuşla 6 cevher /give ile geldi, hiç /loot gitmedi)",
				silk, COAL_ORES, plainCoal, fortuneCoal);
		}
	}

	/**
	 * A command tree without /loot, as some servers send: the vein is destroyed with /fill and
	 * /setblock "destroy", and each ore drops what it drops to a bare hand, where it stood.
	 */
	private void withoutLootThePlainPickaxeDestroysTheVein(ClientGameTestContext context, VeinTestSupport stage) {
		stage.clear(PICKAXE);
		List<BlockPos> vein = ironVein(stage);
		stage.fillInventoryExceptHand("minecraft:cobblestone 64");
		stage.standFacing(stage.base.getX(), stage.base.getZ());
		int before = stage.sentSoFar();

		stage.pressAndWait();
		List<String> sent = stage.sentSince(before);

		if (!remaining(stage, vein).isEmpty()) {
			throw new AssertionError("/loot yokken de damar gitmeliydi, kalan: " + remaining(stage, vein));
		}

		if (stage.onTheGround(Items.RAW_IRON) != vein.size()) {
			throw new AssertionError(vein.size() + " ham demir yere düşmeliydi, düşen: " + stage.onTheGround(Items.RAW_IRON));
		}

		if (sent.stream().anyMatch(command -> command.startsWith("loot ")) || sent.stream().noneMatch(command -> command.endsWith(" destroy"))) {
			throw new AssertionError("/loot yokken /loot gönderilmemeli, \"destroy\" ile kırılmalıydı; giden: " + sent);
		}
	}

	/** The same vein, Silk Touch in hand: the ores come into the inventory as themselves, by /give. */
	private void withoutLootSilkTouchGivesTheOresThemselves(ClientGameTestContext context, VeinTestSupport stage) {
		stage.clear(SILK_PICKAXE);
		List<BlockPos> vein = ironVein(stage);
		stage.standFacing(stage.base.getX(), stage.base.getZ());
		int before = stage.sentSoFar();

		stage.pressAndWait();
		List<String> sent = stage.sentSince(before);

		if (!remaining(stage, vein).isEmpty()) {
			throw new AssertionError("/loot yokken de damar gitmeliydi, kalan: " + remaining(stage, vein));
		}

		if (sent.stream().anyMatch(command -> command.startsWith("loot ")) || sent.stream().noneMatch(command -> command.startsWith("give "))) {
			throw new AssertionError("/loot yokken İpeksi Dokunuşla /give gitmeliydi; giden: " + sent);
		}

		if (stage.carried(Items.IRON_ORE) != 4 || stage.carried(Items.DEEPSLATE_IRON_ORE) != 2
				|| stage.carried(Items.RAW_IRON) != 0 || stage.onTheGround(Items.RAW_IRON) != 0) {
			throw new AssertionError("İpeksi Dokunuşla 4 demir cevheri ve 2 derin kayrak demir cevheri gelmeliydi, ham demir hiç; gelen: "
				+ stage.carried(Items.IRON_ORE) + " + " + stage.carried(Items.DEEPSLATE_IRON_ORE) + ", ham demir envanterde "
				+ stage.carried(Items.RAW_IRON) + ", yerde " + stage.onTheGround(Items.RAW_IRON));
		}
	}

	/**
	 * Six iron ores, two of them deepslate, touching only at edges and corners; a coal ore and
	 * stone touching the vein. The six go and give six raw iron; the coal and the stone stay.
	 */
	private void theWholeVeinGoesAndNothingElse(ClientGameTestContext context, VeinTestSupport stage) {
		stage.clear(PICKAXE);
		List<BlockPos> vein = ironVein(stage);
		BlockPos coal = stage.target(stage.base.getX(), stage.base.getZ()).west();
		BlockPos stone = stage.target(stage.base.getX(), stage.base.getZ()).below();
		stage.put(coal, "minecraft:coal_ore");
		stage.put(stone, "minecraft:stone");
		stage.standFacing(stage.base.getX(), stage.base.getZ());
		stage.assertLookingAt(stage.target(stage.base.getX(), stage.base.getZ()));

		stage.pressAndWait();

		if (!remaining(stage, vein).isEmpty()) {
			throw new AssertionError("damardaki bütün cevherler gitmeliydi, kalan: " + remaining(stage, vein));
		}

		if (stage.stillThere(List.of(coal), Blocks.COAL_ORE).isEmpty() || stage.stillThere(List.of(stone), Blocks.STONE).isEmpty()) {
			throw new AssertionError("damara değen kömür cevheri ve taş yerinde kalmalıydı");
		}

		if (stage.carried(Items.RAW_IRON) != vein.size() || stage.carried(Items.IRON_ORE) != 0) {
			throw new AssertionError("düz kazmayla " + vein.size() + " ham demir gelmeliydi; ham demir " + stage.carried(Items.RAW_IRON)
				+ ", demir cevheri " + stage.carried(Items.IRON_ORE));
		}
	}

	/**
	 * The same vein with a Silk Touch pickaxe: the ores come as themselves. The vein above,
	 * mined with a plain pickaxe, is the control — that one gave raw iron.
	 *
	 * @return the ore blocks that arrived
	 */
	private int silkTouchGivesTheOreItself(ClientGameTestContext context, VeinTestSupport stage) {
		stage.clear(SILK_PICKAXE);
		List<BlockPos> vein = ironVein(stage);
		stage.standFacing(stage.base.getX(), stage.base.getZ());

		stage.pressAndWait();

		if (!remaining(stage, vein).isEmpty()) {
			throw new AssertionError("damardaki bütün cevherler gitmeliydi, kalan: " + remaining(stage, vein));
		}

		int ores = stage.carried(Items.IRON_ORE) + stage.carried(Items.DEEPSLATE_IRON_ORE);

		if (stage.carried(Items.IRON_ORE) != 4 || stage.carried(Items.DEEPSLATE_IRON_ORE) != 2 || stage.carried(Items.RAW_IRON) != 0) {
			throw new AssertionError("İpeksi Dokunuşla 4 demir cevheri ve 2 derin kayrak demir cevheri gelmeliydi, ham demir hiç; gelen: demir cevheri "
				+ stage.carried(Items.IRON_ORE) + ", derin kayrak " + stage.carried(Items.DEEPSLATE_IRON_ORE) + ", ham demir " + stage.carried(Items.RAW_IRON));
		}

		return ores;
	}

	/**
	 * Thirty coal ores in a solid block, five wide, three high, two deep, broken with {@code pickaxe}.
	 *
	 * @return the coal that arrived
	 */
	private int coalVein(ClientGameTestContext context, VeinTestSupport stage, String pickaxe) {
		stage.clear(pickaxe);
		int x = stage.base.getX();
		int y = stage.base.getY();
		int z = stage.base.getZ();
		List<BlockPos> vein = new ArrayList<>();

		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = 1; dy <= 3; dy++) {
				for (int dz = 0; dz <= 1; dz++) {
					vein.add(new BlockPos(x + dx, y + dy, z + dz));
				}
			}
		}

		stage.command("fill " + (x - 2) + " " + (y + 1) + " " + z + " " + (x + 2) + " " + (y + 3) + " " + (z + 1) + " minecraft:coal_ore");
		context.waitTicks(3);
		stage.standFacing(x, z);
		stage.set("commands_per_second", 40);

		try {
			stage.pressAndWait();
		} finally {
			stage.reset("commands_per_second");
		}

		if (!stage.stillThere(vein, Blocks.COAL_ORE).isEmpty()) {
			throw new AssertionError("bütün kömür cevherleri gitmeliydi, kalan: " + stage.stillThere(vein, Blocks.COAL_ORE).size());
		}

		return stage.carried(Items.COAL);
	}

	/** Six iron ores from the target up and back, the third and sixth deepslate; corners join them. */
	private static List<BlockPos> ironVein(VeinTestSupport stage) {
		BlockPos start = stage.target(stage.base.getX(), stage.base.getZ());
		List<BlockPos> vein = List.of(
			start,
			start.offset(1, 1, 1),
			start.offset(1, 2, 2),
			start.offset(0, 3, 2),
			start.offset(-1, 3, 3),
			start.offset(-1, 4, 4));

		for (int i = 0; i < vein.size(); i++) {
			stage.put(vein.get(i), i == 2 || i == 5 ? "minecraft:deepslate_iron_ore" : "minecraft:iron_ore");
		}

		return vein;
	}

	/** The shipped list names real blocks; a typo would sit there matching nothing forever. */
	private static void everyDefaultIsARealBlock(ClientGameTestContext context) {
		List<String> unknown = context.computeOnClient(client -> VeinTestSupport.module().settings()
			.<RegistryListSetting>byId("blocks").orElseThrow()
			.defaultValue().stream()
			.filter(id -> !BuiltInRegistries.BLOCK.containsKey(Identifier.parse(id)))
			.toList());

		if (!unknown.isEmpty()) {
			throw new AssertionError("varsayılan blok listesinde oyunda olmayan blok: " + unknown);
		}
	}

	private static List<BlockPos> remaining(VeinTestSupport stage, List<BlockPos> vein) {
		List<BlockPos> left = new ArrayList<>(stage.stillThere(vein, Blocks.IRON_ORE));
		left.addAll(stage.stillThere(vein, Blocks.DEEPSLATE_IRON_ORE));
		return left;
	}
}
