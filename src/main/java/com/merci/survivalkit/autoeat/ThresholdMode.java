package com.merci.survivalkit.autoeat;

/**
 * When hunger has dropped far enough to eat.
 *
 * <p>{@link #FIXED} is a line the user draws: below it, eat. {@link #AUTO} draws the line
 * per meal instead, from the food about to be eaten — the bite waits until the bar has room
 * for all of it, so not one point of it is lost to a full bar.
 */
public enum ThresholdMode {
	FIXED,
	AUTO
}
