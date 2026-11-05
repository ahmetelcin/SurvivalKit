package com.merci.survivalkit.autotool;

import java.util.List;

/**
 * What Alet Seçici is asked to do, read fresh on every click. The block lists and the
 * threshold are Otomatik Alet's, so there is one place to keep them.
 */
public interface PickSettings {
	PickMode mode();

	List<String> silkTouchBlocks();

	List<String> fortuneBlocks();

	int durabilityThreshold();
}
