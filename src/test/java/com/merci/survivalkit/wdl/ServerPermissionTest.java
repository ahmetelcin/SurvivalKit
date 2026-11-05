package com.merci.survivalkit.wdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A server can tell the old World Downloader mod, over its {@code wdl:control} channel, that
 * downloading is not allowed there. The packets are read the way that mod reads them, and the
 * answer is the one it would give.
 */
class ServerPermissionTest {
	private ServerPermission permission;

	@BeforeEach
	void setUp() {
		permission = new ServerPermission();
	}

	/** Section 0: whether features the server does not know about may be used. */
	private static byte[] section0(boolean canUseUnknown) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(0);
		out.writeBoolean(canUseUnknown);
		return bytes.toByteArray();
	}

	/** Section 1: the download permissions proper. */
	private static byte[] section1(boolean canDownload) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(1);
		out.writeBoolean(canDownload);
		out.writeInt(4);
		out.writeBoolean(true);
		out.writeBoolean(true);
		out.writeBoolean(true);
		out.writeBoolean(true);
		return bytes.toByteArray();
	}

	@Test
	void aServerThatSaysNothingAllowsIt() {
		assertTrue(permission.allowed());
	}

	@Test
	void sectionOneForbiddingIt() throws IOException {
		permission.accept(section1(false));

		assertFalse(permission.allowed());
	}

	@Test
	void sectionOneAllowingIt() throws IOException {
		permission.accept(section1(true));

		assertTrue(permission.allowed());
	}

	@Test
	void withoutSectionOneSectionZeroDecides() throws IOException {
		permission.accept(section0(false));

		assertFalse(permission.allowed());
	}

	@Test
	void sectionOneOverridesSectionZero() throws IOException {
		permission.accept(section0(false));
		permission.accept(section1(true));

		assertTrue(permission.allowed());
	}

	@Test
	void aLaterSectionZeroDoesNotOverrideSectionOne() throws IOException {
		permission.accept(section1(false));
		permission.accept(section0(true));

		assertFalse(permission.allowed());
	}

	@Test
	void aTruncatedPacketChangesNothing() throws IOException {
		byte[] whole = section1(false);
		byte[] cut = java.util.Arrays.copyOf(whole, 5);

		permission.accept(cut);

		assertTrue(permission.allowed());
	}

	@Test
	void anUnknownSectionChangesNothing() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		new DataOutputStream(bytes).writeInt(6);

		permission.accept(bytes.toByteArray());

		assertTrue(permission.allowed());
	}

	@Test
	void anEmptyPacketChangesNothing() {
		permission.accept(new byte[0]);

		assertTrue(permission.allowed());
	}

	@Test
	void anotherServerStartsFresh() throws IOException {
		permission.accept(section1(false));

		permission.reset();

		assertTrue(permission.allowed());
	}

	@Test
	void theParserReadsTheSectionAndItsFlag() throws IOException {
		assertEquals(Optional.of(new WdlControlPacket(1, false)), WdlControlPacket.parse(section1(false)));
		assertEquals(Optional.of(new WdlControlPacket(0, true)), WdlControlPacket.parse(section0(true)));
	}
}
