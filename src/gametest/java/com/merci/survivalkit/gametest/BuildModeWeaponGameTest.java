package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.Setting;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Yapı Modu's wand is a wooden sword, and Oto Silah must leave it alone. With the wand in hand
 * and a Smite sword beside it, the player turns onto a zombie: the wand stays in hand. And with
 * only the wand to be had, Oto Silah does not put it in the ready slot. Each with Yapı Modu off
 * as the negative control, where Oto Silah does both.
 */
public class BuildModeWeaponGameTest implements FabricClientGameTest {
	private static final String SMITE_SWORD = "minecraft:iron_sword[minecraft:enchantments={\"minecraft:smite\":5}]";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(5);
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set midnight");
			world.getServer().runCommand("gamerule doMobSpawning false");
			world.getServer().runCommand("effect give @a minecraft:resistance 600 4 true");
			setAutoWeapon(context, "lock_seconds", 0);
			context.runOnClient(client -> autoWeapon().setEnabled(true));

			List<Integer> withWand = aimWithTheWandInHand(context, world, true);

			if (!withWand.equals(List.of(0))) {
				throw new AssertionError("Yapı Modu açıkken değnek elden gitmemeliydi, seçimler: " + withWand);
			}

			List<Integer> without = aimWithTheWandInHand(context, world, false);

			if (!without.equals(List.of(0, 1))) {
				throw new AssertionError("negatif kontrol başarısız: Yapı Modu kapalıyken Smite kılıcı seçilmeliydi, seçimler: " + without);
			}

			String readyOn = readySlotWithOnlyTheWand(context, world, true);

			if (!readyOn.isEmpty()) {
				throw new AssertionError("Yapı Modu açıkken değnek hazırlık slotuna konmamalıydı, slotta: " + readyOn);
			}

			String readyOff = readySlotWithOnlyTheWand(context, world, false);

			if (!readyOff.equals("minecraft:wooden_sword")) {
				throw new AssertionError("negatif kontrol başarısız: Yapı Modu kapalıyken tahta kılıç hazırlık slotuna gelmeliydi, slotta: " + readyOff);
			}

			context.runOnClient(client -> autoWeapon().setEnabled(false));
			BuildTestSupport.enable(context, false);
			SurvivalKit.LOGGER.info("[gametest] Oto Silah değneğe dokunmadı (zombiye bakınca seçimler {}, kapalıyken {}; "
				+ "hazırlık slotu açıkken boş, kapalıyken {})", withWand, without, readyOff);
		}
	}

	/** @return every hotbar slot that was selected, in order, while the zombie was aimed at */
	private static List<Integer> aimWithTheWandInHand(ClientGameTestContext context, TestSingleplayerContext world, boolean buildMode) {
		BuildTestSupport.enable(context, buildMode);
		BlockPos at = arena(context, world);
		world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:wooden_sword");
		world.getServer().runCommand("item replace entity @a hotbar.1 with " + SMITE_SWORD);
		context.waitTicks(3);
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		context.waitTick();

		// From before the zombie comes: with nothing aimed at, the crowd alone can call for the sword.
		List<Integer> selections = new ArrayList<>();
		record(context, selections);
		int id = zombie(context, world, at);
		record(context, selections);
		context.runOnClient(client -> client.player.lookAt(EntityAnchorArgument.Anchor.EYES, client.level.getEntity(id).getEyePosition()));

		for (int tick = 0; tick < 20; tick++) {
			context.waitTick();
			record(context, selections);
		}

		if (!context.computeOnClient(client -> client.hitResult instanceof EntityHitResult hit && hit.getEntity().getId() == id)) {
			throw new AssertionError("hazırlık: nişangâh zombinin üstünde değil");
		}

		return selections;
	}

	/**
	 * Bread in hand, the wooden sword the only weapon, in the backpack; ready slot 1; a zombie near.
	 *
	 * @return what is in hotbar slot 1 after two seconds, "" for nothing
	 */
	private static String readySlotWithOnlyTheWand(ClientGameTestContext context, TestSingleplayerContext world, boolean buildMode) {
		BuildTestSupport.enable(context, buildMode);
		BlockPos at = arena(context, world);
		world.getServer().runCommand("item replace entity @a hotbar.4 with minecraft:bread");
		world.getServer().runCommand("item replace entity @a inventory.0 with minecraft:wooden_sword");
		context.waitTicks(3);
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(4));
		setAutoWeapon(context, "prep_slot", 1);
		zombie(context, world, at);

		try {
			context.waitTicks(40);
			return context.computeOnClient(client -> {
				var stack = client.player.getInventory().getItem(0);
				return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			});
		} finally {
			context.runOnClient(client -> autoWeapon().settings().<Setting<?>>byId("prep_slot").orElseThrow().reset());
		}
	}

	private static void record(ClientGameTestContext context, List<Integer> selections) {
		int slot = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());

		if (selections.isEmpty() || slot != selections.getLast()) {
			selections.add(slot);
		}
	}

	/** A floor high up, everything else cleared, the player facing away from where the zombie goes. */
	private static BlockPos arena(ClientGameTestContext context, TestSingleplayerContext world) {
		BlockPos feet = context.computeOnClient(client -> client.player.blockPosition());
		int x = feet.getX();
		int y = 120;
		int z = feet.getZ();

		world.getServer().runCommand("kill @e[type=!minecraft:player]");
		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("fill " + (x - 4) + " " + (y - 1) + " " + (z - 4) + " " + (x + 4) + " " + (y + 4) + " " + (z + 6) + " air");
		world.getServer().runCommand("fill " + (x - 4) + " " + (y - 1) + " " + (z - 4) + " " + (x + 4) + " " + (y - 1) + " " + (z + 6) + " minecraft:stone");
		world.getServer().runCommand("tp @a " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 180 0");
		context.waitTicks(10);
		return new BlockPos(x, y, z);
	}

	/** A still zombie three blocks in front; its id on this client, the newest one. */
	private static int zombie(ClientGameTestContext context, TestSingleplayerContext world, BlockPos at) {
		world.getServer().runCommand("summon minecraft:zombie " + (at.getX() + 0.5) + " " + at.getY() + " " + (at.getZ() + 3.5)
			+ " {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
		context.waitTicks(5);

		return context.computeOnClient(client -> {
			int newest = -1;

			for (Entity entity : client.level.entitiesForRendering()) {
				if (entity instanceof LivingEntity living && living != client.player && living.isAlive()
					&& BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("minecraft:zombie")) {
					newest = Math.max(newest, entity.getId());
				}
			}

			if (newest < 0) {
				throw new AssertionError("hazırlık: zombi istemcide yok");
			}

			return newest;
		});
	}

	@SuppressWarnings("unchecked")
	private static void setAutoWeapon(ClientGameTestContext context, String id, int value) {
		context.runOnClient(client -> autoWeapon().settings().<Setting<Integer>>byId(id)
			.orElseThrow(() -> new AssertionError("ayar yok: " + id))
			.set(value));
	}

	private static Module autoWeapon() {
		return SurvivalKit.modules().byId("autoweapon")
			.orElseThrow(() -> new AssertionError("autoweapon modülü kayıtlı değil"));
	}
}
