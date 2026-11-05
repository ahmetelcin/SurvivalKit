package com.merci.survivalkit.area;

import com.merci.survivalkit.vein.Pos;
import java.util.Optional;

/** The game as {@link BuildMode} sees it. The client side only reads, translates and draws. */
public interface BuildContext {
	/** This server and dimension; empty outside a world. */
	Optional<Place> place();

	/** The main hand holds one of the items set as the wand. */
	boolean holdingWand();

	/** The block under the crosshair, if the crosshair is on one. */
	Optional<Pos> target();

	/** The block the player's feet are in; empty outside a world. */
	Optional<Pos> feet();

	/** One line in the player's own chat, not sent anywhere. */
	void say(String message);

	/** Draws these corners, and the box between them once both are there, for the coming tick. */
	void frame(Corners corners);
}
