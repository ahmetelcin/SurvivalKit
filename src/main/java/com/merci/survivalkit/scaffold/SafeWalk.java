package com.merci.survivalkit.scaffold;

/** When Scaffold holds the game's sneak down, which stops a player at an edge. */
public enum SafeWalk {
	OFF,
	/** Only on the ground with nothing yet under the step ahead. */
	AT_EDGE,
	/** The whole time the module is on. */
	ALWAYS
}
