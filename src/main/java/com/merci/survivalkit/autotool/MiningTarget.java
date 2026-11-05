package com.merci.survivalkit.autotool;

/**
 * The block under the crosshair.
 *
 * @param blockId          namespaced id, spelled the way the block lists spell it
 * @param needsCorrectTool the block drops nothing unless broken with the right tool
 */
public record MiningTarget(String blockId, boolean needsCorrectTool) {
}
