package com.merci.survivalkit.scaffold;

/** Whether the bridge climbs or sinks a block for every block it goes on. */
public enum Ramp {
	OFF,
	/** A block higher each block on: a step to jump up. */
	UP,
	/** A block lower each block on: a step to walk down. */
	DOWN
}
