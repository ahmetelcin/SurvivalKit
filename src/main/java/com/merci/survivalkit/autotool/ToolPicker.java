package com.merci.survivalkit.autotool;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.tool.BestTool;
import com.merci.survivalkit.tool.BlockTarget;
import com.merci.survivalkit.tool.ToolSlot;
import java.util.Objects;
import java.util.Optional;

/**
 * Alet Seçici: a middle click on a block puts the best tool for it in hand.
 *
 * <p>This runs at the very start of the client tick, before the game reads its keys. A click
 * taken here is gone by the time the game's own pick block looks for one, which is how the two
 * never both act on the same click — no mixin needed. So the decision comes first and the
 * click is only taken when there is something to do with it: whenever the picker has nothing
 * to offer, the click is left where it is and the game picks the block as it always has.
 *
 * <p>Which tool is best is {@link BestTool}'s call, the same one Otomatik Alet makes.
 */
public final class ToolPicker implements Hands.Owner {
	/** The nine slots that can be selected directly; anything past them is the backpack. */
	private static final int HOTBAR_SIZE = 9;

	private final PickSettings settings;
	private final Hands hands;

	public ToolPicker(PickSettings settings, Hands hands) {
		this.settings = Objects.requireNonNull(settings, "settings");
		this.hands = Objects.requireNonNull(hands, "hands");
	}

	public void tick(PickContext client) {
		// Creative keeps the game's pick block: there it hands out the block itself, which is
		// what a middle click in creative is for.
		if (!client.ready() || client.creative() || settings.mode() == PickMode.OFF) {
			return;
		}

		Optional<ToolSlot> best = best(client);

		if (best.isEmpty()) {
			return;
		}

		if (client.takePickClicks() == 0) {
			return;
		}

		ToolSlot tool = best.get();

		// Already in hand: the click is still kept from the game, which would otherwise swap
		// the tool the player asked for out for a stack of the block.
		if (tool.slot() == client.selectedSlot()) {
			return;
		}

		boolean inHotbar = tool.slot() < HOTBAR_SIZE;
		Hands.Claim[] claims = inHotbar
			? new Hands.Claim[] {Hands.Claim.MAIN_HAND}
			: new Hands.Claim[] {Hands.Claim.MAIN_HAND, Hands.Claim.INVENTORY};

		// Refused only by something keeping the player alive. The click is dropped rather than
		// handed to the game: a block appearing in hand mid-rescue is no better than a tool.
		if (!hands.take(this, Hands.Priority.PICK_TOOL, claims)) {
			return;
		}

		if (inHotbar) {
			client.selectSlot(tool.slot());
		} else {
			client.swap(tool.slot(), client.selectedSlot());
		}

		hands.release(this);
	}

	/** Nothing to undo: the hand is only held inside {@link #tick}, never across ticks. */
	@Override
	public void handsTaken() {
	}

	/** The tool to offer for this click, or empty when the click belongs to the game. */
	private Optional<ToolSlot> best(PickContext client) {
		Optional<MiningTarget> target = client.target();

		if (target.isEmpty()) {
			return Optional.empty();
		}

		if (settings.mode() == PickMode.WHEN_BLOCK_NOT_IN_INVENTORY && client.blockInInventory()) {
			return Optional.empty();
		}

		String id = target.get().blockId();
		BlockTarget block = new BlockTarget(target.get().needsCorrectTool(),
			settings.silkTouchBlocks().contains(id), settings.fortuneBlocks().contains(id));

		return BestTool.choose(client.tools(), block, settings.durabilityThreshold());
	}
}
