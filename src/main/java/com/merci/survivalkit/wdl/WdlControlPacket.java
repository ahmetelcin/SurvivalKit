package com.merci.survivalkit.wdl;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * One packet a server sends the old World Downloader mod on {@code wdl:control}, cut down to
 * the one flag that decides whether downloading may go ahead.
 *
 * <p>The mod reads it as a Java data stream: the section number, then that section's fields.
 * Section 0 carries whether features the server does not know about may be used; section 1
 * opens with whether downloading is allowed at all. The rest of section 1 (save radius, what
 * may be saved) and sections 2 to 7 are left unread — none of it can turn a refusal around.
 *
 * @param flag section 0: may unknown features be used; section 1: may the world be downloaded
 */
public record WdlControlPacket(int section, boolean flag) {
	public static final int UNKNOWN_FEATURES = 0;
	public static final int PERMISSIONS = 1;

	/** Empty for anything short, cut off or of a section that says nothing about downloading. */
	public static Optional<WdlControlPacket> parse(byte[] bytes) {
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
			int section = in.readInt();

			if (section != UNKNOWN_FEATURES && section != PERMISSIONS) {
				return Optional.empty();
			}

			boolean flag = in.readBoolean();

			// Section 1 is only believed whole: a packet cut off after its first flag may be a
			// garbled one, and a refusal read from garbage is no refusal.
			if (section == PERMISSIONS) {
				in.readInt();
				in.readBoolean();
				in.readBoolean();
				in.readBoolean();
				in.readBoolean();
			}

			return Optional.of(new WdlControlPacket(section, flag));
		} catch (IOException cutShort) {
			return Optional.empty();
		}
	}
}
