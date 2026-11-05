package com.merci.survivalkit.weapon;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Watches the auto hit and writes to the log when it sits in one state for over two seconds.
 * It changes nothing: it is there so that a stall in a real game can be read off the log.
 *
 * <p>Time is counted in the ticks it is shown, one per client tick, so a paused game, an open
 * screen or a new world never adds up to a stall; anything that is not the auto hit at work
 * — nothing aimed, no weapon, the setting off — ends the count.
 */
final class AutoHitWatch {
	/** Where the auto hit is on a tick it is at work. */
	enum State {
		/** Not at work this tick: nothing aimed, no weapon in hand, auto hit off, or no game. */
		IDLE,
		/** The selection was moved to the best weapon this tick; no hit on a switch tick. */
		SWITCH,
		/** Waiting for the attack cooldown to fill. */
		COOLDOWN,
		/** A hit went out this tick. */
		ATTACK
	}

	/** Two seconds; a longer stay in one state is reported. */
	static final int LIMIT_TICKS = 40;

	private final Consumer<String> log;
	private State state = State.IDLE;
	private int ticks;
	private boolean reported;

	AutoHitWatch(Consumer<String> log) {
		this.log = Objects.requireNonNull(log, "log");
	}

	/**
	 * One tick's state.
	 *
	 * @param detail what else was true this tick, for the log line; only built when it is written
	 */
	void observe(State now, Supplier<String> detail) {
		if (now != state) {
			if (reported) {
				log.accept("Oto Silah otomatik vur takılması bitti: " + state + " " + ticks + " tick sürdü, şimdi " + now);
			}

			state = now;
			ticks = 0;
			reported = false;
		}

		ticks++;

		if (state != State.IDLE && !reported && ticks > LIMIT_TICKS) {
			reported = true;
			log.accept("Oto Silah otomatik vur " + ticks + " tick'tir " + state + " durumunda takılı: " + detail.get());
		}
	}
}
