package com.merci.survivalkit.weapon;

/**
 * What the rule needs to know about a target, read from the game's entity tags.
 *
 * @param sensitiveToSmite the target is in {@code #sensitive_to_smite}
 * @param sensitiveToBane  the target is in {@code #sensitive_to_bane_of_arthropods}
 */
public record TargetKind(boolean sensitiveToSmite, boolean sensitiveToBane) {
}
