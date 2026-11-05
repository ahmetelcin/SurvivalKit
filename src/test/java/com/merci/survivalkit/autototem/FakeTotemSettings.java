package com.merci.survivalkit.autototem;

/** The settings as plain fields, so a test can change one between ticks the way the menu would. */
final class FakeTotemSettings implements TotemSettings {
	TotemMode mode = TotemMode.ALWAYS;
	int healthThreshold = 8;
	boolean restoreOffhand = true;
	java.util.List<String> fallback = java.util.List.of();
	boolean warnWhenOut = true;

	@Override
	public java.util.List<String> fallback() {
		return fallback;
	}

	@Override
	public boolean warnWhenOut() {
		return warnWhenOut;
	}

	@Override
	public TotemMode mode() {
		return mode;
	}

	@Override
	public int healthThreshold() {
		return healthThreshold;
	}

	@Override
	public boolean restoreOffhand() {
		return restoreOffhand;
	}
}
