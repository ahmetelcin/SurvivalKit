package com.merci.survivalkit.autotool;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.tool.BestTool;
import com.merci.survivalkit.tool.BlockTarget;
import com.merci.survivalkit.tool.ToolSlot;
import java.util.Objects;
import java.util.Optional;

/**
 * Takes out the best tool when a block starts to break, and puts back what was in hand once
 * breaking is over.
 *
 * <p>"Over" means the attack key was let go, or it has been held for {@link #IDLE_TICKS}
 * without anything breaking — not the end of each block. Between two blocks the game pauses
 * for a few ticks, and a hand that went back to bread there and out to the pickaxe again would
 * flicker through a whole tunnel. So a run of blocks is one switch out and one switch back.
 *
 * <p>Like {@link com.merci.survivalkit.autoeat.AutoEat}, all of the thinking is here and none
 * of Minecraft is: which tool is best is {@link BestTool}'s call, and the client is reached
 * through {@link ToolContext}.
 */
public final class AutoTool implements Hands.Owner {
	/** How long attack may be held with nothing breaking before the hand goes back. */
	public static final int IDLE_TICKS = 20;

	private static final int NO_SWAP = -1;

	private final ToolSettings settings;
	private final Hands hands;

	private boolean out;
	private int previousSlot;
	private int swappedFrom = NO_SWAP;
	/** The hotbar slot the swap went into, which is where it has to be undone even if the player scrolled since. */
	private int swappedTo;
	private int idle;
	/** Kept for the interruption, which arrives while another module is ticking. */
	private ToolContext current;

	public AutoTool(ToolSettings settings, Hands hands) {
		this.settings = Objects.requireNonNull(settings, "settings");
		this.hands = Objects.requireNonNull(hands, "hands");
	}

	public void tick(ToolContext client) {
		current = client;

		if (!client.ready()) {
			return;
		}

		if (client.creative()) {
			giveBack(client);
			return;
		}

		if (!out) {
			if (client.breaking() && startsFromWhatIsInHand(client) && !keepWhatIsInHand(client)) {
				takeOut(client);
			}

			return;
		}

		if (!client.attackHeld()) {
			giveBack(client);
			return;
		}

		if (!client.breaking()) {
			if (++idle >= IDLE_TICKS) {
				giveBack(client);
			}

			return;
		}

		idle = 0;
		takeOut(client);
	}

	/** Called when the module is switched off. Anything taken out goes back. */
	public void stop(ToolContext client) {
		giveBack(client);
	}

	@Override
	public void handsTaken() {
		if (current != null) {
			undo(current);
		}
	}

	/**
	 * Whether a run may start at all. Only looked at before one starts: once out, the hand
	 * holds a tool the module took out, and the next block's tool follows as before.
	 */
	private boolean startsFromWhatIsInHand(ToolContext client) {
		return !settings.onlyWithToolInHand() || client.holdingTool();
	}

	/**
	 * Items the player is holding on purpose. A sword is kept so a fight is not disarmed by a
	 * stray click. A Silk Touch tool is kept on the blocks it is the tool for — stone under a
	 * Silk Touch pickaxe, grass under a Silk Touch shovel — because whoever holds one there is
	 * after the block itself.
	 *
	 * <p>Only there. Wood, dirt, wool and hay drop to anything, a pickaxe included, and
	 * counting that as a reason to keep the pickaxe left it in hand for all of them.
	 */
	private boolean keepWhatIsInHand(ToolContext client) {
		// Yapı Modu's wand, whatever the sword setting says.
		if (client.reserved(client.selectedSlot())) {
			return true;
		}

		if (settings.keepSword() && client.holdingSword()) {
			return true;
		}

		if (!settings.keepSilkTouch() || client.target().isEmpty()) {
			return false;
		}

		return client.tools().stream()
			.filter(tool -> tool.slot() == client.selectedSlot())
			.anyMatch(held -> held.silkTouch() > 0 && held.harvests());
	}

	/**
	 * Puts the best tool for the block under the crosshair in hand. Also how a run of blocks
	 * moves from one tool to another: the hand goes straight from pickaxe to shovel, and the
	 * item from before the run stays the one that comes back at the end.
	 */
	private void takeOut(ToolContext client) {
		Optional<ToolSlot> best = best(client);

		if (best.isEmpty() || best.get().slot() == client.selectedSlot()) {
			return;
		}

		// A tool sitting where the last swap came from is the item from before the run; undo
		// the swap and look again next tick rather than swap it back in a second way.
		if (swappedFrom != NO_SWAP) {
			client.swap(swappedFrom, swappedTo);
			swappedFrom = NO_SWAP;
			return;
		}

		ToolSlot tool = best.get();
		boolean inHotbar = tool.slot() < HOTBAR_SIZE;
		Hands.Claim[] claims = inHotbar
			? new Hands.Claim[] {Hands.Claim.MAIN_HAND}
			: new Hands.Claim[] {Hands.Claim.MAIN_HAND, Hands.Claim.INVENTORY};

		if (!hands.take(this, Hands.Priority.AUTO_TOOL, claims)) {
			return;
		}

		if (!out) {
			previousSlot = client.selectedSlot();
			out = true;
			idle = 0;
		}

		if (inHotbar) {
			client.selectSlot(tool.slot());
		} else {
			swappedFrom = tool.slot();
			swappedTo = client.selectedSlot();
			client.swap(swappedFrom, swappedTo);
		}
	}

	private Optional<ToolSlot> best(ToolContext client) {
		return client.target().flatMap(target -> BestTool.choose(
			client.tools().stream().filter(tool -> !client.reserved(tool.slot())).toList(),
			new BlockTarget(target.needsCorrectTool(),
				settings.silkTouchBlocks().contains(target.blockId()),
				settings.fortuneBlocks().contains(target.blockId())),
			settings.durabilityThreshold()));
	}

	/** Breaking is over: the item from before goes back if the user wants it to. */
	private void giveBack(ToolContext client) {
		if (out && settings.restorePrevious()) {
			undo(client);
		}

		forget();
		hands.release(this);
	}

	private void undo(ToolContext client) {
		if (!out) {
			return;
		}

		if (swappedFrom != NO_SWAP) {
			client.swap(swappedFrom, swappedTo);
		}

		if (client.selectedSlot() != previousSlot) {
			client.selectSlot(previousSlot);
		}

		forget();
	}

	private void forget() {
		out = false;
		swappedFrom = NO_SWAP;
		idle = 0;
	}

	/** The nine slots that can be selected directly; anything past them is the backpack. */
	private static final int HOTBAR_SIZE = 9;
}
