package com.merci.survivalkit.wdl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Turns a server address into a world folder name that Windows will accept. */
public final class SaveName {
	/** Leaves room for the deep paths under the folder: region/r.-1.-1.mca and friends. */
	public static final int MAX_LENGTH = 64;

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("-yyyyMMdd-HHmmss");
	private static final String FALLBACK = "sunucu";

	private SaveName() {
	}

	/**
	 * The one folder a server's downloads all go into, so a later download adds to the same
	 * world instead of starting another.
	 */
	public static String forServer(String serverAddress) {
		String host = clean(withoutPort(serverAddress));

		if (host.length() > MAX_LENGTH) {
			host = clean(host.substring(0, MAX_LENGTH));
		}

		return host.isEmpty() ? FALLBACK : host;
	}

	public static String of(String serverAddress, LocalDateTime when) {
		String stamp = STAMP.format(when);
		String host = clean(withoutPort(serverAddress));
		int room = MAX_LENGTH - stamp.length();

		if (host.length() > room) {
			host = clean(host.substring(0, room));
		}

		return (host.isEmpty() ? FALLBACK : host) + stamp;
	}

	private static String withoutPort(String serverAddress) {
		int colon = serverAddress.lastIndexOf(':');
		return colon < 0 ? serverAddress : serverAddress.substring(0, colon);
	}

	/**
	 * Keeps letters, digits and the few punctuation marks that read well in a folder name;
	 * everything else goes. Trailing dots and spaces are dropped because Windows silently
	 * strips them, which would make the folder unopenable.
	 */
	private static String clean(String raw) {
		StringBuilder cleaned = new StringBuilder(raw.length());

		for (char character : raw.toCharArray()) {
			if (Character.isLetterOrDigit(character) || character == '.' || character == '-' || character == '_' || character == ' ') {
				cleaned.append(character);
			}
		}

		while (!cleaned.isEmpty() && (cleaned.charAt(cleaned.length() - 1) == '.' || cleaned.charAt(cleaned.length() - 1) == ' ')) {
			cleaned.setLength(cleaned.length() - 1);
		}

		return cleaned.toString().strip();
	}
}
