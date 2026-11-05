package com.merci.survivalkit.module;

import com.merci.survivalkit.settings.KeybindSetting;

/** Test double that records how often each lifecycle hook ran. */
final class RecordingModule extends Module {
	int enableCalls;
	int disableCalls;
	int tickCalls;

	RecordingModule(String id) {
		this(id, Category.MISC);
	}

	RecordingModule(String id, Category category) {
		super(id, category, KeybindSetting.UNBOUND);
	}

	@Override
	protected void onEnable() {
		enableCalls++;
	}

	@Override
	protected void onDisable() {
		disableCalls++;
	}

	@Override
	protected void onTick() {
		tickCalls++;
	}
}
