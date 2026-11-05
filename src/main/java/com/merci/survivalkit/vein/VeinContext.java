package com.merci.survivalkit.vein;

import com.merci.survivalkit.area.Area;
import java.util.List;
import java.util.Optional;

/** The game as {@link VeinBreaker} sees it. The client side only reads, translates and sends. */
public interface VeinContext {
	/** In a world, with a player and a connection. */
	boolean ready();

	/** The block under the crosshair, if the crosshair is on one. */
	Optional<Pos> target();

	/** The block's id ("minecraft:oak_log"); "minecraft:air" for air. */
	String blockAt(Pos pos);

	/** Whether the block is in the game's log tag. */
	boolean isLog(Pos pos);

	/** Leaves the world grew (not placed by a player), or the nether trees' wart blocks. */
	boolean isNaturalFoliage(Pos pos);

	/** Whether the server's command tree offers this command to the player, i.e. the player may run it. */
	boolean canRun(String command);

	/** Whether the trigger key is held down, with no screen open. */
	boolean triggerHeld();

	/** Whether the item in the main hand has Silk Touch. */
	boolean holdsSilkTouch();

	/** The Fortune level of the item in the main hand, 0 without. */
	int fortuneLevel();

	/** Yapı Modu's area for this server and dimension, while the module is on and both corners are marked. */
	Optional<Area> area();

	/** The food bar, 0 to 20, as the server last sent it. */
	int foodLevel();

	/** Saturation, as the server last sent it. */
	float saturation();

	/** The tool in the main hand, if it is one that wears. */
	Optional<HeldTool> heldTool();

	/** Sends a command to the server, without the slash. */
	void send(String command);

	/** One line in the player's own chat, not sent anywhere. */
	void say(String message);

	/** Outlines these blocks for the coming tick; an empty list draws nothing. */
	void outline(List<Pos> blocks);
}
