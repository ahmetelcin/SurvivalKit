package com.merci.survivalkit.module;

/**
 * Reports whether a module's bound key was pressed since the last poll.
 * Keeps {@link ModuleManager} independent of Minecraft's key handling.
 */
@FunctionalInterface
public interface TogglePoller {
	boolean consumeToggle(Module module);
}
