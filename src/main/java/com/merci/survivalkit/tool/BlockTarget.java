package com.merci.survivalkit.tool;

/**
 * What the rule needs to know about the block being broken.
 *
 * @param needsCorrectTool true when the block drops nothing unless broken with the right tool,
 *                         which is also when the bare hand drops nothing
 * @param wantsSilkTouch   the block is on the user's Silk Touch list
 * @param wantsFortune     the block is on the user's Fortune list
 */
public record BlockTarget(boolean needsCorrectTool, boolean wantsSilkTouch, boolean wantsFortune) {
}
