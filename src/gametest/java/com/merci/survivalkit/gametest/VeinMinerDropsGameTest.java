package com.merci.survivalkit.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.vein.OreDrops;
import java.io.Reader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;

/**
 * Fortune on a server without /loot: the drops are rolled by the mod and handed over with /give.
 * First the mod's copy of vanilla's rules is held to the running game's own loot tables and ore
 * XP; then thirty coal ores with a Fortune III pickaxe bring more than thirty coal, and the same
 * ores with a plain pickaxe exactly thirty, by the server's own "destroy".
 */
public class VeinMinerDropsGameTest implements FabricClientGameTest {
	private static final String PICKAXE = "minecraft:iron_pickaxe";
	private static final String FORTUNE_PICKAXE = "minecraft:iron_pickaxe[minecraft:enchantments={\"minecraft:fortune\":3}]";

	/** See VeinMinerOreGameTest: no more than thirty coal from thirty ores under Fortune III has chance (2/5)^30. */
	private static final int COAL_ORES = 30;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			VeinTestSupport stage = new VeinTestSupport(context, world);

			int checked = theTableIsTheGamesOwn(world);

			stage.dropFromCommandTree("loot");
			int plain;
			int fortune;
			int experience;

			try {
				plain = plainPickaxeCoalIsTheServersDestroy(context, stage);
				int[] result = fortunePickaxeCoalIsRolledAndGiven(context, stage, world);
				fortune = result[0];
				experience = result[1];
			} finally {
				stage.resendCommandTree("loot");
			}

