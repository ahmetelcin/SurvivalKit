package com.merci.survivalkit.autoeat;

import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.util.FoodSlot;
import com.merci.survivalkit.util.ItemPriorityResolver;
import com.merci.survivalkit.util.ItemPriorityResolver.Fallback;
import java.util.List;
import java.util.Objects;

/** Eats when hunger drops, then puts the hotbar back the way it found it. */
public final class AutoEat implements Hands.Owner {
	/** How long to wait for a bite to start before giving up. */
	public static final int START_TIMEOUT_TICKS = 20;

	private static final String GOLDEN_APPLE = "minecraft:golden_apple";
	private static final String ENCHANTED_GOLDEN_APPLE = "minecraft:enchanted_golden_apple";

	/**
	 * Never spent on an ordinary empty stomach. They are worth too much to burn as calories,
	 * so the fallback may not reach for them — but a user who lists one by name still gets it.
	 *
	 * <p>Cheapest first, which is the order the health rule spends them in: the enchanted one
	 * is the emergency behind the emergency.
	 */
	private static final List<String> APPLES = List.of(GOLDEN_APPLE, ENCHANTED_GOLDEN_APPLE);

	private static final int NO_SWAP = -1;

	/** The one thing in the off hand that is worth more than not having to swap hands. */
	private static final String TOTEM = "minecraft:totem_of_undying";

	private enum State {
		IDLE,
		/** Off hand only: the food has been sent to the off hand and has not arrived yet. */
		REACHING,
		STARTING,
		EATING
	}

	private final EatSettings settings;
	private final Hands hands;
	/** The client this is ticking against, so an interruption knows where to put things back. */
	private EatContext current;
	private State state = State.IDLE;
	private int previousSlot;
	/** The backpack slot a stack was pulled out of, and the hotbar slot it went into. */
	private int swappedFrom = NO_SWAP;
	private int swappedTo;
	/** The slot swapped into the off hand, which has to go back whatever happens. */
	private int offhandFrom = NO_SWAP;
	/** Which hand this bite is being taken with, decided when it starts. */
	private EatingHand hand = EatingHand.MAIN_HAND;
	/** What was sent to the off hand, so its arrival can be recognised. */
	private String reachingFor = "";
	private int waited;

	/** Settings are read every tick, so anything the user changes lands on the next one. */
	public AutoEat(EatSettings settings, Hands hands) {
		this.settings = Objects.requireNonNull(settings, "settings");
		this.hands = Objects.requireNonNull(hands, "hands");
	}

	/**
	 * Something more urgent wants the hands — a totem, most likely, which means the player
	 * is about to need them. The meal is off: the key goes up and everything moved goes
	 * back, before this returns.
	 */
	@Override
	public void handsTaken() {
		if (current != null) {
			undo(current);
		}
	}

	public void tick(EatContext client) {
		// Kept for the interruption, which arrives while another module is ticking and has
		// no client of its own to hand over.
		current = client;

		if (!client.ready()) {
			abort(client);
			return;
		}

		switch (state) {
			case IDLE -> start(client);
			case REACHING -> startOnceTheFoodIsInTheOffHand(client);
			case STARTING -> waitForTheBite(client);
			case EATING -> finishWhenTheBiteEnds(client);
		}
	}

	/**
	 * Called at the start of the tick, before the game reads the keys.
	 *
	 * <p>A bite does not only end in the tick: the server finishing it arrives as a packet,
	 * handled before the keys are read. For that moment the use key is down over a hand that
	 * is no longer eating, and the game gives the press to whatever the crosshair is on — the
	 * chest opens, the door swings. So the key goes up here, the moment the bite is over; the
	 * rest of the ending is left to {@link #tick}.
	 */
	public void beforeKeysAreRead(EatContext client) {
		if (state == State.EATING && !client.usingItem()) {
			client.holdUseKey(false);
		}
	}

	/** Called when the module is switched off mid-bite. */
	public void stop(EatContext client) {
		abort(client);
	}

