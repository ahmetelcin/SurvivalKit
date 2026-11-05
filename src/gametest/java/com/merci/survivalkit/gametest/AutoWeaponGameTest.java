package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.BoolSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Oto Silah in a running game: real aim — the player turns onto the mob — and the real attack
 * key. How much health the mob loses to that one hit says which weapon it was hit with: an
 * iron sword does 6, with Sharpness V 9, and with Smite V or Bane V against the right target 18.5.
 */
public class AutoWeaponGameTest implements FabricClientGameTest {
	private static final int PLAIN = 0;
	private static final int SMITE = 1;
	private static final int BANE = 2;
	private static final int SHARP = 3;

	private static final List<String> HOTBAR = List.of(
		"minecraft:iron_sword",
		"minecraft:iron_sword[minecraft:enchantments={\"minecraft:smite\":5}]",
		"minecraft:iron_sword[minecraft:enchantments={\"minecraft:bane_of_arthropods\":5}]",
		"minecraft:iron_sword[minecraft:enchantments={\"minecraft:sharpness\":5}]");

	/** How far off the ground a phantom is put: in the air, its body level with the player's eyes. */
	private static final double FLYING_HEIGHT = 1.5;

	/** Scale of each hit this client made, as the attack event saw it; auto hit is judged on it. */
	private static final List<Float> STRENGTH_AT_HIT = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide()) {
				STRENGTH_AT_HIT.add(player.getAttackStrengthScale(0.0f));
			}

			return InteractionResult.PASS;
		});

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(5);
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set midnight");
			world.getServer().runCommand("gamerule doMobSpawning false");
			world.getServer().runCommand("effect give @a minecraft:resistance 600 4 true");

			Map<String, String> seen = new LinkedHashMap<>();
			List<String> wrong = new ArrayList<>();

			// The aim and ready-slot scenarios measure those alone. The fight lock is on by default
			// and rightly holds everything after a hit — the auto hit's included — so it is off for
			// them and the lock scenarios at the end set it themselves.
			setInt(context, "lock_seconds", 0);

			for (Object[] pair : new Object[][] {
				{"minecraft:zombie", SMITE, 18.5f},
				{"minecraft:spider", BANE, 18.5f},
				// Flying: in the air, off the ground, the way phantoms come at you.
				{"minecraft:phantom", SMITE, 18.5f},
				{"minecraft:cow", SHARP, 9.0f}}) {
				String mob = (String) pair[0];
				int slot = (int) pair[1];
				float damage = (float) pair[2];

				enable(context, true);
				aimAndHit(context, world, mob, "minecraft:iron_sword", List.of(PLAIN, slot), damage, seen, wrong, "açık");

				// Negative control: the same aim and the same key with the module off. The plain
				// sword stays in hand and the hit lands for 6, not for what the right one would do.
				enable(context, false);
				aimAndHit(context, world, mob, "minecraft:iron_sword", List.of(PLAIN), 6.0f, seen, wrong, "kapalı");
			}

			// Without a weapon in hand nothing is taken out, whatever is aimed at.
			enable(context, true);
			aimAndHit(context, world, "minecraft:zombie", "minecraft:bread", List.of(PLAIN), -1, seen, wrong, "ekmek");

			long logBeforeAutoHit = logSize();
			autoHit(context, world, seen, wrong);
			watchdog(context, world, seen, wrong, logBeforeAutoHit);
			prepSlot(context, world, seen, wrong);
			fightLock(context, world, seen, wrong);
			crowd(context, world, seen, wrong);
			enable(context, false);

			if (!wrong.isEmpty()) {
				throw new AssertionError("Oto Silah: " + String.join(" | ", wrong));
			}

			SurvivalKit.LOGGER.info("[gametest] Oto Silah nişanda doğrulandı {}", seen);
		}
	}

	/**
	 * Faces away, turns onto the mob, watches the selection, then presses attack once the
	 * cooldown is full and reads the mob's lost health.
	 *
	 * @param damage what the hit should take off, or -1 not to hit at all
	 */
	private static void aimAndHit(ClientGameTestContext context, TestSingleplayerContext world, String mob, String hand,
			List<Integer> expected, float damage, Map<String, String> seen, List<String> wrong, String label) {
		BlockPos at = arena(context, world, hand);
		int id = summon(context, world, mob, at);

		List<Integer> history = new ArrayList<>();
		record(context, history);
		turnOnto(context, id);

		for (int i = 0; i < 10; i++) {
			context.waitTick();
			record(context, history);
		}

		String what = label + " " + mob;

		// A set-up check, not a result: an aim that missed would make "nothing switched" pass
		// for the wrong reason, which is exactly what happened before the aim was fixed.
		if (!aimedAt(context, id)) {
			wrong.add(what + ": hazırlık — nişangah mobun üstünde değil");
			return;
		}

		if (!history.equals(expected)) {
			wrong.add(what + ": seçim beklenen " + expected + ", olan " + history);
		}

		if (damage < 0) {
			seen.put(what, "seçim " + history);
			return;
		}

		context.waitTicks(25);
		float before = health(context, id);
		context.getInput().pressKey(options -> options.keyAttack);
		// The health read here is the client's copy: the hit goes to the server and the new health
		// comes back. Five ticks were enough until five clients ran side by side and one read 0.0,
		// so the wait is for the change itself, with room to spare.
		for (int i = 0; i < 40 && health(context, id) == before; i++) {
			context.waitTick();
		}
		float lost = before - health(context, id);

		float expectedLoss = Math.min(afterArmour(mob, damage), before);
		seen.put(what, "seçim " + history + ", can kaybı " + lost);

		if (Math.abs(lost - expectedLoss) > 0.01f) {
			wrong.add(what + ": vuruş " + expectedLoss + " götürmeliydi, götürdü " + lost);
		}
	}

	/**
	 * Auto hit on an iron golem, which has health enough to be hit for a while: with the setting
	 * off nothing is hit in three seconds of aiming; on, it is hit, and every hit at full strength.
	 */
	private static void autoHit(ClientGameTestContext context, TestSingleplayerContext world, Map<String, String> seen,
			List<String> wrong) {
		for (boolean on : new boolean[] {false, true}) {
			BlockPos at = arena(context, world, "minecraft:iron_sword");
			int golem = summon(context, world, "minecraft:iron_golem", at);
			setAutoAttack(context, on);
			turnOnto(context, golem);
			STRENGTH_AT_HIT.clear();

			// Kept on it: a hit knocks it back a little, and the aim should not drift off.
			for (int i = 0; i < 60; i++) {
				turnOnto(context, golem);
				context.waitTick();
			}

			if (!aimedAt(context, golem)) {
				wrong.add("otomatik vur: hazırlık — nişangah golemin üstünde değil");
			}

			List<Float> hits = List.copyOf(STRENGTH_AT_HIT);
			seen.put("otomatik vur " + (on ? "açık" : "kapalı"), "vuruş anındaki güç " + hits);

			if (!on && !hits.isEmpty()) {
				wrong.add("otomatik vur kapalıyken vurmamalıydı, vuruş: " + hits);
			}

			if (on && hits.size() < 2) {
				wrong.add("otomatik vur açıkken 3 saniyede en az 2 kez vurmalıydı, vuruş: " + hits);
			}

			if (hits.stream().anyMatch(strength -> strength < 1.0f)) {
				wrong.add("otomatik vur bekleme süresi dolmadan vurdu: " + hits);
			}
		}

		setAutoAttack(context, false);
	}

	/**
	 * The watchdog, in the log a real game writes. The ordinary auto hit above left no stall
	 * line; with Mining Fatigue the cooldown never fills, the auto hit waits on it for good, and
	 * after two seconds the log says so.
	 */
	private static void watchdog(ClientGameTestContext context, TestSingleplayerContext world, Map<String, String> seen,
			List<String> wrong, long logBefore) {
		if (stallLines(logBefore) != 0) {
			wrong.add("bekçi: olağan otomatik vurda takılma yazılmamalıydı");
		}

		BlockPos at = arena(context, world, "minecraft:iron_sword");
		int golem = summon(context, world, "minecraft:iron_golem", at);
		world.getServer().runCommand("effect give @a minecraft:mining_fatigue 30 20 true");
		setAutoAttack(context, true);
		long before = logSize();

		for (int i = 0; i < 60; i++) {
			turnOnto(context, golem);
			context.waitTick();
		}

		setAutoAttack(context, false);
		world.getServer().runCommand("effect clear @a minecraft:mining_fatigue");
		long lines = stallLines(before);
		seen.put("bekçi", "yorgunlukta takılma satırı " + lines);

		if (lines != 1) {
			wrong.add("bekçi: bekleme 3 sn dolmadı, log'da 1 takılma satırı olmalıydı, olan " + lines);
		}
	}

	private static java.nio.file.Path latestLog() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs/latest.log");
	}

	private static long logSize() {
		try {
			return java.nio.file.Files.size(latestLog());
		} catch (java.io.IOException e) {
			throw new AssertionError("latest.log okunamadı", e);
		}
	}

	/** The watchdog's stall lines written after this many bytes of the log. */
	private static long stallLines(long from) {
		try (java.io.InputStream in = java.nio.file.Files.newInputStream(latestLog())) {
			in.skipNBytes(from);
			return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines()
				.filter(line -> line.contains("durumunda takılı"))
				.count();
		} catch (java.io.IOException e) {
			throw new AssertionError("latest.log okunamadı", e);
		}
	}

	// --- step 3: the ready slot ---

	/** "Slot 5" in the settings; index 4 in the inventory. */
	private static final int PREP_SETTING = 5;
	private static final int PREP = PREP_SETTING - 1;

	/**
	 * Bread in hand, the Smite and Bane swords in the backpack, the player facing away from the
	 * mob — nothing is aimed at. The best sword for the mob has to turn up in slot 5 with the
	 * bread still in hand; with the module off, or the mob out of range, the slot stays empty.
	 */
	private static void prepSlot(ClientGameTestContext context, TestSingleplayerContext world, Map<String, String> seen,
			List<String> wrong) {
		setInt(context, "prep_slot", PREP_SETTING);
		setInt(context, "range", 8);
		setBool(context, "select_prep_slot", false);

		readyFor(context, world, "açık zombi 5 blok", "minecraft:zombie", 0, 5, true, "smite", true, seen, wrong);
		readyFor(context, world, "açık örümcek 5 blok", "minecraft:spider", 0, 5, true, "bane_of_arthropods", true, seen, wrong);
		readyFor(context, world, "kapalı zombi 5 blok", "minecraft:zombie", 0, 5, false, "", true, seen, wrong);
		readyFor(context, world, "açık zombi 12 blok (menzil 8)", "minecraft:zombie", 0, 12, true, "", true, seen, wrong);
		// Diagonally 7 and 7 away: inside the square the client first gathers from, but 9.9 blocks
		// off — only the real distance keeps it out. Straight ahead at 12 never tested that.
		readyFor(context, world, "açık zombi çapraz 7,7 = 9,9 blok (menzil 8)", "minecraft:zombie", 7, 7, true, "", true, seen, wrong);

		setBool(context, "select_prep_slot", true);
		readyFor(context, world, "slotu da seç, zombi 5 blok", "minecraft:zombie", 0, 5, true, "smite", false, seen, wrong);

		setBool(context, "select_prep_slot", false);
		setInt(context, "prep_slot", 0);
	}

	/**
	 * @param enchantment what the sword in the ready slot should carry, or "" for an empty slot
	 * @param breadStays  whether the bread should still be in hand afterwards
	 */
	private static void readyFor(ClientGameTestContext context, TestSingleplayerContext world, String label, String mob,
			int dx, int distance, boolean on, String enchantment, boolean breadStays, Map<String, String> seen, List<String> wrong) {
		BlockPos at = arena(context, world, "minecraft:bread");
		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("item replace entity @a hotbar." + PLAIN + " with minecraft:bread");
		world.getServer().runCommand("item replace entity @a inventory.11 with " + HOTBAR.get(SMITE));
		world.getServer().runCommand("item replace entity @a inventory.12 with " + HOTBAR.get(BANE));
		enable(context, on);
		context.waitTicks(3);

		world.getServer().runCommand("summon " + mob + " " + (at.getX() + 0.5 + dx) + " " + at.getY() + " " + (at.getZ() + 0.5 + distance)
			+ " {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
		// The slot follows the crowd, and a crowd counts once it has held for a second.
		context.waitTicks(30);

		String inSlot = context.computeOnClient(client -> describe(client.player.getInventory().getItem(PREP)));
		int selected = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		String inHand = context.computeOnClient(client -> describe(client.player.getMainHandItem()));
		boolean aimed = context.computeOnClient(client -> client.hitResult instanceof net.minecraft.world.phys.EntityHitResult);
		seen.put("hazırlık " + label, "slot 5: " + inSlot + ", elde: " + inHand);

		if (aimed) {
			wrong.add("hazırlık " + label + ": hazırlık — mob nişangahta olmamalıydı");
		}

		boolean slotRight = enchantment.isEmpty() ? inSlot.equals("boş") : inSlot.contains(enchantment);

		if (!slotRight) {
			wrong.add("hazırlık " + label + ": slot 5'te beklenen " + (enchantment.isEmpty() ? "boş" : enchantment) + ", olan " + inSlot);
		}

		if (breadStays && (selected != PLAIN || !inHand.equals("minecraft:bread"))) {
			wrong.add("hazırlık " + label + ": eldeki ekmek değişmemeliydi, elde " + inHand + " (slot " + selected + ")");
		}

		if (!breadStays && selected != PREP) {
			wrong.add("hazırlık " + label + ": slot 5 seçilmeliydi, seçili " + selected);
		}
	}

	// --- step 4: the fight lock ---

	/** A Sharpness sword that is a different item from the iron Smite sword: switching resets the cooldown. */
	private static final String DIAMOND_SHARP = "minecraft:diamond_sword[minecraft:enchantments={\"minecraft:sharpness\":5}]";

	/**
	 * The lock holds back only a switch that would throw the attack cooldown away: one to a
	 * different item, while the cooldown is still filling. Each scenario aims at a cow — the
	 * Sharpness sword comes out — hits it with the attack key, turns onto an undead mob, and reads
	 * the selection twice: soon after the hit with the cooldown still filling, and once it is
	 * full but the three-second lock still runs.
	 *
	 * <p>The first is the report from the game: phantoms, two iron swords, and the Sharpness one
	 * stuck in hand all fight long.
	 */
	private static void fightLock(ClientGameTestContext context, TestSingleplayerContext world, Map<String, String> seen,
			List<String> wrong) {
		enable(context, true);
		setInt(context, "prep_slot", 0);

		afterAHit(context, world, "aynı eşya (demir→demir), phantom, kilit 3 sn", 3, true, false, "minecraft:phantom",
			SMITE, SMITE, seen, wrong);
		afterAHit(context, world, "farklı eşya (elmas→demir), kilit 3 sn", 3, true, true, "minecraft:zombie",
			SHARP, SMITE, seen, wrong);
		afterAHit(context, world, "farklı eşya, kilit 0 (negatif kontrol)", 0, true, true, "minecraft:zombie",
			SMITE, SMITE, seen, wrong);
		afterAHit(context, world, "farklı eşya, nişanda kapalı (negatif kontrol)", 3, false, true, "minecraft:zombie",
			SMITE, SMITE, seen, wrong);

		afterBeingHurt(context, world, "hasar, kilit 3 sn", seen, wrong);

		setInt(context, "lock_seconds", 3);
		setBool(context, "lock_aim", true);
		setInt(context, "prep_slot", 0);
	}

	/**
	 * @param diamond   the Sharpness sword is a diamond one, a different item from the iron Smite sword
	 * @param filling   the slot expected with the cooldown still filling, a few ticks after the hit
	 * @param full      the slot expected once the cooldown is full, the lock still running
	 */
	private static void afterAHit(ClientGameTestContext context, TestSingleplayerContext world, String label, int seconds,
			boolean lockAim, boolean diamond, String mob, int filling, int full, Map<String, String> seen, List<String> wrong) {
		setInt(context, "lock_seconds", seconds);
		setBool(context, "lock_aim", lockAim);

		BlockPos at = arena(context, world, "minecraft:iron_sword");

		if (diamond) {
			world.getServer().runCommand("item replace entity @a hotbar." + SHARP + " with " + DIAMOND_SHARP);
		}

		// Both well inside the 3-block reach: 1.2 to the side and 2.5 ahead is 2.8 blocks to the
		// centre. The zombie once stood 2 and 3 out — 3.6 — and the aim missed it half the time.
		int cow = summonAt(context, world, "minecraft:cow", at, -1.2, 2.5);
		int undead = summonAt(context, world, mob, at, 1.2, 2.5);

		turnOnto(context, cow);
		context.waitTicks(30);

		if (!aimedAt(context, cow) || selected(context) != SHARP) {
			wrong.add("kilit " + label + ": hazırlık — ineğe nişan alınıp Keskinlik seçilmeliydi, seçili " + selected(context));
			return;
		}

		float before = health(context, cow);
		context.getInput().pressKey(options -> options.keyAttack);
		long hitAt = time(context);
		context.waitTicks(3);

		if (health(context, cow) >= before) {
			wrong.add("kilit " + label + ": hazırlık — inek vurulmalıydı");
			return;
		}

		turnOnto(context, undead);
		context.waitTicks(3);

		if (!aimedAt(context, undead)) {
			wrong.add("kilit " + label + ": hazırlık — " + mob + " nişangahta olmalıydı");
			return;
		}

		int whileFilling = selected(context);
		boolean wasFilling = context.computeOnClient(client -> client.player.getAttackStrengthScale(0.0f) < 1.0f);

		while (time(context) < hitAt + 30) {
			turnOnto(context, undead);
			context.waitTick();
		}

		int whenFull = selected(context);
		boolean stillLocked = time(context) < hitAt + seconds * 20L;
		seen.put("kilit " + label, "bekleme dolmadan " + whileFilling + ", dolunca " + whenFull);

		if (!wasFilling) {
			wrong.add("kilit " + label + ": hazırlık — ilk okumada bekleme süresi dolmamış olmalıydı");
		}

		if (seconds > 0 && !stillLocked) {
			wrong.add("kilit " + label + ": hazırlık — ikinci okumada kilit sürüyor olmalıydı");
		}

		if (whileFilling != filling) {
			wrong.add("kilit " + label + ": bekleme dolmadan beklenen slot " + filling + ", olan " + whileFilling);
		}

		if (whenFull != full) {
			wrong.add("kilit " + label + ": bekleme dolunca beklenen slot " + full + ", olan " + whenFull);
		}
	}

	/**
	 * A hit taken starts the lock too, but filling the ready slot never touches the hand, so it
	 * costs the cooldown nothing and goes ahead while locked: bread in hand, the player hurt with a
	 * command, a zombie in range — the Smite sword turns up in slot 5 inside the three seconds.
	 */
	private static void afterBeingHurt(ClientGameTestContext context, TestSingleplayerContext world, String label,
			Map<String, String> seen, List<String> wrong) {
		setInt(context, "lock_seconds", 3);
		setInt(context, "prep_slot", PREP_SETTING);

		BlockPos at = arena(context, world, "minecraft:bread");
		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("item replace entity @a hotbar." + PLAIN + " with minecraft:bread");
		world.getServer().runCommand("item replace entity @a inventory.11 with " + HOTBAR.get(SMITE));
		world.getServer().runCommand("effect clear @a minecraft:resistance");
		world.getServer().runCommand("effect give @a minecraft:instant_health 1 5 true");
		context.waitTicks(5);

		// @p, not @a: /damage takes a single target and refuses @a outright. It did, silently, and
		// a lock left over from the scenario before let this one pass without any hit at all.
		float healthBefore = context.computeOnClient(client -> client.player.getHealth());
		world.getServer().runCommand("damage @p 2 minecraft:generic");
		long hurtAt = time(context);
		context.waitTicks(3);
		float healthAfter = context.computeOnClient(client -> client.player.getHealth());
		seen.put(label + " can", healthBefore + " -> " + healthAfter);

		if (healthAfter >= healthBefore) {
			wrong.add(label + ": hazırlık — oyuncu hasar almalıydı, can " + healthBefore + " -> " + healthAfter);
			return;
		}

		summonAt(context, world, "minecraft:zombie", at, 0.0, 5.0);
		context.waitTicks(30);

		String during = context.computeOnClient(client -> describe(client.player.getInventory().getItem(PREP)));
		boolean stillLocked = time(context) < hurtAt + 3 * 20L;
		seen.put(label, "kilitte slot 5: " + during);

		if (!stillLocked) {
			wrong.add(label + ": hazırlık — okuma kilit sürerken yapılmalıydı");
		}

		if (!during.contains("smite")) {
			wrong.add(label + ": kilitte de slot 5'e smite gelmeliydi, olan " + during);
		}

		world.getServer().runCommand("effect give @a minecraft:resistance 600 4 true");
	}

	private static int summonAt(ClientGameTestContext context, TestSingleplayerContext world, String mob, BlockPos at,
			double dx, double dz) {
		boolean flying = mob.equals("minecraft:phantom");
		world.getServer().runCommand("summon " + mob + " " + (at.getX() + 0.5 + dx) + " " + (at.getY() + (flying ? FLYING_HEIGHT : 0))
			+ " " + (at.getZ() + 0.5 + dz)
			+ " {NoAI:1b,PersistenceRequired:1b,Silent:1b" + (flying ? ",NoGravity:1b" : "") + "}");
		context.waitTicks(5);

		return context.computeOnClient(client -> {
			int newest = -1;

			for (Entity entity : client.level.entitiesForRendering()) {
				if (entity instanceof LivingEntity living && living != client.player && living.isAlive()
						&& net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(mob)) {
					newest = Math.max(newest, entity.getId());
				}
			}

			return newest;
		});
	}

	private static int selected(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
	}

	private static long time(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.level.getGameTime());
	}

	/** The item's id and its enchantments' ids, or "boş". */
	private static String describe(net.minecraft.world.item.ItemStack stack) {
		if (stack.isEmpty()) {
			return "boş";
		}

		StringBuilder text = new StringBuilder(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		stack.getEnchantments().keySet().forEach(holder -> text.append(" ").append(holder.getRegisteredName()));
		return text.toString();
	}

	private static void setInt(ClientGameTestContext context, String id, int value) {
		context.runOnClient(client -> module().settings()
			.<com.merci.survivalkit.settings.IntSetting>byId(id)
			.orElseThrow(() -> new AssertionError("ayar yok: " + id))
			.set(value));
	}

	private static void setBool(ClientGameTestContext context, String id, boolean value) {
		context.runOnClient(client -> module().settings()
			.<BoolSetting>byId(id)
			.orElseThrow(() -> new AssertionError("ayar yok: " + id))
			.set(value));
	}

	// --- the crowd ---

	/**
	 * Nothing aimed at — the player faces away from everything — and around: a spider nearest,
	 * three zombies behind it, two cows and two villagers nearer than the zombies, and three
	 * spiders beyond the 8-block range. Only hostile mobs in range count, three zombies to one
	 * spider, and the Smite sword comes out. Counting the cows and villagers would make it
	 * Sharpness, counting the far spiders or going by the nearest mob would make it Bane.
	 *
	 * <p>Then the spider is aimed at and Bane comes out; turning away brings Smite back.
	 *
	 * <p>Then four more spiders step in for half a second and are gone: the hand does not move.
	 * Last, the same four stay — and Bane comes out, but not before a second: the counterpart
	 * that shows the wait is a wait and not a refusal.
	 */
	private static void crowd(ClientGameTestContext context, TestSingleplayerContext world, Map<String, String> seen,
			List<String> wrong) {
		enable(context, true);
		setInt(context, "lock_seconds", 0);
		setInt(context, "prep_slot", 0);
		setInt(context, "range", 8);
		context.runOnClient(client -> module().settings()
			.<com.merci.survivalkit.settings.EnumSetting<com.merci.survivalkit.weapon.WeaponSource>>byId("source")
			.orElseThrow(() -> new AssertionError("ayar yok: source"))
			.set(com.merci.survivalkit.weapon.WeaponSource.AIM_THEN_CROWD));

		BlockPos at = arena(context, world, "minecraft:iron_sword");
		List<Integer> history = new ArrayList<>();
		record(context, history);

		// All in the same tick, so the count is never caught halfway; the near spider last, so it
		// is the newest spider and summonAt hands back its id.
		put(world, at, "minecraft:zombie", -2.0, 5.0, "");
		put(world, at, "minecraft:zombie", 0.0, 6.0, "");
		put(world, at, "minecraft:zombie", 2.0, 5.0, "");
		put(world, at, "minecraft:cow", -3.0, 3.5, "");
		put(world, at, "minecraft:cow", 3.0, 3.5, "");
		put(world, at, "minecraft:villager", -1.5, 4.0, "");
		put(world, at, "minecraft:villager", 1.5, 4.0, "");

		for (double dx : new double[] {-2.0, 0.0, 2.0}) {
			put(world, at, "minecraft:spider", dx, 12.0, "");
		}

		int spider = summonAt(context, world, "minecraft:spider", at, 0.0, 2.5);
		record(context, history);

		for (int i = 0; i < 30; i++) {
			context.waitTick();
			record(context, history);
		}

		boolean aimedAtAnything = context.computeOnClient(client -> client.hitResult instanceof net.minecraft.world.phys.EntityHitResult);
		seen.put("çoğunluk 3 zombi + 1 örümcek", "seçim " + history);

		if (aimedAtAnything) {
			wrong.add("çoğunluk: hazırlık — hiçbir mob nişangahta olmamalıydı");
		}

		if (!history.equals(List.of(PLAIN, SMITE))) {
			wrong.add("çoğunluk: 3 zombi + 1 örümcek, nişan yok → Smite beklenirdi, seçim " + history);
		}

		// Aim comes first.
		turnOnto(context, spider);
		context.waitTicks(5);
		int onSpider = selected(context);
		boolean spiderAimed = aimedAt(context, spider);
		context.runOnClient(client -> {
			client.player.setYRot(180.0f);
			client.player.setXRot(0.0f);
		});
		context.waitTicks(5);
		int awayAgain = selected(context);
		seen.put("çoğunluk örümceğe nişan", "nişanda " + onSpider + ", dönünce " + awayAgain);

		if (!spiderAimed) {
			wrong.add("çoğunluk: hazırlık — örümceğe nişan alınmalıydı");
		} else if (onSpider != BANE) {
			wrong.add("çoğunluk: örümceğe nişan alınca Bane beklenirdi, seçili " + onSpider);
		}

		if (awayAgain != SMITE) {
			wrong.add("çoğunluk: nişan bitince çoğunluğun Smite'ı geri gelmeliydi, seçili " + awayAgain);
		}

		// Four more spiders for half a second: five to three, but not for long enough.
		List<Integer> flicker = new ArrayList<>();
		record(context, flicker);
		moreSpiders(world, at);
		context.waitTicks(10);
		world.getServer().runCommand("kill @e[tag=gecici]");

		for (int i = 0; i < 40; i++) {
			context.waitTick();
			record(context, flicker);
		}

		seen.put("çoğunluk yarım saniyelik değişim", "seçim " + flicker);

		if (!flicker.equals(List.of(SMITE))) {
			wrong.add("çoğunluk: yarım saniyelik yeni çoğunluk silahı değiştirmemeliydi, seçim " + flicker);
		}

		// The same four, staying: Bane, after a second and not before.
		moreSpiders(world, at);
		int switchedAfter = -1;

		for (int i = 1; i <= 60 && switchedAfter < 0; i++) {
			context.waitTick();

			if (selected(context) != SMITE) {
				switchedAfter = i;
			}
		}

		int finallySelected = selected(context);
		seen.put("çoğunluk kalıcı değişim", "seçili " + finallySelected + ", " + switchedAfter + " tik sonra");

		if (finallySelected != BANE) {
			wrong.add("çoğunluk: kalan yeni çoğunluk Bane'i çıkarmalıydı, seçili " + finallySelected);
		} else if (switchedAfter < 20) {
			wrong.add("çoğunluk: yeni çoğunluk 1 saniye dolmadan silah değişti, " + switchedAfter + " tik sonra");
		}

		world.getServer().runCommand("kill @e[type=!minecraft:player]");
		setInt(context, "lock_seconds", 3);
	}

	/** Four spiders behind the player, inside the range, tagged so they can be taken away again. */
	private static void moreSpiders(TestSingleplayerContext world, BlockPos at) {
		for (double[] spot : new double[][] {{-3.0, 2.0}, {3.0, 2.0}, {-2.0, 7.0}, {2.0, 7.0}}) {
			put(world, at, "minecraft:spider", spot[0], spot[1], ",Tags:[\"gecici\"]");
		}
	}

	/** A still mob at this spot, without waiting for it to reach the client. */
	private static void put(TestSingleplayerContext world, BlockPos at, String mob, double dx, double dz, String extra) {
		world.getServer().runCommand("summon " + mob + " " + (at.getX() + 0.5 + dx) + " " + at.getY() + " " + (at.getZ() + 0.5 + dz)
			+ " {NoAI:1b,PersistenceRequired:1b,Silent:1b" + extra + "}");
	}

	// --- plumbing ---

	/** A clean floor high in the air, the given item in hand and the four swords after it, facing away. */
	private static BlockPos arena(ClientGameTestContext context, TestSingleplayerContext world, String hand) {
		BlockPos feet = context.computeOnClient(client -> client.player.blockPosition());
		int x = feet.getX();
		int y = 120;
		int z = feet.getZ();

		world.getServer().runCommand("kill @e[type=!minecraft:player]");
		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("fill " + (x - 4) + " " + (y - 1) + " " + (z - 4) + " " + (x + 4) + " " + (y + 4) + " " + (z + 6) + " air");
		world.getServer().runCommand("fill " + (x - 4) + " " + (y - 1) + " " + (z - 4) + " " + (x + 4) + " " + (y - 1) + " " + (z + 6) + " minecraft:stone");
		world.getServer().runCommand("item replace entity @a hotbar." + PLAIN + " with " + hand);

		for (int i = 1; i < HOTBAR.size(); i++) {
			world.getServer().runCommand("item replace entity @a hotbar." + i + " with " + HOTBAR.get(i));
		}

		// Facing away from where the mob will stand.
		world.getServer().runCommand("tp @a " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 180 0");
		context.waitTicks(10);
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(PLAIN));
		context.waitTicks(3);
		return new BlockPos(x, y, z);
	}

	/**
	 * A still mob three blocks in front, and its id on this client — the newest of its type,
	 * since the last scenario's mob can linger on the client for a few ticks after being killed.
	 * A phantom is put {@link #FLYING_HEIGHT} up in the air and kept there, as it would be flying.
	 */
	private static int summon(ClientGameTestContext context, TestSingleplayerContext world, String mob, BlockPos at) {
		boolean flying = mob.equals("minecraft:phantom");
		world.getServer().runCommand("summon " + mob + " " + (at.getX() + 0.5) + " " + (at.getY() + (flying ? FLYING_HEIGHT : 0))
			+ " " + (at.getZ() + 3.5)
			+ " {NoAI:1b,PersistenceRequired:1b,Silent:1b" + (flying ? ",NoGravity:1b" : "") + "}");
		context.waitTicks(5);

		return context.computeOnClient(client -> {
			int newest = -1;

			for (Entity entity : client.level.entitiesForRendering()) {
				if (entity instanceof LivingEntity living && living != client.player && living.isAlive()
						&& net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(mob)) {
					newest = Math.max(newest, entity.getId());
				}
			}

			if (newest < 0) {
				throw new AssertionError("hazırlık: " + mob + " istemcide yok");
			}

			return newest;
		});
	}

	/** Turns the player's own view onto the mob's eyes, the way moving the mouse onto it would. */
	private static void turnOnto(ClientGameTestContext context, int id) {
		context.runOnClient(client -> {
			Entity entity = client.level.getEntity(id);

			if (entity != null) {
				client.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, entity.getEyePosition());
			}
		});
	}

	private static boolean aimedAt(ClientGameTestContext context, int id) {
		return context.computeOnClient(client -> client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
			&& hit.getEntity().getId() == id);
	}

	/**
	 * What a hit leaves after the target's own armour, the vanilla formula. Only the zombie has
	 * any here — 2 points — and it takes 18.5 down to 18.204 and 6 to 5.904.
	 */
	private static float afterArmour(String mob, float damage) {
		float armour = mob.equals("minecraft:zombie") ? 2.0f : 0.0f;
		float reduction = Math.min(20.0f, Math.max(armour / 5.0f, armour - damage / 2.0f));
		return damage * (1.0f - reduction / 25.0f);
	}

	private static float health(ClientGameTestContext context, int id) {
		return context.computeOnClient(client -> client.level.getEntity(id) instanceof LivingEntity living ? living.getHealth() : 0.0f);
	}

	private static void record(ClientGameTestContext context, List<Integer> history) {
		int slot = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());

		if (history.isEmpty() || history.getLast() != slot) {
			history.add(slot);
		}
	}

	private static void setAutoAttack(ClientGameTestContext context, boolean on) {
		context.runOnClient(client -> module().settings()
			.<BoolSetting>byId("auto_attack")
			.orElseThrow(() -> new AssertionError("otomatik vur ayarı yok"))
			.set(on));
	}

	private static void enable(ClientGameTestContext context, boolean on) {
		context.runOnClient(client -> module().setEnabled(on));
		context.waitTicks(2);
	}

	private static Module module() {
		return SurvivalKit.modules().byId("autoweapon")
			.orElseThrow(() -> new AssertionError("autoweapon modülü kayıtlı değil"));
	}
}
