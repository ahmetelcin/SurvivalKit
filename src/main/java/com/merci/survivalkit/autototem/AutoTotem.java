package com.merci.survivalkit.autototem;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.Objects;
import java.util.Optional;

/**
 * Keeps a totem of undying in the off hand.
 *
 * <p>Like {@link com.merci.survivalkit.autoeat.AutoEat}, all of the thinking is here and
 * none of Minecraft is: the client is reached through {@link TotemContext} so the rules can
 * be tried without a game.
 */
public final class AutoTotem implements Hands.Owner {
	public static final String TOTEM = "minecraft:totem_of_undying";

	/** How long to wait for a swap to land before assuming it never will. */
	public static final int SWAP_TIMEOUT_TICKS = 20;

	private static final int NO_SLOT = -1;

	private final TotemSettings settings;
	private final Hands hands;

	private int keptAt = NO_SLOT;
	private int waited;
	private boolean swapping;
	private boolean warned;
	private String swappingTo = "";

	public AutoTotem(TotemSettings settings, Hands hands) {
		this.settings = Objects.requireNonNull(settings, "settings");
		this.hands = Objects.requireNonNull(hands, "hands");
	}

	public void tick(TotemContext client) {
		if (!client.ready()) {
			return;
		}

		if (swapping) {
			waitForTheSwap(client);
			return;
		}

		if (wanted(client)) {
			noticeWhetherThereAreTotemsLeft(client);
			putTheWantedItemInTheOffHand(client);
			return;
		}

		giveTheHandBack(client);
	}

	/** Called when the module is switched off. Anything borrowed goes back. */
	public void stop(TotemContext client) {
		swapping = false;
		hands.release(this);

		if (keptAt != NO_SLOT && settings.restoreOffhand()) {
			client.swapToOffhand(keptAt);
			keptAt = NO_SLOT;
		}
	}

	/**
	 * Nothing outranks this module today, so this is the defensive path rather than one that
	 * runs. If something ever does outrank it, note what is deliberately not undone: the
	 * totem stays in the off hand, because a module that took the hands off the thing
	 * keeping the player alive is not a trade worth making silently. What was borrowed is
	 * still remembered, so it goes back when health recovers.
	 */
	@Override
	public void handsTaken() {
		swapping = false;
	}

	private boolean wanted(TotemContext client) {
		return settings.mode() == TotemMode.ALWAYS || client.health() < settings.healthThreshold();
	}

	/**
	 * What the off hand should be holding right now: a totem whenever one can be reached, and
	 * otherwise the first entry of the user's fallback list that is actually in the inventory.
	 * Empty means the hand is none of this module's business — it holds nothing and, just as
	 * importantly, claims nothing, so AutoEat may use that hand.
	 *
	 * <p>Something already in the off hand counts as reachable: that is what keeps a hand that
	 * is already right from being taken apart and put back together every tick.
	 */
	private Optional<String> wantedId(TotemContext client) {
		if (inReach(client, TOTEM)) {
			return Optional.of(TOTEM);
		}

		return settings.fallback().stream().filter(id -> inReach(client, id)).findFirst();
	}

	private boolean inReach(TotemContext client, String id) {
		return id.equals(client.offhandId()) || client.slotOf(id).isPresent();
	}

	/**
	 * One line, once, when the totems run out — and armed again by the next totem that turns
	 * up, because running out a second time is news a second time. Said only while a totem is
	 * actually wanted: in the health-threshold mode a full health bar is not the moment to be
	 * told the pocket is empty.
	 */
	private void noticeWhetherThereAreTotemsLeft(TotemContext client) {
		if (inReach(client, TOTEM)) {
			warned = false;
			return;
		}

		if (warned || !settings.warnWhenOut()) {
			return;
		}

		warned = true;
		client.say(Lang.of(Keys.TOTEM_OUT));
	}

	private void putTheWantedItemInTheOffHand(TotemContext client) {
		String wanted = wantedId(client).orElse("");

		if (wanted.isEmpty() || wanted.equals(client.offhandId())) {
			return;
		}

		int slot = client.slotOf(wanted).orElse(NO_SLOT);

		if (slot == NO_SLOT) {
			return;
		}

		if (!hands.take(this, Hands.Priority.AUTO_TOTEM, Hands.Claim.OFF_HAND, Hands.Claim.INVENTORY)) {
			return;
		}

		// Whatever was in the off hand lands in the slot the totem came out of, and that is
		// where it waits. Only the first borrow is remembered: after a totem is spent and
		// the next one goes in, the player's own item is still sitting where it was put.
		if (keptAt == NO_SLOT && !client.offhandId().isEmpty()) {
			keptAt = slot;
		}

		swapping = true;
		swappingTo = wanted;
		waited = 0;
		client.swapToOffhand(slot);
	}

	/**
	 * The swap is a round trip to the server; the hands are held until it shows up. What is
	 * waited for is whatever was sent — a fallback item as readily as a totem, or the hands
	 * would be held for the whole timeout with the job already done.
	 */
	private void waitForTheSwap(TotemContext client) {
		if (swappingTo.equals(client.offhandId()) || ++waited > SWAP_TIMEOUT_TICKS) {
			swapping = false;
			hands.release(this);
		}
	}

	/**
	 * Health has recovered and the hand was only borrowed. The totem goes back where the
	 * player's own item came from, and they get their shield, map or firework back.
	 */
	private void giveTheHandBack(TotemContext client) {
		if (keptAt == NO_SLOT || !settings.restoreOffhand()) {
			return;
		}

		if (!hands.take(this, Hands.Priority.AUTO_TOTEM, Hands.Claim.OFF_HAND, Hands.Claim.INVENTORY)) {
			return;
		}

		client.swapToOffhand(keptAt);
		keptAt = NO_SLOT;
		hands.release(this);
	}
}