	private void start(EatContext client) {
		FoodSlot food = choose(client);

		if (food == null) {
			return;
		}

		EatingHand wanted = handFor(client, food);

		// Asked for before anything is moved, and dropped again the moment the meal is over.
		// Refused means something more urgent is using the hands: there is no half-measure
		// here, so the attempt is simply left for a later tick.
		if (!hands.take(this, Hands.Priority.AUTO_EAT, claimsFor(client, wanted, food))) {
			return;
		}

		previousSlot = client.selectedSlot();
		waited = 0;
		hand = wanted;

		if (hand == EatingHand.OFF_HAND) {
			// No use key yet. Pressing it now would run the main hand: a block would be
			// placed, a log stripped, a shield raised. The bite is started by hand below,
			// once the food has actually reached the off hand.
			offhandFrom = food.slot();
			reachingFor = food.id();
			state = State.REACHING;
			client.swapToOffhand(offhandFrom);
			return;
		}

		// The use key is never what starts a bite, in either hand. Pressed while the crosshair
		// is on a block or an entity, the game hands the press to that first: a chest opens,
		// a door swings, a frame turns its item, a bed in the Nether goes off. Started
		// directly, the food is all it can reach.
		state = State.STARTING;
		reachFor(client, food);
		startTheBite(client);
	}

	/**
	 * Starts eating what is in the main hand without going near the use key, and only once
	 * it has started is the key held — over a bite in progress it has nothing else to act on.
	 */
	private void startTheBite(EatContext client) {
		client.startUsing(EatingHand.MAIN_HAND);

		if (client.usingItem()) {
			state = State.EATING;
			client.holdUseKey(true);
		}
	}

	/**
	 * Exactly what this bite will disturb, so two modules only collide when they really
	 * would have. Eating in the off hand always swaps; eating in the main hand only does
	 * when the food has to come out of the backpack.
	 */
	private static Hands.Claim[] claimsFor(EatContext client, EatingHand hand, FoodSlot food) {
		if (hand == EatingHand.OFF_HAND) {
			return new Hands.Claim[] {Hands.Claim.OFF_HAND, Hands.Claim.INVENTORY};
		}

		return food.inHotbar()
			? new Hands.Claim[] {Hands.Claim.MAIN_HAND}
			: new Hands.Claim[] {Hands.Claim.MAIN_HAND, Hands.Claim.INVENTORY};
	}

	/**
	 * Which hand this particular bite goes in. The setting asks for one; two things overrule
	 * it, and both are about not taking something away that the player is relying on.
	 */
	private EatingHand handFor(EatContext client, FoodSlot food) {
		if (settings.eatingHand() != EatingHand.OFF_HAND) {
			return EatingHand.MAIN_HAND;
		}

		// A totem is the difference between dying and not. Eating in the main hand costs a
		// held sword for a second; moving the totem costs the run.
		if (settings.keepOffhandTotem() && TOTEM.equals(client.offhandId())) {
			return EatingHand.MAIN_HAND;
		}

		// Already the held stack: sending it to the off hand would empty the main hand,
		// which is the one thing this mode exists to avoid. It is in a hand already.
		if (food.slot() == client.selectedSlot()) {
			return EatingHand.MAIN_HAND;
		}

		return EatingHand.OFF_HAND;
	}

	/**
	 * The swap is a round trip to the server, so the food is not in the off hand the instant
	 * it is asked for. Once it is there the bite is started for that hand specifically, and
	 * only then is the key held — and only if the bite really did start, so the key is never
	 * down over a main hand that would act on it.
	 */
	private void startOnceTheFoodIsInTheOffHand(EatContext client) {
		if (reachingFor.equals(client.offhandId())) {
			client.startUsing(EatingHand.OFF_HAND);

			if (client.usingItem()) {
				state = State.EATING;
				client.holdUseKey(true);
				return;
			}
		}

		if (++waited > START_TIMEOUT_TICKS) {
			abort(client);
		}
	}

	/**
	 * Food in the hotbar is simply selected. Food in the backpack is swapped into the slot
	 * already in hand, which leaves the selection alone — {@link #abort} swaps it back.
	 */
	private void reachFor(EatContext client, FoodSlot food) {
		if (food.inHotbar()) {
			client.selectSlot(food.slot());
			return;
		}

		swappedFrom = food.slot();
		swappedTo = client.selectedSlot();
		client.swap(swappedFrom, swappedTo);
	}

