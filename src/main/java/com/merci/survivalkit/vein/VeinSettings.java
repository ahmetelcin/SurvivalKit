package com.merci.survivalkit.vein;

import com.merci.survivalkit.area.WithoutArea;
import java.util.List;

/** What {@link VeinBreaker} reads from the menu. */
public interface VeinSettings {
	Trigger trigger();

	/** The blocks a vein may be made of, as namespaced ids. */
	List<String> blocks();

	/** At most this many blocks go at once, counting the one that started it. */
	int maxBlocks();

	/** Logs are left alone unless natural (non-persistent) leaves touch the pile. */
	boolean naturalTreesOnly();

	/** Outlines round the blocks that would go, before they go. */
	boolean showOutline();

	/** Where /loot puts the drops. Without /loot the server decides; see {@link CommandPlan.Route}. */
	Drops drops();

	/** At most this many commands in any one second, so the server does not take it for spam. */
	int commandsPerSecond();

	/** The server's "changed the block" / "dropped items" replies are kept out of chat while a job runs. */
	boolean hideFeedback();

	/** With no Yapı Modu area in force: break as before, or not at all. */
	WithoutArea withoutArea();

	/** One hunger point for this many blocks broken; 0 is free. See {@link HungerCost}. */
	int blocksPerHungerPoint();

	/** The tool in hand wears as if each block were broken with it by hand. See {@link ToolWear}. */
	boolean toolWear();
}
