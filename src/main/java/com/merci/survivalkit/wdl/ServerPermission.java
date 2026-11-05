package com.merci.survivalkit.wdl;

/**
 * Whether the server has said downloading is allowed, by the old World Downloader mod's rule.
 *
 * <p>A server that says nothing allows it. Section 1 of the control channel is the answer when
 * it has come; until it has, section 0's word on unknown features stands in for it. So a server
 * that only says "nothing you do not know about" still keeps the download out.
 *
 * <p>Kept for one connection: {@link #reset} starts over when the player joins another server.
 */
public final class ServerPermission {
	private boolean canUseUnknownFeatures = true;
	private boolean canDownload = true;
	private boolean heardPermissions;

	public synchronized void accept(byte[] packet) {
		WdlControlPacket.parse(packet).ifPresent(parsed -> {
			if (parsed.section() == WdlControlPacket.PERMISSIONS) {
				canDownload = parsed.flag();
				heardPermissions = true;
			} else {
				canUseUnknownFeatures = parsed.flag();
			}
		});
	}

	public synchronized void reset() {
		canUseUnknownFeatures = true;
		canDownload = true;
		heardPermissions = false;
	}

	public synchronized boolean allowed() {
		return heardPermissions ? canDownload : canUseUnknownFeatures;
	}
}
