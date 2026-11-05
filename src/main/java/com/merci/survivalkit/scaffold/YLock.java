package com.merci.survivalkit.scaffold;

/** Which y every block goes in, jumping or not; with none, the level follows the feet. */
public enum YLock {
	OFF,
	/** Where the feet stood when Scaffold was switched on, or the lock was set to this. */
	START,
	/** The number in the settings. */
	FIXED
}
