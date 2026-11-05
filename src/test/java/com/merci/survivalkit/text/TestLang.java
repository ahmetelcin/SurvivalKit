package com.merci.survivalkit.text;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The shipped language files, as a plain map, for tests that need real words.
 *
 * <p>Tests read the same files the game reads rather than a fixture, so a test that passes
 * is a statement about what players will actually see.
 */
public final class TestLang {
	private TestLang() {
	}

	/** Every key and its text, for a language shipped with the mod ("en_us"). */
	public static Map<String, String> load(String language) {
		String path = "/assets/survivalkit/lang/" + language + ".json";

		try (InputStream stream = TestLang.class.getResourceAsStream(path)) {
			if (stream == null) {
				throw new AssertionError("dil dosyası yok: " + path);
			}

			JsonObject json = JsonParser.parseReader(
				new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			Map<String, String> entries = new LinkedHashMap<>();
			json.entrySet().forEach(entry -> entries.put(entry.getKey(), entry.getValue().getAsString()));
			return entries;
		} catch (IOException e) {
			throw new AssertionError("dil dosyası okunamadı: " + path, e);
		}
	}

	/** Installs a language so display names come back as words, the way the game shows them. */
	public static void install(String language) {
		Map<String, String> entries = load(language);
		Lang.use(key -> entries.getOrDefault(key, key));
	}
}
