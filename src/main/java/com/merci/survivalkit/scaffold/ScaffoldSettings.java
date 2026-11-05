package com.merci.survivalkit.scaffold;

import java.util.List;

/** What the user has asked of Scaffold, read fresh every tick. */
public interface ScaffoldSettings {
	/** Ticks to wait after a tick that put blocks down; 0 is every tick. */
	int delay();

	/** The most blocks one tick puts down. */
	int perTick();

	boolean fillAir();

	boolean fillWater();

	boolean fillLava();

	SupportMode supportMode();

	/** The blocks that may be built against, with {@link SupportMode#LISTED}. */
	List<String> supports();

	/** The blocks to build with, in order. */
	List<String> preferred();

	/** Never built with. */
	List<String> blocked();

	boolean preferFullStack();

	BlockChoice.Fallback fallback();

	/** How many blocks past the next step to fill straight on while walking, 0–5. */
	int extend();

	/** Whether a run of blocks stays on the level it started on. */
	boolean keepY();

	/** Which y every block goes in; anything but {@link YLock#OFF} also keeps the tower down. */
	YLock yLock();

	/** The y of {@link YLock#FIXED}. */
	int fixedY();

	/** The lock key, with {@link YLock#FIXED}: the number becomes this, kept like any other change. */
	void fixedY(int y);

	/** How many blocks wide the bridge is, 1–3: 3 reaches out to both sides, 2 to the side the feet are nearer. */
	int width();

	/** Whether walking slantwise fills the corner between two blocks that touch only at an edge. */
	boolean diagonal();

	/** Whether every block on goes a block higher or lower; the Y lock slides along with it. */
	Ramp ramp();

	/** Whether pressing sneak takes the locked Y a block down. */
	boolean descend();

	/** Whether the block underfoot is built with first, when it is in reach. */
	boolean sameBlock();

	/** Whether a wall torch goes on the bridge's side every {@link #torchInterval} blocks. */
	boolean torches();

	/** How many blocks of bridge between two torches. */
	int torchInterval();

	/** Whether a block goes under the feet while jumping straight up. */
	boolean tower();

	/** Whether the tower needs the jump key held, rather than any jump. */
	boolean towerOnlyWithJump();

	SafeWalk safeWalk();

	boolean pauseWithScreen();

	/** Falling further than a jump. */
	boolean pauseWhileFalling();

	boolean pauseInLava();

	/** Whether the blocks left are counted on the action bar. */
	boolean showCount();

	/** Fewer blocks left than this and the count is a warning; 0 never warns. */
	int lowWarning();

	/** Whether what the HUD says is said on the action bar too, as it was before there was a HUD. */
	boolean actionBar();

	/** Whether the next block to go down is outlined. */
	boolean showTarget();

	/** Whether what was in hand goes back once nothing more is being put down. */
	boolean restorePrevious();
}
