package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import java.util.Arrays;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Drawn maps come through the download with the same picture: one hanging in a frame the
 * player can see, one held in the hand.
 *
 * <p>The picture is compared pixel for pixel against what the client had on screen. The
 * downloaded map has to be locked too: the client never learns where a map was drawn, and an
 * unlocked one would be drawn over the first time it was held.
 */
public class MapDownloadGameTest implements FabricClientGameTest {
	private static final int DRAW_TICKS = 100;

	@Override
	public void runTest(ClientGameTestContext context) {
		byte[][] pictures = new byte[2][];
		int[] ids = new int[2];

		WdlTestSupport.downloadThenReopen(context, world -> {
			context.waitTicks(5);
			// Survival: in creative the filled map goes to another slot and the hand keeps
			// the empty one. Floors laid first, so nobody falls on arrival.
			world.getServer().runCommand("gamemode survival @a");
			platform(context, world, 0, 0);
			platform(context, world, 300, 300);
			world.getServer().runCommand("tp @a 0.5 120 0.5 0 90");
			context.waitTicks(20);

			// The first map: drawn in the hand, then hung in a frame and taken out of the
			// inventory, so the download can only have it from the frame.
			ids[0] = drawAMap(context, world);
			world.getServer().runCommand("setblock 0 118 3 minecraft:stone");
			world.getServer().runCommand("summon minecraft:item_frame 0 118 2 {Facing:2b,Item:{id:\"minecraft:filled_map\",count:1,"
					+ "components:{\"minecraft:map_id\":" + ids[0] + "}}}");
			world.getServer().runCommand("clear @a");
			context.waitTicks(40);

			// The second: drawn somewhere else and still in the hand.
			world.getServer().runCommand("tp @a 300.5 120 300.5 0 90");
			context.waitTicks(40);
			ids[1] = drawAMap(context, world);
			world.getServer().runCommand("tp @a 0.5 120 0.5 0 20");
			context.waitTicks(40);

			pictures[0] = picture(context, ids[0]);
			pictures[1] = picture(context, ids[1]);

			if (ids[0] == ids[1]) {
				throw new AssertionError("hazırlık başarısız: iki harita aynı id'yi aldı: " + ids[0]);
			}

			WdlTestSupport.setEnabled(context, true);
			context.waitTicks(30);
			WdlTestSupport.setEnabled(context, false);
		}, reopened -> reopened.getServer().runOnServer(server -> {
			for (int which = 0; which < 2; which++) {
				MapItemSavedData data = server.overworld().getMapData(new MapId(ids[which]));
				String where = which == 0 ? "çerçevedeki" : "eldeki";

				if (data == null) {
					throw new AssertionError(where + " harita (id " + ids[which] + ") indirilen dünyada yok");
				}

				if (!Arrays.equals(data.colors, pictures[which])) {
					throw new AssertionError(where + " haritanın görüntüsü değişmiş (id " + ids[which] + "), farklı piksel: "
							+ differing(data.colors, pictures[which]));
				}

				if (!data.locked) {
					throw new AssertionError(where + " harita kilitli yazılmalıydı, yoksa ilk tutuşta üstüne çizilir");
				}
			}

			int next = server.overworld().getFreeMapId().id();

			if (next <= Math.max(ids[0], ids[1])) {
				throw new AssertionError("yeni harita indirilenin üstüne yazar: sıradaki id " + next);
			}
		}));

		SurvivalKit.LOGGER.info("[gametest] haritalar indirildi: çerçevedeki (id {}) ve eldeki (id {}) aynı görüntüyle, kilitli",
				ids[0], ids[1]);
	}

	private static void platform(ClientGameTestContext context, TestSingleplayerContext world, int x, int z) {
		world.getServer().runCommand("forceload add " + x + " " + z);
		context.waitTicks(20);
		world.getServer().runCommand("fill " + (x - 2) + " 119 " + (z - 2) + " " + (x + 2) + " 119 " + (z + 4) + " minecraft:stone");
	}

	/** An empty map, used, then held until the picture around it is drawn in. */
	private static int drawAMap(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:map");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		context.waitTicks(5);
		context.runOnClient(client -> client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND));
		context.waitTicks(DRAW_TICKS);

		MapId id = context.computeOnClient(client -> client.player.getMainHandItem().get(DataComponents.MAP_ID));

		if (id == null) {
			throw new AssertionError("hazırlık başarısız: boş harita doldurulmadı");
		}

		long drawn = context.computeOnClient(client -> {
			MapItemSavedData data = client.level.getMapData(id);
			return data == null ? 0L : countDrawn(data.colors);
		});

		if (drawn < 1000) {
			throw new AssertionError("hazırlık başarısız: harita çizilmedi, dolu piksel " + drawn);
		}

		return id.id();
	}

	private static byte[] picture(ClientGameTestContext context, int id) {
		byte[] colors = context.computeOnClient(client -> {
			MapItemSavedData data = client.level.getMapData(new MapId(id));
			return data == null ? null : data.colors.clone();
		});

		if (colors == null) {
			throw new AssertionError("hazırlık başarısız: istemci " + id + " numaralı haritayı bilmiyor");
		}

		return colors;
	}

	private static long countDrawn(byte[] colors) {
		long drawn = 0;

		for (byte color : colors) {
			if (color != 0) {
				drawn++;
			}
		}

		return drawn;
	}

	private static int differing(byte[] a, byte[] b) {
		int count = 0;

		for (int i = 0; i < Math.min(a.length, b.length); i++) {
			if (a[i] != b[i]) {
				count++;
			}
		}

		return count + Math.abs(a.length - b.length);
	}
}
