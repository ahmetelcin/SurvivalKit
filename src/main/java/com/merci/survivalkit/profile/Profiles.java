package com.merci.survivalkit.profile;

import com.google.gson.JsonObject;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.module.ModuleManager;
import com.merci.survivalkit.settings.SettingsStorage;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The profile in use and the switch from one to another. A switch saves the one being left,
 * switches off every module that is on — each through its own switch-off, so a held key is let
 * go and a borrowed hand given back — and reads the next over a clean slate. Whatever is on in
 * the next one is switched on by the module manager's next tick.
 *
 * <p>Two are built in and can be neither renamed nor deleted: "default", and "safe" — in which
 * every module is off each time it is switched to, whatever was left on in it last time.
 */
public final class Profiles {
	private final ProfileStore store;
	private final ModuleManager modules;
	private final Path legacyFile;
	private String active;
	private SettingsStorage storage;

	/** @param legacyFile where settings lived before profiles; read once, into "default" only */
	public Profiles(ProfileStore store, ModuleManager modules, Path legacyFile) {
		this.store = Objects.requireNonNull(store, "store");
		this.modules = Objects.requireNonNull(modules, "modules");
		this.legacyFile = legacyFile;
		this.active = store.active();
		this.storage = storageFor(active);
	}

	/** Reads the profile in use over whatever the settings hold: the game starting. */
	public void load() {
		storage.load(modules.settings());
		keepSafe();
	}

	public void save() {
		storage.save(modules.settings());
	}

	/** Throws away the settings in memory and takes what is on disk for the profile in use. */
	public void reload() {
		storage.reload(modules.settings());
		keepSafe();
	}

	public String active() {
		return active;
	}

	public List<String> list() {
		return store.list();
	}

	public ProfileStore store() {
		return store;
	}

	/** Where the profile in use is written. */
	public Path file() {
		return storage.file();
	}

	/** What a profile is called on screen: "safe" in the language in use, the rest as named. */
	public static String label(String name) {
		return name.equals(ProfileStore.SAFE) ? Lang.of(Keys.PROFILE_SAFE) : name;
	}

	/** The switch itself; to the one in use already, or to one that is not there, nothing happens. */
	public void switchTo(String name) {
		if (name.equals(active) || !store.exists(name)) {
			return;
		}

		save();

		for (Module module : modules.modules()) {
			module.setEnabled(false);
		}

		active = name;
		store.setActive(name);
		storage = storageFor(name);
		storage.reload(modules.settings());
		keepSafe();
	}

	/** In "safe", nothing is on: not even what was switched on in it last time. */
	private void keepSafe() {
		if (active.equals(ProfileStore.SAFE)) {
			for (Module module : modules.modules()) {
				module.setEnabled(false);
			}
		}
	}

	/**
	 * A world was joined: the profile its rules choose, if that is another one, is switched to.
	 *
	 * @param address the server's address, or null for singleplayer
	 * @return what was switched to and why; empty if nothing changed
	 */
	public Optional<ServerRules.Choice> joined(String address) {
		Optional<ServerRules.Choice> choice = rules().choose(address, store::exists)
			.filter(chosen -> !chosen.profile().equals(active));
		choice.ifPresent(chosen -> switchTo(chosen.profile()));
		return choice;
	}

	/** The rules that choose a profile on joining a world, as they are on disk. */
	public ServerRules rules() {
		return ServerRules.read(store.state());
	}

	public void setRules(ServerRules rules) {
		JsonObject state = store.state();
		rules.write(state);
		store.writeState(state);
	}

	/** A new profile, every setting at its default, and a switch to it. */
	public Optional<ProfileNames.Problem> create(String name) {
		Optional<ProfileNames.Problem> problem = ProfileNames.check(name, taken(), null);

		if (problem.isEmpty()) {
			store.create(name.strip());
			switchTo(name.strip());
		}

		return problem;
	}

	/** The profile in use, as it is now, under another name, and a switch to the copy. */
	public Optional<ProfileNames.Problem> copy(String name) {
		Optional<ProfileNames.Problem> problem = ProfileNames.check(name, taken(), null);

		if (problem.isEmpty()) {
			save();
			store.copy(active, name.strip());
			switchTo(name.strip());
		}

		return problem;
	}

	/** The profile in use under another name; the built-in ones keep theirs. Rules follow it. */
	public Optional<ProfileNames.Problem> rename(String name) {
		if (!canChange(active)) {
			return Optional.of(ProfileNames.Problem.TAKEN);
		}

		Optional<ProfileNames.Problem> problem = ProfileNames.check(name, taken(), active);

		if (problem.isEmpty() && !name.strip().equals(active)) {
			save();
			ServerRules rules = rules();
			rules.renamed(active, name.strip());
			store.rename(active, name.strip());
			setRules(rules);
			active = name.strip();
			storage = storageFor(active);
		}

		return problem;
	}

	/** The profile in use, gone, and a switch to "default" — which itself cannot go. Rules for it go too. */
	public boolean delete() {
		if (!canChange(active)) {
			return false;
		}

		String gone = active;
		switchTo(ProfileStore.DEFAULT);
		store.delete(gone);
		ServerRules rules = rules();
		rules.deleted(gone);
		setRules(rules);
		return true;
	}

	/** Whether a profile may be renamed or deleted: all but the two built in. */
	public static boolean canChange(String name) {
		return !name.equals(ProfileStore.DEFAULT) && !name.equals(ProfileStore.SAFE);
	}

	/** Every name in use, and "safe" as it is shown too: a profile called "Güvenli" would be two of them. */
	private List<String> taken() {
		List<String> taken = new ArrayList<>(list());
		taken.add(label(ProfileStore.SAFE));
		return taken;
	}

	private SettingsStorage storageFor(String name) {
		return name.equals(ProfileStore.DEFAULT)
			? new SettingsStorage(store.file(name), legacyFile)
			: new SettingsStorage(store.file(name));
	}
}
