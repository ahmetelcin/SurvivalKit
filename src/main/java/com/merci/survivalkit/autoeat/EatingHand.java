package com.merci.survivalkit.autoeat;

/**
 * Which hand a bite is taken with.
 *
 * <p>The main hand is the simple one: the food is selected and the use key is held, which is
 * what a player does. It costs the hand for as long as the bite lasts, and whatever was held
 * has to be found again afterwards.
 *
 * <p>The off hand leaves the main hand alone entirely — the sword, the pickaxe or the stack
 * of blocks stays exactly where it is, which is the point. It costs more care instead: the
 * food has to be put in the off hand and taken back out, and the bite has to be started for
 * that hand specifically rather than by pressing use, which would run the main hand first.
 *
 * <p>Kept free of {@code InteractionHand} so the state machine stays testable without a game;
 * {@link ClientEatContext} is where the two meet.
 */
public enum EatingHand {
	MAIN_HAND,
	OFF_HAND
}
