package com.merci.survivalkit.vein;

import com.merci.survivalkit.area.Area;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** A world made of a map of blocks, a command tree made of a set of names. */
final class FakeVeinContext implements VeinContext {
	static final String AIR = "minecraft:air";

	final Map<Pos, String> blocks = new HashMap<>();
	final Set<Pos> naturalLeaves = new HashSet<>();
	final Set<String> commands = new HashSet<>(Set.of("loot", "setblock", "fill", "give"));
	final List<String> sent = new ArrayList<>();
	final List<String> said = new ArrayList<>();
	boolean ready = true;
	boolean triggerHeld;
	boolean silkTouch;
	int fortune;
	Pos target;
	List<Pos> outlined = List.of();
	Area area;
	int food = 20;
	float saturation = 5.0f;
	HeldTool tool;

	FakeVeinContext put(Pos pos, String id) {
		blocks.put(pos, id);
		return this;
	}

	@Override
	public boolean ready() {
		return ready;
	}

	@Override
	public Optional<Pos> target() {
		return Optional.ofNullable(target);
	}

	@Override
	public String blockAt(Pos pos) {
		return blocks.getOrDefault(pos, AIR);
	}

	@Override
	public boolean isLog(Pos pos) {
		return blockAt(pos).endsWith("_log") || blockAt(pos).endsWith("_stem");
	}

	@Override
	public boolean isNaturalFoliage(Pos pos) {
		return naturalLeaves.contains(pos);
	}

	@Override
	public boolean canRun(String command) {
		return commands.contains(command);
	}

	@Override
	public boolean triggerHeld() {
		return triggerHeld;
	}

	@Override
	public boolean holdsSilkTouch() {
		return silkTouch;
	}

	@Override
	public int fortuneLevel() {
		return fortune;
	}

	@Override
	public Optional<Area> area() {
		return Optional.ofNullable(area);
	}

	@Override
	public int foodLevel() {
		return food;
	}

	@Override
	public Optional<HeldTool> heldTool() {
		return Optional.ofNullable(tool);
	}

	@Override
	public float saturation() {
		return saturation;
	}

	@Override
	public void send(String command) {
		sent.add(command);
	}

	@Override
	public void say(String message) {
		said.add(message);
	}

	@Override
	public void outline(List<Pos> blocks) {
		outlined = blocks;
	}
}