	private void waitForTheBite(EatContext client) {
		if (choose(client) == null) {
			abort(client);
			return;
		}

		if (client.usingItem()) {
			state = State.EATING;
			client.holdUseKey(true);
			return;
		}

		if (++waited > START_TIMEOUT_TICKS) {
			abort(client);
			return;
		}

		startTheBite(client);
	}

	private void finishWhenTheBiteEnds(EatContext client) {
		if (client.usingItem()) {
			client.holdUseKey(true);
			return;
		}

		abort(client);
	}

	/**
	 * Releases the key and undoes everything the bite disturbed. The swap is always undone,
	 * so the hotbar keeps the layout the user arranged; re-selecting the old slot is the
	 * part they can turn off. Safe to call when nothing is in flight.
	 */
	private void abort(EatContext client) {
		undo(client);
		hands.release(this);
	}

	/**
	 * Puts everything back without letting go of the hands, which is what an interruption
	 * needs: the lock has already struck this module off by the time it says so.
	 */
	private void undo(EatContext client) {
		if (state == State.IDLE) {
			return;
		}

		state = State.IDLE;
		client.holdUseKey(false);

		// Always, however the meal ended: finished, interrupted, or the module switched off
		// mid-bite. Whatever the off hand was holding is the player's, not ours to keep.
		if (offhandFrom != NO_SWAP) {
			client.swapToOffhand(offhandFrom);
			offhandFrom = NO_SWAP;
			reachingFor = "";
		}

		if (swappedFrom != NO_SWAP) {
			client.swap(swappedFrom, swappedTo);
			swappedFrom = NO_SWAP;
		}

		// Nothing to go back to when the selection was never left: an off-hand bite does not
		// touch it, and re-selecting would be a change where the point was to make none.
		if (hand == EatingHand.MAIN_HAND && settings.returnToSlot()) {
			client.selectSlot(previousSlot);
		}
	}

	/**
	 * Low health calls for a golden apple whatever the hunger bar says; otherwise the
	 * ordinary rules apply, and only once hunger has actually dropped — or once health has,
	 * when the user asked for that not to wait.
	 */
	FoodSlot choose(EatContext client) {
		List<FoodSlot> available = client.food();
		FoodSlot emergency = goldenApple(client, available);

		if (emergency != null) {
			return emergency;
		}

		FoodSlot food = ItemPriorityResolver
			.choose(available, settings.priority(), settings.blocked(), APPLES, settings.fallback())
			.orElse(null);

		if (food == null || client.foodLevel() >= FULL) {
			return null;
		}

		return hurt(client) || hungry(client, food) ? food : null;
	}

	/** Hunger in half-drumsticks when the bar is full. The game will not let you eat past it. */
	private static final int FULL = 20;

	/**
	 * Fixed: below the user's line. Automatic: once the bar has room for every point this
	 * food gives, so steak waits for 12 and bread for 15.
	 */
	private boolean hungry(EatContext client, FoodSlot food) {
		if (settings.thresholdMode() == ThresholdMode.AUTO) {
			return FULL - client.foodLevel() >= food.nutrition();
		}

		return client.foodLevel() < settings.hungerThreshold();
	}

	/** Natural healing stops once the bar drops, so while hurt, any gap in it is worth filling. */
	private boolean hurt(EatContext client) {
		return settings.eatEarlyWhenHurt() && client.health() < settings.earlyEatingHealth();
	}

	/**
	 * A blocked apple stays blocked: the block list outranks the emergency too. The enchanted
	 * one is far rarer than the plain one, so it is only in play once asked for.
	 */
	private FoodSlot goldenApple(EatContext client, List<FoodSlot> available) {
		if (!settings.goldenApple() || client.health() >= settings.healthThreshold()) {
			return null;
		}

		List<String> wanted = settings.enchantedGoldenApple() ? APPLES : List.of(GOLDEN_APPLE);

		return ItemPriorityResolver.choose(available, wanted, settings.blocked(), Fallback.NOTHING).orElse(null);
	}
}
