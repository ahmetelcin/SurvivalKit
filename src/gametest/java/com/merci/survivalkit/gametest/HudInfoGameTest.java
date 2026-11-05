package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.hud.HudLine;
import com.merci.survivalkit.hud.HudPanel;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.module.impl.HudModule;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import com.merci.survivalkit.scaffold.YLock;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

/**
 * Each module's box on the HUD says the right thing, in a running game, with the real keys: Scaffold's
 * blocks left and locked y as blocks go down, and what its lock key says, on the HUD and — only
 * when asked — on the action bar; the totems carried; Oto Silah's fight lock after a real hit,
 * and off again after its seconds; Damar Kırıcı's job and how far it has got; Dünya İndirici
 * downloading with the chunks it saved, and stopped. Every box with something to say is drawn.
 */
public class HudInfoGameTest implements FabricClientGameTest {
	private static final String COBBLE = "minecraft:cobblestone";
	private static final String TOTEM = "minecraft:totem_of_undying";
	private static final String LOCK_KEY = "key.keyboard.y";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			ScaffoldTestSupport stage = new ScaffoldTestSupport(context, world);
			context.runOnClient(client -> module("hud").setEnabled(true));

			try {
				scaffold(context, stage);
				totems(context, stage);
				weaponLock(context, stage);
				downloader(context);
				vein(context, world);
			} catch (AssertionError e) {
				SurvivalKit.LOGGER.error("[gametest-hata] {}", e.getMessage());
				throw e;
			}
		}

		// A new world: the download that stopped in the last one is no news here.
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(10);

			if (!lines(context, "wdl").isEmpty()) {
				SurvivalKit.LOGGER.error("[gametest-hata] başka bir dünyada önceki indirmenin satırı kalmamalıydı: {}", lines(context, "wdl"));
				throw new AssertionError("başka bir dünyada önceki indirmenin satırı kalmamalıydı: " + lines(context, "wdl"));
			}

			SurvivalKit.LOGGER.info("[gametest] HUD modül bilgileri doğrulandı (İskele sayaç ve Y, kilit tuşu HUD'da, eylem çubuğu yalnız istenince; "
				+ "totem sayısı; Oto Silah kilidi vuruştan sonra ve süresi dolunca; Damar Kırıcı işi ve ilerlemesi; Dünya İndirici indiriliyor/durdu ve chunk sayısı, yeni dünyada durdu satırı yok)");
		}
	}

	/**
	 * Locked at the walkway's level with 64 cobblestone: the box says so before a step; after a
	 * walk out over the gap it says what is left, with the action bar quiet; asked to, the
	 * action bar says the same; the real lock key's line goes on top.
	 */
	private static void scaffold(ClientGameTestContext context, ScaffoldTestSupport stage) {
		int y = stage.start.getY();
		stage.set("y_lock", YLock.FIXED);
		stage.set("fixed_y", y);
		stage.set("y_lock_key", LOCK_KEY);
		stage.stage("hotbar.2", COBBLE + " 64");
		stage.enable(true);
		clearActionBar(context);
		stage.waitTicks(3);

		expect(context, "scaffold", List.of(count(context, 64, y)), "İskele, yürümeden");

		stage.walk(30);
		int placed = (int) stage.gapBlocks(8).stream().filter(COBBLE::equals).count();

		if (placed < 3) {
			throw new AssertionError("hazırlık: köprü kurulmalıydı, konan: " + placed);
		}

		expect(context, "scaffold", List.of(count(context, 64 - placed, y)), "İskele, " + placed + " blok konduktan sonra");
		String bar = stage.actionBar();

		if (!bar.isEmpty()) {
			throw new AssertionError("\"eylem çubuğunda da göster\" kapalıyken eylem çubuğu boş kalmalıydı: " + bar);
		}

		stage.set("action_bar", true);
		stage.waitTicks(3);
		String said = stage.actionBar();
		String line = count(context, 64 - placed, y).text();

		if (!line.equals(said)) {
			throw new AssertionError("\"eylem çubuğunda da göster\" açıkken eylem çubuğu HUD'un satırını söylemeliydi: \"" + said + "\", beklenen \"" + line + "\"");
		}

		stage.set("action_bar", false);
		context.getInput().holdKeyFor(InputConstants.getKey(LOCK_KEY), 2);
		stage.waitTicks(2);
		String locked = context.computeOnClient(client -> Lang.of(Keys.SCAFFOLD_Y_LOCKED, y));
		List<HudLine> lines = lines(context, "scaffold");

		if (lines.isEmpty() || !lines.getFirst().text().equals(locked)) {
			throw new AssertionError("kilit tuşunun satırı HUD'da en üstte olmalıydı: " + lines);
		}

		stage.enable(false);
		expect(context, "scaffold", List.of(), "İskele kapalıyken");
		SurvivalKit.LOGGER.info("[gametest] HUD İskele: {} blok kondu, kutu \"{}\"", placed, line);
	}

	private static HudLine count(ClientGameTestContext context, int left, int y) {
		return context.computeOnClient(client -> HudLine.of(
			Lang.of(Keys.SCAFFOLD_WITH_Y, Lang.of(Keys.SCAFFOLD_COUNT, left), y)));
	}

	/** Three totems, one of them put in the off hand: three. One taken away: two. None: a warning. */
	private static void totems(ClientGameTestContext context, ScaffoldTestSupport stage) {
		stage.stage(null, null);
		stage.command("item replace entity @a hotbar.3 with " + TOTEM);
		stage.command("item replace entity @a hotbar.4 with " + TOTEM);
		stage.command("item replace entity @a inventory.5 with " + TOTEM);
		context.runOnClient(client -> module("autototem").setEnabled(true));
		stage.waitTicks(10);
		String offhand = context.computeOnClient(client -> net.minecraft.core.registries.BuiltInRegistries.ITEM
			.getKey(client.player.getOffhandItem().getItem()).toString());

		if (!TOTEM.equals(offhand)) {
			throw new AssertionError("hazırlık: Otomatik Totem sol ele totem koymalıydı: " + offhand);
		}

		expect(context, "autototem", List.of(totemLine(context, 3, false)), "üç totem");
		stage.command("clear @a " + TOTEM + " 1");
		stage.waitTicks(10);
		expect(context, "autototem", List.of(totemLine(context, 2, false)), "biri alınınca");
		stage.command("clear @a " + TOTEM);
		stage.waitTicks(10);
		expect(context, "autototem", List.of(totemLine(context, 0, true)), "hiç kalmayınca");
		context.runOnClient(client -> module("autototem").setEnabled(false));
	}

	private static HudLine totemLine(ClientGameTestContext context, int count, boolean warning) {
		return context.computeOnClient(client -> new HudLine(Lang.of(Keys.HUD_TOTEMS, count), warning));
	}

	/**
	 * A cow two blocks ahead, an iron sword in hand: not locked; the real attack key on the cow:
	 * locked for the three seconds the setting gives; four seconds on, not locked.
	 */
	private static void weaponLock(ClientGameTestContext context, ScaffoldTestSupport stage) {
		stage.stage("hotbar.0", "minecraft:iron_sword");
		BlockPos at = stage.start;
		stage.put(at.south(), "minecraft:stone");
		stage.put(at.south(2), "minecraft:stone");
		stage.command("summon minecraft:cow " + (at.getX() + 0.5) + " " + (at.getY() + 1) + " " + (at.getZ() + 2.5)
			+ " {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
		context.runOnClient(client -> {
			client.player.setXRot(25.0f);
			module("autoweapon").setEnabled(true);
		});
		stage.waitTicks(10);
		boolean onCow = context.computeOnClient(client -> client.crosshairPickEntity instanceof LivingEntity living
			&& net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).toString().equals("minecraft:cow"));

		if (!onCow) {
			throw new AssertionError("hazırlık: nişangah inekte olmalıydı");
		}

		expect(context, "autoweapon", List.of(lockLine(context, 0)), "vuruştan önce");
		context.getInput().pressKey(options -> options.keyAttack);
		stage.waitTicks(3);
		expect(context, "autoweapon", List.of(lockLine(context, 3)), "vuruştan hemen sonra");
		stage.waitTicks(80);
		expect(context, "autoweapon", List.of(lockLine(context, 0)), "süre dolunca");
		context.runOnClient(client -> {
			module("autoweapon").setEnabled(false);
			client.player.setXRot(0.0f);
		});
		stage.command("kill @e[type=minecraft:cow]");
	}

	private static HudLine lockLine(ClientGameTestContext context, int seconds) {
		return context.computeOnClient(client -> HudLine.of(seconds > 0
			? Lang.of(Keys.HUD_WEAPON_LOCKED, seconds) : Lang.of(Keys.HUD_WEAPON_FREE)));
	}

	/** Switched on: downloading, with the chunks it has saved; off: stopped, with what it saved. */
	private static void downloader(ClientGameTestContext context) {
		context.runOnClient(client -> module("wdl").setEnabled(true));
		context.waitTicks(10);
		int chunks = context.computeOnClient(client -> ((WorldDownloaderModule) module("wdl")).lastSavedChunks());

		if (chunks <= 0) {
			throw new AssertionError("hazırlık: indirme yüklü chunk'ları kaydetmeliydi: " + chunks);
		}

		HudLine on = context.computeOnClient(client -> HudLine.of(Lang.of(Keys.HUD_WDL_ON, chunks)));
		expect(context, "wdl", List.of(on), "indirirken");
		context.runOnClient(client -> module("wdl").setEnabled(false));
		context.waitTicks(3);
		int saved = context.computeOnClient(client -> ((WorldDownloaderModule) module("wdl")).lastSavedChunks());

		// Stopping saves every loaded chunk once more: the same chunks, not new ones.
		if (saved != chunks) {
			throw new AssertionError("durunca yeniden kaydedilen chunk'lar ikinci kez sayılmamalıydı: " + chunks + " -> " + saved);
		}
		HudLine off = context.computeOnClient(client -> HudLine.of(Lang.of(Keys.HUD_WDL_OFF, saved)));
		expect(context, "wdl", List.of(off), "durdurunca");
		SurvivalKit.LOGGER.info("[gametest] HUD Dünya İndirici: {} chunk indirirken, {} durunca", chunks, saved);
	}

	/**
	 * A seven-log tree, Damar Kırıcı slowed to four commands a second, the real key: every tick
	 * of the job the box names the seven blocks and how far it has got, never going back; done,
	 * the box is gone.
	 */
	private static void vein(ClientGameTestContext context, TestSingleplayerContext world) {
		VeinTestSupport vein = new VeinTestSupport(context, world);
		vein.set("commands_per_second", 4);
		vein.clear("minecraft:iron_axe");
		int x = vein.base.getX();
		int z = vein.base.getZ();
		List<BlockPos> logs = vein.tree(x, z);
		vein.standFacing(x, z);
		vein.assertLookingAt(vein.target(x, z));
		vein.press();
		List<Integer> seen = new ArrayList<>();

		for (int tick = 0; tick < 400 && (seen.isEmpty() || vein.busy()); tick++) {
			context.waitTick();
			List<HudLine> lines = lines(context, "veinminer");

			if (lines.isEmpty()) {
				continue;
			}

			String text = lines.getFirst().text();
			int percent = -1;

			for (int p = 0; p <= 100 && percent < 0; p++) {
				int candidate = p;
				String expected = context.computeOnClient(client -> Lang.of(Keys.HUD_VEIN, logs.size(), candidate));

				if (expected.equals(text)) {
					percent = p;
				}
			}

			if (percent < 0) {
				throw new AssertionError("Damar Kırıcı kutusu " + logs.size() + " blok ve yüzde söylemeliydi: " + text);
			}

			drawn(context, "veinminer");
			seen.add(percent);
		}

		vein.waitUntilDone();

		if (seen.size() < 3) {
			throw new AssertionError("iş sürerken kutu birkaç tick görünmeliydi: " + seen);
		}

		for (int i = 1; i < seen.size(); i++) {
			if (seen.get(i) < seen.get(i - 1)) {
				throw new AssertionError("ilerleme geri gitmemeliydi: " + seen);
			}
		}

		if (seen.getLast() <= seen.getFirst()) {
			throw new AssertionError("ilerleme artmalıydı: " + seen);
		}

		expect(context, "veinminer", List.of(), "iş bitince");
		SurvivalKit.LOGGER.info("[gametest] HUD Damar Kırıcı: {} blok, ilerleme {}", logs.size(), seen);
	}

	/** The box says exactly this, and — saying anything — is drawn. */
	private static void expect(ClientGameTestContext context, String panel, List<HudLine> expected, String when) {
		List<HudLine> lines = lines(context, panel);

		if (!lines.equals(expected)) {
			throw new AssertionError(panel + " kutusu (" + when + ") " + expected + " demeliydi, dedi: " + lines);
		}

		if (!expected.isEmpty()) {
			drawn(context, panel);
		}
	}

	private static void drawn(ClientGameTestContext context, String panel) {
		context.waitTick();

		if (context.computeOnClient(client -> SurvivalKit.hud().drawn(panel)) == null) {
			throw new AssertionError(panel + " kutusu ekrana çizilmeliydi");
		}
	}

	private static List<HudLine> lines(ClientGameTestContext context, String panel) {
		return context.computeOnClient(client -> ((HudModule) module("hud")).panels().stream()
			.filter(candidate -> candidate.id().equals(panel))
			.findFirst()
			.map(HudPanel::lines)
			.orElseThrow(() -> new AssertionError("HUD'da kutu yok: " + panel)));
	}

	private static void clearActionBar(ClientGameTestContext context) {
		context.runOnClient(client -> client.gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.empty(), false));
	}

	private static Module module(String id) {
		return SurvivalKit.modules().byId(id).orElseThrow(() -> new AssertionError("modül yok: " + id));
	}
}
