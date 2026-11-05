package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What is inside a donkey's and a llama's saddle bags, and a chest and a hopper minecart,
 * comes through the download once the player has opened them — and a horse keeps its saddle
 * and armour.
 *
 * <p>The contents are put there by command, which reaches the server only: the client never
 * sees them until the screen is open. The animals are opened the way a player opens them, by
 * riding and pressing the inventory key; the carts by clicking them.
 */
public class MountInventoryGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		UUID[] ids = new UUID[5];

		WdlTestSupport.downloadThenReopen(context, world -> {
			context.waitTicks(5);
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("fill -6 119 -6 6 119 6 minecraft:stone");
			world.getServer().runCommand("fill -6 120 -6 6 123 6 minecraft:air");
			world.getServer().runCommand("tp @a 0.5 120 0.5 0 0");
			context.waitTicks(20);

			ids[0] = summon(context, world, "donkey", 3, 0, "{Tame:1b,ChestedHorse:1b,NoAI:1b}");
			ids[1] = summon(context, world, "llama", -3, 0, "{Tame:1b,ChestedHorse:1b,Strength:3,NoAI:1b}");
			ids[2] = summon(context, world, "horse", 0, 3, "{Tame:1b,NoAI:1b}");
			ids[3] = summon(context, world, "chest_minecart", 3, 3, "{}");
			ids[4] = summon(context, world, "hopper_minecart", -3, 3, "{}");

			world.getServer().runCommand("item replace entity " + ids[0] + " horse.0 with minecraft:diamond 5");
			world.getServer().runCommand("item replace entity " + ids[1] + " horse.2 with minecraft:emerald 9");
			world.getServer().runCommand("item replace entity " + ids[2] + " saddle with minecraft:saddle");
			world.getServer().runCommand("item replace entity " + ids[2] + " armor.body with minecraft:diamond_horse_armor");
			world.getServer().runCommand("item replace entity " + ids[3] + " container.4 with minecraft:gold_ingot 7");
			world.getServer().runCommand("item replace entity " + ids[4] + " container.1 with minecraft:redstone 3");
			context.waitTicks(10);

			WdlTestSupport.setEnabled(context, true);

			rideAndOpen(context, world, ids[0]);
			rideAndOpen(context, world, ids[1]);
			clickOpen(context, ids[3]);
			clickOpen(context, ids[4]);

			WdlTestSupport.setEnabled(context, false);
		}, reopened -> reopened.getServer().runOnServer(server -> {
			expect(horseSlot(server, ids[0], 0), Items.DIAMOND, 5, "eşeğin heybesi");
			expect(horseSlot(server, ids[1], 2), Items.EMERALD, 9, "lamanın heybesi");
			expect(((LivingEntity) entity(server, ids[2])).getItemBySlot(EquipmentSlot.SADDLE), Items.SADDLE, 1, "atın eyeri");
			expect(((LivingEntity) entity(server, ids[2])).getItemBySlot(EquipmentSlot.BODY), Items.DIAMOND_HORSE_ARMOR, 1, "atın zırhı");
			expect(((AbstractMinecartContainer) entity(server, ids[3])).getItem(4), Items.GOLD_INGOT, 7, "sandıklı vagon");
			expect(((AbstractMinecartContainer) entity(server, ids[4])).getItem(1), Items.REDSTONE, 3, "hunili vagon");
		}));

		SurvivalKit.LOGGER.info("[gametest] binek ve vagon envanterleri indirildi: eşek, lama heybesi, at eyeri ve zırhı, "
				+ "sandıklı ve hunili vagon");
	}

	private static UUID summon(ClientGameTestContext context, TestSingleplayerContext world, String type, int x, int z, String nbt) {
		world.getServer().runCommand("summon minecraft:" + type + " " + (x + 0.5) + " 120 " + (z + 0.5) + " " + nbt);
		context.waitTicks(2);

		return world.getServer().computeOnServer(server -> server.overworld()
				.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Entity.class),
						entity -> entity.getType().builtInRegistryHolder().key().identifier().getPath().equals(type))
				.getLast().getUUID());
	}

	/** Mounted, then the inventory key, which opens the animal's inventory rather than the player's. */
	private static void rideAndOpen(ClientGameTestContext context, TestSingleplayerContext world, UUID id) {
		world.getServer().runCommand("ride @a[limit=1] mount " + id);
		context.waitTicks(10);
		context.getInput().pressKey(options -> options.keyInventory);
		context.waitTicks(10);
		assertAContainerIsOpen(context, id);
		context.runOnClient(client -> client.player.closeContainer());
		context.waitTicks(5);
		world.getServer().runCommand("ride @a[limit=1] dismount");
		world.getServer().runCommand("tp @a 0.5 120 0.5 0 0");
		context.waitTicks(10);
	}

	/** Looked at and clicked with the use key, the way a player opens a cart. */
	private static void clickOpen(ClientGameTestContext context, UUID id) {
		context.runOnClient(client -> client.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
				clientEntity(client, id).getBoundingBox().getCenter()));
		context.waitTicks(3);

		boolean aimed = context.computeOnClient(client -> client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
				&& hit.getEntity().getUUID().equals(id));

		if (!aimed) {
			throw new AssertionError("hazırlık başarısız: nişangâh vagonda değil: " + context.computeOnClient(client -> client.hitResult));
		}

		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(10);
		assertAContainerIsOpen(context, id);
		context.runOnClient(client -> client.player.closeContainer());
		context.waitTicks(5);
	}

	private static void assertAContainerIsOpen(ClientGameTestContext context, UUID id) {
		if (!context.computeOnClient(client -> client.gui.screen() instanceof AbstractContainerScreen<?>)) {
			throw new AssertionError("hazırlık başarısız: envanter açılmadı: " + id);
		}
	}

	private static Entity clientEntity(net.minecraft.client.Minecraft client, UUID id) {
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity.getUUID().equals(id)) {
				return entity;
			}
		}

		throw new AssertionError("istemci varlığı görmüyor: " + id);
	}

	private static Entity entity(MinecraftServer server, UUID id) {
		for (int attempt = 0; attempt < 1; attempt++) {
			Entity entity = server.overworld().getEntity(id);

			if (entity != null) {
				return entity;
			}
		}

		throw new AssertionError("indirilen dünyada varlık yok: " + id);
	}

	private static ItemStack horseSlot(MinecraftServer server, UUID id, int index) {
		return ((AbstractHorse) entity(server, id)).getSlot(AbstractHorse.INVENTORY_SLOT_OFFSET + index).get();
	}

	private static void expect(ItemStack actual, Item item, int count, String what) {
		if (!actual.is(item) || actual.getCount() != count) {
			throw new AssertionError(what + ": " + count + " " + item + " bekleniyordu, bulunan " + actual);
		}
	}
}
