package com.merci.survivalkit.scaffold;

/** What fills a block a placed block would take the place of. */
public enum Gap {
	/** Air, or something a block simply replaces: grass, snow layers, flowers. */
	AIR,
	WATER,
	LAVA,
	/** Not a gap: something to stand on, or something a block cannot go into. */
	NONE
}
