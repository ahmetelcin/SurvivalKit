package com.merci.survivalkit.scaffold;

import java.util.List;

/** The module's defaults, each one open to a test. */
final class FakeScaffoldSettings implements ScaffoldSettings {
	int delay;
	int perTick = 1;
	boolean fillAir = true;
	boolean fillWater;
	boolean fillLava = true;
	SupportMode supportMode = SupportMode.ALL;
	List<String> supports = List.of();
	List<String> preferred = List.of("minecraft:cobblestone", "minecraft:dirt");
	List<String> blocked = BlockChoice.BLOCKED_BY_DEFAULT;
	boolean preferFullStack;
	BlockChoice.Fallback fallback = BlockChoice.Fallback.LARGEST_STACK;
	boolean restorePrevious = true;
	int extend;
	boolean keepY;
	YLock yLock = YLock.OFF;
	int fixedY = 64;
	int width = 1;
	boolean diagonal = true;
	Ramp ramp = Ramp.OFF;
	boolean descend;
	boolean sameBlock;
	boolean torches;
	int torchInterval = 12;
	boolean tower = true;
	boolean towerOnlyWithJump = true;
	SafeWalk safeWalk = SafeWalk.OFF;
	boolean pauseWithScreen = true;
	boolean pauseWhileFalling = true;
	boolean pauseInLava = true;
	boolean showCount = true;
	int lowWarning = 16;
	boolean showTarget = true;
	/** On, so the tests written before the HUD still hear the action bar. */
	boolean actionBar = true;

	@Override
	public int delay() {
		return delay;
	}

	@Override
	public int perTick() {
		return perTick;
	}

	@Override
	public boolean fillAir() {
		return fillAir;
	}

	@Override
	public boolean fillWater() {
		return fillWater;
	}

	@Override
	public boolean fillLava() {
		return fillLava;
	}

	@Override
	public SupportMode supportMode() {
		return supportMode;
	}

	@Override
	public List<String> supports() {
		return supports;
	}

	@Override
	public List<String> preferred() {
		return preferred;
	}

	@Override
	public List<String> blocked() {
		return blocked;
	}

	@Override
	public boolean preferFullStack() {
		return preferFullStack;
	}

	@Override
	public BlockChoice.Fallback fallback() {
		return fallback;
	}

	@Override
	public int extend() {
		return extend;
	}

	@Override
	public boolean keepY() {
		return keepY;
	}

	@Override
	public YLock yLock() {
		return yLock;
	}

	@Override
	public int fixedY() {
		return fixedY;
	}

	@Override
	public void fixedY(int y) {
		fixedY = y;
	}

	@Override
	public int width() {
		return width;
	}

	@Override
	public boolean diagonal() {
		return diagonal;
	}

	@Override
	public Ramp ramp() {
		return ramp;
	}

	@Override
	public boolean descend() {
		return descend;
	}

	@Override
	public boolean sameBlock() {
		return sameBlock;
	}

	@Override
	public boolean torches() {
		return torches;
	}

	@Override
	public int torchInterval() {
		return torchInterval;
	}

	@Override
	public boolean tower() {
		return tower;
	}

	@Override
	public boolean towerOnlyWithJump() {
		return towerOnlyWithJump;
	}

	@Override
	public SafeWalk safeWalk() {
		return safeWalk;
	}

	@Override
	public boolean pauseWithScreen() {
		return pauseWithScreen;
	}

	@Override
	public boolean pauseWhileFalling() {
		return pauseWhileFalling;
	}

	@Override
	public boolean pauseInLava() {
		return pauseInLava;
	}

	@Override
	public boolean showCount() {
		return showCount;
	}

	@Override
	public int lowWarning() {
		return lowWarning;
	}

	@Override
	public boolean showTarget() {
		return showTarget;
	}

	@Override
	public boolean actionBar() {
		return actionBar;
	}

	@Override
	public boolean restorePrevious() {
		return restorePrevious;
	}
}
