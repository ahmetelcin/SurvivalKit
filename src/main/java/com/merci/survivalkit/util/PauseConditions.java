package com.merci.survivalkit.util;

import java.util.Objects;

/**
 * Whether a module should keep its hands off the player right now.
 *
 * <p>The decision is taken from a snapshot rather than from the client, so the rules can be
 * tried without a game. Reading the snapshot off the running client is somebody else's job.
 */
public final class PauseConditions {
	/**
	 * @param inWorld    false before the player and level exist, and while leaving a world
	 * @param screenOpen true while any screen has focus: inventory, chat, pause menu
	 */
	public record State(boolean inWorld, boolean screenOpen) {
	}

	private PauseConditions() {
	}

	public static boolean paused(State state) {
		Objects.requireNonNull(state, "state");
		return !state.inWorld() || state.screenOpen();
	}

	public static boolean active(State state) {
		return !paused(state);
	}
}
