package com.merci.survivalkit.menu;

/**
 * Waits for the player to stop fiddling, then says it is time to write.
 *
 * <p>Dragging a slider changes a setting on every tick it moves. Writing the file each time
 * would hammer the disk for one adjustment, and saving only when the menu closes would lose
 * everything if the game died first. So each change pushes the deadline back, and the write
 * happens about a second after the last one.
 *
 * <p>Counted in ticks and kept free of any clock, so the rule can be tried without a game.
 */
public final class SettingsAutoSave {
	/** Twenty ticks is a second at the rate the client runs. */
	public static final int DELAY_TICKS = 20;

	private static final int IDLE = -1;

	private final int delayTicks;
	private int ticksLeft = IDLE;

	public SettingsAutoSave() {
		this(DELAY_TICKS);
	}

	public SettingsAutoSave(int delayTicks) {
		if (delayTicks < 1) {
			throw new IllegalArgumentException("Gecikme en az bir tick olmalı: " + delayTicks);
		}

		this.delayTicks = delayTicks;
	}

	/** A setting changed. Pushes the deadline out to a full delay from now. */
	public void touch() {
		ticksLeft = delayTicks;
	}

	public boolean isPending() {
		return ticksLeft != IDLE;
	}

	/** One client tick. True exactly once per quiet spell, on the tick the write is due. */
	public boolean tick() {
		if (ticksLeft == IDLE) {
			return false;
		}

		if (--ticksLeft > 0) {
			return false;
		}

		ticksLeft = IDLE;
		return true;
	}

	/** Gives up the wait and asks to write now — for a menu closing or a game shutting down. */
	public boolean flush() {
		boolean due = isPending();
		ticksLeft = IDLE;
		return due;
	}
}
