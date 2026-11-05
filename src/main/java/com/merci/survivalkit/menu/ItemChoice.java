package com.merci.survivalkit.menu;

import java.util.Objects;

/**
 * One thing the picker can offer, reduced to what choosing needs.
 *
 * @param id          namespaced id, as the list stores it
 * @param displayName what the game calls it in the player's language
 */
public record ItemChoice(String id, String displayName) {
	public ItemChoice {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(displayName, "displayName");
	}
}
