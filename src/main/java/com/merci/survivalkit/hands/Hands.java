package com.merci.survivalkit.hands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Who is allowed to move what the player is holding.
 *
 * <p>Two modules reaching for the same hand at the same time is how a totem ends up in the
 * backpack mid-fight. So nothing swaps anything without asking here first, and the answer
 * depends on what the other modules are in the middle of.
 */
public final class Hands {
	/** What can be claimed. The inventory is here because a swap disturbs one. */
	public enum Claim {
		MAIN_HAND,
		OFF_HAND,
		INVENTORY
	}

	/**
	 * How badly a module needs the hands, listed together so the order between modules is
	 * one thing to read rather than a number hidden in each of them.
	 */
	public enum Priority {
		/** A faster pickaxe. Anything else that wants the hand is worth more than that. */
		AUTO_TOOL(5),
		/**
		 * The right weapon for the target. Above Otomatik Alet — in a fight the sword matters
		 * more than the pickaxe — and below feeding you: a switch is one tick and can wait for
		 * the next, a meal cut short can cost the fight.
		 */
		AUTO_WEAPON(8),
		/** Feeding you. It can always wait a moment. */
		AUTO_EAT(10),
		/**
		 * A block under your feet before you fall. Above feeding you and Otomatik Alet: a meal or a
		 * pickaxe can wait a tick, the drop cannot. Held only while blocks are going down.
		 */
		SCAFFOLD(20),
		/**
		 * A middle click. The player asked for this one by hand, so it outranks what the mod
		 * does on its own — short of keeping them alive. Held only for the moment of the switch.
		 */
		PICK_TOOL(50),
		/** Keeping you alive. Nothing else holds the hands while this wants them. */
		AUTO_TOTEM(100);

		private final int weight;

		Priority(int weight) {
			this.weight = weight;
		}

		public int weight() {
			return weight;
		}
	}

	/** A module that can be interrupted, and undoes whatever it had moved when it is. */
	public interface Owner {
		/**
		 * Something more urgent wants the hands. By the time this returns, everything this
		 * module moved has to be back where it was: the claim is already gone.
		 */
		void handsTaken();
	}

	private record Held(Owner owner, Priority priority, Set<Claim> claims) {
	}

	private final List<Held> held = new ArrayList<>();

	/**
	 * Asks for what a module is about to move. True means it may; false means something at
	 * least as urgent is already holding one of them, and the asking changed nothing.
	 *
	 * <p>Granting it interrupts every less urgent module in the way — all of them, not only
	 * the one that clashed, because a module that has been interrupted has put everything
	 * back and is holding nothing. Asking again for what you already hold widens your own
	 * claim and never interrupts you.
	 */
	public boolean take(Owner owner, Priority priority, Claim... wanted) {
		Set<Claim> claims = claimsOf(wanted);

		for (Held entry : held) {
			if (entry.owner != owner && conflicts(entry, claims) && entry.priority.weight() >= priority.weight()) {
				return false;
			}
		}

		List<Held> losing = held.stream()
			.filter(entry -> entry.owner != owner && conflicts(entry, claims))
			.toList();

		// Struck off the list before being told, so a module that lets go inside its own
		// interruption finds nothing left to let go of.
		held.removeAll(losing);
		losing.forEach(entry -> entry.owner.handsTaken());

		held.removeIf(entry -> entry.owner == owner);
		held.add(new Held(owner, priority, claims));
		return true;
	}

	/** Done with them. Harmless when nothing was held, which an interrupted module will find. */
	public void release(Owner owner) {
		held.removeIf(entry -> entry.owner == owner);
	}

	public boolean holds(Owner owner) {
		return held.stream().anyMatch(entry -> entry.owner == owner);
	}

	/** Everything claimed right now, whoever holds it. */
	public Set<Claim> taken() {
		Set<Claim> all = EnumSet.noneOf(Claim.class);
		held.forEach(entry -> all.addAll(entry.claims));
		return all;
	}

	private static boolean conflicts(Held entry, Set<Claim> claims) {
		return !Collections.disjoint(entry.claims, claims);
	}

	private static Set<Claim> claimsOf(Claim... wanted) {
		Set<Claim> claims = EnumSet.noneOf(Claim.class);
		claims.addAll(List.of(wanted));
		return claims;
	}
}
