package com.merci.survivalkit.autototem;

/**
 * When the totem is wanted in the off hand.
 *
 * <p>Always is the simple answer and costs the off hand permanently. The health threshold
 * leaves the hand free while things are going well and only takes it when they are not,
 * which is worth something to anyone who keeps a shield or a map there.
 */
public enum TotemMode {
	ALWAYS,
	HEALTH_THRESHOLD
}
