package com.merci.survivalkit.vein;

import com.merci.survivalkit.area.WithoutArea;
import java.util.List;

final class FakeVeinSettings implements VeinSettings {
	Trigger trigger = Trigger.LOOK_AND_PRESS;
	List<String> blocks = List.of(
		"minecraft:oak_log", "minecraft:birch_log",
		"minecraft:iron_ore", "minecraft:deepslate_iron_ore", "minecraft:coal_ore");
	int maxBlocks = 128;
	boolean naturalTreesOnly = true;
	boolean showOutline = true;
	Drops drops = Drops.INVENTORY;
	int commandsPerSecond = 1000;
	boolean hideFeedback = true;
	WithoutArea withoutArea = WithoutArea.EVERYWHERE;
	/** Free unless a test is about the cost, so the other tests' command lists stay as they were. */
	int blocksPerHungerPoint;
	/** Off unless a test is about it, for the same reason. */
	boolean toolWear;

	@Override
	public boolean toolWear() {
		return toolWear;
	}

	@Override
	public WithoutArea withoutArea() {
		return withoutArea;
	}

	@Override
	public int blocksPerHungerPoint() {
		return blocksPerHungerPoint;
	}

	@Override
	public Trigger trigger() {
		return trigger;
	}

	@Override
	public List<String> blocks() {
		return blocks;
	}

	@Override
	public int maxBlocks() {
		return maxBlocks;
	}

	@Override
	public boolean naturalTreesOnly() {
		return naturalTreesOnly;
	}

	@Override
	public boolean showOutline() {
		return showOutline;
	}

	@Override
	public Drops drops() {
		return drops;
	}

	@Override
	public int commandsPerSecond() {
		return commandsPerSecond;
	}

	@Override
	public boolean hideFeedback() {
		return hideFeedback;
	}
}
