package com.merci.survivalkit.autototem;

import java.util.OptionalInt;

/** What {@link AutoTotem} may ask of the running client, and what it may tell it to do. */
public interface TotemContext {
	/** False while there is no player or a screen is open. */
	boolean ready();

	/** Health in half-hearts, 0-20. */
	float health();

	/** The namespaced id in the off hand, or an empty string when it is empty. */
	String offhandId();

	/**
	 * The first slot within the configured search scope holding this id.
	 *
	 * <p>By id rather than by item, because that is how the lists the user builds spell
	 * things, and because the fallback list asks the same question about other items.
	 */
	OptionalInt slotOf(String id);

	/** How many of this id the player carries anywhere: the whole inventory and both hands, whatever the scope. */
	int count(String id);

	/** Puts one line in the player's own chat. Nobody else sees it. */
	void say(String message);

	/**
	 * Exchanges an inventory slot with the off hand. Symmetric: calling it again with the
	 * same slot puts both back. The main hand is never involved.
	 */
	void swapToOffhand(int inventorySlot);
}
