package com.merci.survivalkit.scaffold;

import com.merci.survivalkit.vein.Pos;

/**
 * A click that puts a block into {@link #target}: on {@link #face} of the neighbour {@link #support},
 * at the middle of that face. It is the click a player makes by looking at that face and pressing
 * use — the server checks that the point is on the clicked block and the block is in reach, never
 * where the player is looking, and the block goes down through vanilla's own placement.
 */
public record Placement(Pos target, Pos support, Face face, Vec hit) {
}
