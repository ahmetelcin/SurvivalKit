package com.merci.survivalkit.autotool;

import java.util.List;

/** Settings as plain fields, with the same defaults the module ships with. */
final class FakeToolSettings implements ToolSettings {
	boolean restorePrevious = true;
	List<String> silkTouchBlocks = List.of();
	List<String> fortuneBlocks = List.of();
	int durabilityThreshold = 10;
	boolean keepSword = true;
	boolean keepSilkTouch = true;
	boolean onlyWithToolInHand = true;

	@Override
	public boolean restorePrevious() {
		return restorePrevious;
	}

	@Override
	public List<String> silkTouchBlocks() {
		return silkTouchBlocks;
	}

	@Override
	public List<String> fortuneBlocks() {
		return fortuneBlocks;
	}

	@Override
	public int durabilityThreshold() {
		return durabilityThreshold;
	}

	@Override
	public boolean keepSword() {
		return keepSword;
	}

	@Override
	public boolean keepSilkTouch() {
		return keepSilkTouch;
	}

	@Override
	public boolean onlyWithToolInHand() {
		return onlyWithToolInHand;
	}
}
