package com.merci.survivalkit.area;

/** What a block-breaking module does when there is no area to keep to. */
public enum WithoutArea {
	/** Works as if there were no area rule at all. */
	EVERYWHERE,
	/** Does nothing until an area is drawn. */
	NOWHERE
}
