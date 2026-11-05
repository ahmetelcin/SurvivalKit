package com.merci.survivalkit.scaffold;

import com.merci.survivalkit.area.Area;
import com.merci.survivalkit.vein.Pos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A world of solid blocks and a 36-slot inventory, enough for Scaffold. A placement puts the
 * held block into the world and takes one off the stack, the way the game does.
 */
final class FakeScaffoldContext implements ScaffoldContext {
	record Slot(String id, int count, int maxCount, boolean placeable) {
	}

	boolean ready = true;
	boolean screenOpen;
	Vec feet = new Vec(0.5, 64, 0.5);
	Vec motion = new Vec(0, 0, 0);
	double reach = 4.5;
	boolean onGround = true;
	boolean jumpHeld;
	boolean lockKeyHeld;
	boolean sneakKeyHeld;
	double fallDistance;
	boolean inLava;
	Vec heading = new Vec(0, 0, 0);
	Optional<Area> area = Optional.empty();
	/** Every call to sneak, in order. */
	final List<Boolean> sneaks = new ArrayList<>();
	/** Every action bar line, with whether it was a warning. */
	final List<String> actionBar = new ArrayList<>();
	/** The boxes drawn, a tick at a time. */
	final List<Pos> outlined = new ArrayList<>();
	boolean accepts = true;
	/** HOTBAR or FULL, as the setting would cut the scan. */
	int scanned = 36;

	final Map<Pos, String> solid = new HashMap<>();
	final Set<Pos> water = new HashSet<>();
	final Set<Pos> lava = new HashSet<>();
	/** Blocks that are there but do something when used. */
	final Set<Pos> interactive = new HashSet<>();
	final Slot[] slots = new Slot[36];
	int selected;

	final List<Placement> placed = new ArrayList<>();
	/** Wall torches hung, with the face of the block each hangs on. */
	final Map<Pos, Face> torches = new HashMap<>();
	final List<String> actions = new ArrayList<>();

	/** A floor of stone under y = 64 from x = -5 to {@code lastX}, z = -5..5. */
	void floorTo(int lastX) {
		for (int x = -5; x <= lastX; x++) {
			for (int z = -5; z <= 5; z++) {
				solid.put(new Pos(x, 63, z), "minecraft:stone");
			}
		}
	}

	void give(int slot, String id, int count) {
		slots[slot] = new Slot(id, count, 64, true);
	}

	void giveTorches(int slot, int count) {
		slots[slot] = new Slot("minecraft:torch", count, 64, false);
	}

	void giveItem(int slot, String id) {
		slots[slot] = new Slot(id, 1, 1, false);
	}

	String held() {
		return slots[selected] == null ? null : slots[selected].id();
	}

	@Override
	public boolean ready() {
		return ready;
	}

	@Override
	public boolean screenOpen() {
		return screenOpen;
	}

	@Override
	public Vec feet() {
		return feet;
	}

	@Override
	public Vec motion() {
		return motion;
	}

	@Override
	public boolean onGround() {
		return onGround;
	}

	@Override
	public boolean jumpHeld() {
		return jumpHeld;
	}

	@Override
	public boolean sneakKeyHeld() {
		return sneakKeyHeld;
	}

	@Override
	public boolean lockKeyHeld() {
		return lockKeyHeld;
	}

	@Override
	public double fallDistance() {
		return fallDistance;
	}

	@Override
	public boolean inLava() {
		return inLava;
	}

	@Override
	public Vec heading() {
		return heading;
	}

	@Override
	public void sneak(boolean down) {
		sneaks.add(down);
	}

	boolean sneaking() {
		return !sneaks.isEmpty() && sneaks.getLast();
	}

	@Override
	public Optional<Area> area() {
		return area;
	}

	@Override
	public Vec eyes() {
		return new Vec(feet.x(), feet.y() + 1.62, feet.z());
	}

	@Override
	public double reach() {
		return reach;
	}

	@Override
	public Gap gap(Pos pos) {
		if (solid.containsKey(pos) || torches.containsKey(pos)) {
			return Gap.NONE;
		}

		if (water.contains(pos)) {
			return Gap.WATER;
		}

		return lava.contains(pos) ? Gap.LAVA : Gap.AIR;
	}

	@Override
	public Optional<String> clickable(Pos pos) {
		return interactive.contains(pos) ? Optional.empty() : Optional.ofNullable(solid.get(pos));
	}

	@Override
	public List<BlockStack> blocks() {
		List<BlockStack> stacks = new ArrayList<>();

		for (int slot = 0; slot < scanned; slot++) {
			Slot item = slots[slot];

			if (item != null && item.count() > 0) {
				stacks.add(new BlockStack(slot, item.id(), item.count(), item.maxCount(), item.placeable()));
			}
		}

		return stacks;
	}

	@Override
	public int selectedSlot() {
		return selected;
	}

	@Override
	public void selectSlot(int slot) {
		actions.add("select " + slot);
		selected = slot;
	}

	@Override
	public void swap(int inventorySlot, int hotbarSlot) {
		actions.add("swap " + inventorySlot + " " + hotbarSlot);
		Slot moved = slots[inventorySlot];
		slots[inventorySlot] = slots[hotbarSlot];
		slots[hotbarSlot] = moved;
	}

	@Override
	public void actionBar(String text, boolean warning) {
		actionBar.add((warning ? "! " : "") + text);
	}

	@Override
	public void outline(Pos pos) {
		outlined.add(pos);
	}

	@Override
	public boolean place(Placement placement) {
		Slot item = slots[selected];

		if (accepts && item != null && item.count() > 0 && BlockChoice.TORCHES.contains(item.id())) {
			actions.add("torch " + placement.target());
			torches.put(placement.target(), placement.face());
			slots[selected] = item.count() == 1 ? null : new Slot(item.id(), item.count() - 1, item.maxCount(), false);
			return true;
		}

		if (!accepts || item == null || !item.placeable() || item.count() == 0) {
			return false;
		}

		actions.add("place " + placement.target());
		placed.add(placement);
		solid.put(placement.target(), item.id());
		water.remove(placement.target());
		lava.remove(placement.target());
		slots[selected] = item.count() == 1 ? null : new Slot(item.id(), item.count() - 1, item.maxCount(), true);
		return true;
	}
}
