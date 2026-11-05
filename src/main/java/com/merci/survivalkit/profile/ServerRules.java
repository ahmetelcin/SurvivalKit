package com.merci.survivalkit.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Which profile to be in on joining a world: address rules, top to bottom, the first that
 * matches winning; one for singleplayer; and one for when nothing matched. Each of the last two
 * may be "keep", which leaves whatever profile is in use alone — and both are, until the player
 * says otherwise, so nobody's profile changes under them for having updated the mod.
 *
 * <p>Nothing is put here for any server on the player's behalf.
 */
public final class ServerRules {
	/** @param address what the address is matched against: a host, "*" matching anything, a port only if given */
	public record Rule(String address, String profile) {
		public Rule {
			Objects.requireNonNull(address, "address");
			Objects.requireNonNull(profile, "profile");
		}
	}

	/** Why a profile was chosen, for the notice. */
	public enum Reason {
		RULE, SINGLEPLAYER, FALLBACK
	}

	/** @param rule the address of the rule that matched, for {@link Reason#RULE} */
	public record Choice(String profile, Reason reason, String rule) {
	}

	private static final int DEFAULT_PORT = 25565;

	private final List<Rule> rules = new ArrayList<>();
	private String singleplayer;
	private String fallback;

	public List<Rule> rules() {
		return List.copyOf(rules);
	}

	public void setRules(List<Rule> rules) {
		this.rules.clear();
		this.rules.addAll(rules);
	}

	/** The profile for singleplayer, or null to keep the one in use. */
	public String singleplayer() {
		return singleplayer;
	}

	public void setSingleplayer(String profile) {
		this.singleplayer = profile;
	}

	/** The profile when no rule matches a server, or null to keep the one in use. */
	public String fallback() {
		return fallback;
	}

	public void setFallback(String profile) {
		this.fallback = profile;
	}

	/**
	 * The profile for a world just joined.
	 *
	 * @param address the server's address as the player typed it, or null for singleplayer
	 * @param exists  which profiles there are: a rule for one that is gone is passed over
	 * @return empty to keep the profile in use
	 */
	public Optional<Choice> choose(String address, Predicate<String> exists) {
		if (address == null) {
			return pick(singleplayer, Reason.SINGLEPLAYER, null, exists);
		}

		for (Rule rule : rules) {
			if (matches(rule.address(), address) && exists.test(rule.profile())) {
				return Optional.of(new Choice(rule.profile(), Reason.RULE, rule.address()));
			}
		}

		return pick(fallback, Reason.FALLBACK, null, exists);
	}

	private static Optional<Choice> pick(String profile, Reason reason, String rule, Predicate<String> exists) {
		return profile != null && exists.test(profile) ? Optional.of(new Choice(profile, reason, rule)) : Optional.empty();
	}

	/**
	 * Whether an address answers to a rule. Case does not matter, nor a trailing dot. "*" matches
	 * any run of characters, dots included, and "*.ornek.net" matches "ornek.net" itself too: that is
	 * what anybody writing it means. The port counts only if the rule names one; a server given
	 * without one is on 25565. A blank rule matches nothing.
	 */
	public static boolean matches(String rule, String address) {
		String pattern = normalise(rule);
		String host = normalise(address);

		if (pattern.isEmpty() || host.isEmpty()) {
			return false;
		}

		if (pattern.contains(":")) {
			host = withPort(host);
			pattern = withPort(pattern);
		} else {
			host = withoutPort(host);
		}

		if (pattern.startsWith("*.") && host.equals(pattern.substring(2))) {
			return true;
		}

		return glob(pattern).matcher(host).matches();
	}

	private static String normalise(String text) {
		String trimmed = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
		return trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
	}

	private static String withoutPort(String address) {
		int colon = address.lastIndexOf(':');
		return colon >= 0 && address.indexOf(':') == colon ? address.substring(0, colon) : address;
	}

	private static String withPort(String address) {
		return address.contains(":") ? address : address + ":" + DEFAULT_PORT;
	}

	private static Pattern glob(String pattern) {
		String[] parts = pattern.split("\\*", -1);
		StringBuilder regex = new StringBuilder();

		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				regex.append(".*");
			}

			regex.append(Pattern.quote(parts[i]));
		}

		return Pattern.compile(regex.toString());
	}

	/** The notice on switching: which profile, then why — the rule that matched, singleplayer, or nothing matching. */
	public static List<String> notice(Choice choice) {
		String why = switch (choice.reason()) {
			case RULE -> Lang.of(Keys.TOAST_RULE, choice.rule());
			case SINGLEPLAYER -> Lang.of(Keys.TOAST_SINGLEPLAYER);
			case FALLBACK -> Lang.of(Keys.TOAST_FALLBACK);
		};

		return List.of(Lang.of(Keys.TOAST_PROFILE, Profiles.label(choice.profile())), why);
	}

	/** A profile was renamed: the rules that chose it choose it under its new name. */
	public void renamed(String from, String to) {
		rules.replaceAll(rule -> rule.profile().equals(from) ? new Rule(rule.address(), to) : rule);
		singleplayer = from.equals(singleplayer) ? to : singleplayer;
		fallback = from.equals(fallback) ? to : fallback;
	}

	/** A profile was deleted: whatever chose it is gone, or keeps the profile in use instead. */
	public void deleted(String name) {
		rules.removeIf(rule -> rule.profile().equals(name));
		singleplayer = name.equals(singleplayer) ? null : singleplayer;
		fallback = name.equals(fallback) ? null : fallback;
	}

	/** Read from the profiles' state file; anything unreadable is simply not there. */
	public static ServerRules read(JsonObject state) {
		ServerRules read = new ServerRules();
		JsonElement rules = state.get("rules");

		if (rules != null && rules.isJsonArray()) {
			for (JsonElement element : rules.getAsJsonArray()) {
				if (element.isJsonObject() && string(element.getAsJsonObject(), "address") != null
					&& string(element.getAsJsonObject(), "profile") != null) {
					read.rules.add(new Rule(string(element.getAsJsonObject(), "address"), string(element.getAsJsonObject(), "profile")));
				}
			}
		}

		read.singleplayer = string(state, "singleplayer");
		read.fallback = string(state, "fallback");
		return read;
	}

	/** Written into the state file beside whatever else is kept there. */
	public void write(JsonObject state) {
		JsonArray array = new JsonArray();

		for (Rule rule : rules) {
			JsonObject object = new JsonObject();
			object.addProperty("address", rule.address());
			object.addProperty("profile", rule.profile());
			array.add(object);
		}

		state.add("rules", array);
		put(state, "singleplayer", singleplayer);
		put(state, "fallback", fallback);
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
	}

	private static void put(JsonObject object, String key, String value) {
		if (value == null) {
			object.remove(key);
		} else {
			object.add(key, new JsonPrimitive(value));
		}
	}
}