			SurvivalKit.LOGGER.info("[gametest] Damar Kırıcı /loot'suz Servet doğrulandı (tablo oyunun {} cevher ganimet tablosu ve XP aralığıyla aynı; "
				+ "{} kömür cevherinden düz kazmayla yere {}, Servet III ile /give ile {} kömür ve {} XP puanı)",
				checked, COAL_ORES, plain, fortune, experience);
		}
	}

	/**
	 * Every ore in {@link OreDrops}, its deepslate twin too, against the loot table the server
	 * loaded and the XP its block was built with. A number Mojang changes fails here.
	 *
	 * @return how many blocks were checked
	 */
	private static int theTableIsTheGamesOwn(TestSingleplayerContext world) {
		List<String> problems = world.getServer().computeOnServer(server -> {
			List<String> found = new ArrayList<>();

			for (Map.Entry<String, OreDrops.Drop> entry : OreDrops.all().entrySet()) {
				for (String block : withTwin(entry.getKey())) {
					String problem = compare(server, block, entry.getValue());

					if (problem != null) {
						found.add(block + ": " + problem);
					}
				}
			}

			return found;
		});

		if (!problems.isEmpty()) {
			throw new AssertionError("Servet tablosu oyunun kendi verisinden ayrılmış: " + problems);
		}

		return OreDrops.all().keySet().stream().mapToInt(block -> withTwin(block).size()).sum();
	}

	private static List<String> withTwin(String block) {
		Identifier id = Identifier.parse(block);
		Identifier twin = Identifier.withDefaultNamespace("deepslate_" + id.getPath());
		return BuiltInRegistries.BLOCK.containsKey(twin) ? List.of(block, twin.toString()) : List.of(block);
	}

	/** Null when the loot table and the block's XP say what the drop says. */
	private static String compare(MinecraftServer server, String block, OreDrops.Drop drop) {
		Identifier id = Identifier.parse(block);
		JsonObject table;

		try (Reader reader = server.getResourceManager()
				.getResourceOrThrow(Identifier.fromNamespaceAndPath(id.getNamespace(), "loot_table/blocks/" + id.getPath() + ".json"))
				.openAsReader()) {
			table = JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception e) {
			return "ganimet tablosu okunamadı: " + e;
		}

		JsonArray children = table.getAsJsonArray("pools").get(0).getAsJsonObject()
			.getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonArray("children");

		// The second child is the drop without Silk Touch; the first is the block itself.
		JsonObject plain = children.get(1).getAsJsonObject();
		String item = plain.get("name").getAsString();
		int min = 1;
		int max = 1;
		String formula = null;
		int multiplier = 1;

		// 26.3 calls the list "modifier" and the kind "type"; older tables said "functions" and "function".
		JsonArray modifiers = plain.has("modifier") ? plain.getAsJsonArray("modifier") : plain.getAsJsonArray("functions");

		for (JsonElement element : modifiers) {
			JsonObject function = element.getAsJsonObject();

			String kind = function.has("type") ? function.get("type").getAsString() : function.get("function").getAsString();

			switch (kind) {
				case "minecraft:set_count" -> {
					JsonObject count = function.getAsJsonObject("count");
					min = (int) count.get("min").getAsFloat();
					max = (int) count.get("max").getAsFloat();
				}
				case "minecraft:apply_bonus" -> {
					if (!function.get("enchantment").getAsString().equals("minecraft:fortune")) {
						return "Servet dışı bir büyüyle bonus: " + function;
					}

					formula = function.get("formula").getAsString();

					if (function.has("parameters")) {
						multiplier = function.getAsJsonObject("parameters").get("bonusMultiplier").getAsInt();
					}
				}
				default -> {
				}
			}
		}

		String expectedFormula = switch (drop.bonus()) {
			case ORE_DROPS -> "minecraft:ore_drops";
			case UNIFORM_BONUS -> "minecraft:uniform_bonus_count";
		};

		if (!item.equals(drop.item()) || min != drop.min() || max != drop.max() || !expectedFormula.equals(formula) || multiplier != 1) {
			return "oyunda " + item + " " + min + "–" + max + " " + formula + " ×" + multiplier + ", tabloda " + drop;
		}

		Block instance = BuiltInRegistries.BLOCK.getValue(id);
		int xpMin;
		int xpMax;

		if (instance instanceof DropExperienceBlock) {
			try {
				Field range = DropExperienceBlock.class.getDeclaredField("xpRange");
				range.setAccessible(true);
				IntProvider provider = (IntProvider) range.get(instance);
				xpMin = provider.minInclusive();
				xpMax = provider.maxInclusive();
			} catch (ReflectiveOperationException e) {
				return "XP aralığı okunamadı: " + e;
			}
		} else if (instance instanceof RedStoneOreBlock) {
			// Hard-coded in RedStoneOreBlock.spawnAfterBreak (UniformInt.of(1, 5)), no field to read.
			xpMin = 1;
			xpMax = 5;
		} else {
			return "XP veren cevher değil: " + instance.getClass().getSimpleName();
		}

		if (xpMin != drop.xpMin() || xpMax != drop.xpMax()) {
			return "oyunda XP " + xpMin + "–" + xpMax + ", tabloda " + drop.xpMin() + "–" + drop.xpMax();
		}

		return null;
	}

	/** @return the coal on the ground */
	private int plainPickaxeCoalIsTheServersDestroy(ClientGameTestContext context, VeinTestSupport stage) {
		stage.clear(PICKAXE);
		List<BlockPos> vein = coal(context, stage);
		stage.fillInventoryExceptHand("minecraft:cobblestone 64");
		int before = stage.sentSoFar();

		stage.pressAndWait();
		stage.reset("commands_per_second");
		List<String> sent = stage.sentSince(before);

		if (!stage.stillThere(vein, Blocks.COAL_ORE).isEmpty()) {
			throw new AssertionError("bütün kömür cevherleri gitmeliydi, kalan: " + stage.stillThere(vein, Blocks.COAL_ORE).size());
		}

		int ground = stage.onTheGround(Items.COAL);

		if (ground != COAL_ORES || sent.stream().anyMatch(command -> command.startsWith("give "))) {
			throw new AssertionError("negatif kontrol başarısız: Servetsiz kazmayla yere tam " + COAL_ORES + " kömür düşmeli, /give gitmemeliydi; yerde "
				+ ground + ", giden: " + sent);
		}

		return ground;
	}

	/** @return the coal given and the experience points gained */
	private int[] fortunePickaxeCoalIsRolledAndGiven(ClientGameTestContext context, VeinTestSupport stage, TestSingleplayerContext world) {
		stage.clear(FORTUNE_PICKAXE);
		List<BlockPos> vein = coal(context, stage);
		// A difference, not a reading: /xp set leaves the lifetime counter alone.
		int experienceBefore = totalExperience(world);
		int before = stage.sentSoFar();

		stage.pressAndWait();
		stage.reset("commands_per_second");
		List<String> sent = stage.sentSince(before);

		if (!stage.stillThere(vein, Blocks.COAL_ORE).isEmpty()) {
			throw new AssertionError("bütün kömür cevherleri gitmeliydi, kalan: " + stage.stillThere(vein, Blocks.COAL_ORE).size());
		}

		int carried = stage.carried(Items.COAL);
		int ground = stage.onTheGround(Items.COAL);
		int experience = totalExperience(world) - experienceBefore;

		if (sent.stream().anyMatch(command -> command.startsWith("loot ") || command.contains("destroy"))
				|| sent.stream().noneMatch(command -> command.startsWith("give @s minecraft:coal "))) {
			throw new AssertionError("/loot'suz Servet'te kömür /give ile verilmeliydi, ne /loot ne destroy; giden: " + sent);
		}

		if (carried <= COAL_ORES || ground != 0) {
			throw new AssertionError("Servet III'lü kazmayla " + COAL_ORES + " cevherden envantere " + COAL_ORES + "'dan fazla kömür gelmeliydi, yere hiç; envanterde "
				+ carried + ", yerde " + ground);
		}

		// Exactly what /xp sent and nothing from elsewhere: leftover orbs are cleared with the stage.
		int byCommand = sent.stream()
			.filter(command -> command.startsWith("xp add @s ") && command.endsWith(" points"))
			.mapToInt(command -> Integer.parseInt(command.substring("xp add @s ".length(), command.length() - " points".length())))
			.sum();

		if (byCommand <= 0 || experience != byCommand) {
			throw new AssertionError("kömür cevherinin XP'si /xp ile gelmeliydi; /xp ile giden " + byCommand + ", oyuncuda " + experience + "; giden: " + sent);
		}

		return new int[] {carried, experience};
	}

	private static int totalExperience(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst().totalExperience);
	}

	/** Thirty coal ores, five wide, three high, two deep, the front middle one at the crosshair. */
	private static List<BlockPos> coal(ClientGameTestContext context, VeinTestSupport stage) {
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
		return vein;
	}
}
