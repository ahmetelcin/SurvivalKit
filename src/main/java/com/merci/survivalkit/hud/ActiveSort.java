package com.merci.survivalkit.hud;

/** How the list of modules that are on is ordered. */
public enum ActiveSort {
	/** A to Z, in the player's language. */
	NAME,
	/** Longest name first: the list narrows down like a staircase. */
	LENGTH,
	/** As the menu lists them. */
	MENU
}
