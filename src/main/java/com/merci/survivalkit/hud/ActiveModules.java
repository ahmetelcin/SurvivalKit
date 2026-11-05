package com.merci.survivalkit.hud;

import com.merci.survivalkit.module.Module;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** The modules that are on, by name: what the active module list says. The HUD itself is left out. */
public final class ActiveModules implements HudPanel {
	public static final String ID = "active_modules";

	private final Supplier<List<Module>> modules;
	private final Supplier<ActiveSort> sort;
	private final HudPlacement placement;
	private final String self;

	/**
	 * @param self the HUD module's id, which is never listed: it being on is the list being there
	 */
	public ActiveModules(Supplier<List<Module>> modules, Supplier<ActiveSort> sort, HudPlacement placement, String self) {
		this.modules = modules;
		this.sort = sort;
		this.placement = placement;
		this.self = self;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public HudPlacement placement() {
		return placement;
	}

	@Override
	public List<HudLine> lines() {
		return names(modules.get(), sort.get(), self).stream().map(HudLine::of).toList();
	}

	static List<String> names(List<Module> modules, ActiveSort sort, String self) {
		List<String> names = modules.stream()
			.filter(module -> module.isEnabled() && !module.id().equals(self))
			.map(Module::displayName)
			.toList();

		Collator collator = Collator.getInstance(Locale.ROOT);
		return switch (sort) {
			case MENU -> names;
			case NAME -> names.stream().sorted(collator).toList();
			case LENGTH -> names.stream().sorted(Comparator.<String>comparingInt(String::length).reversed().thenComparing(collator)).toList();
		};
	}
}
