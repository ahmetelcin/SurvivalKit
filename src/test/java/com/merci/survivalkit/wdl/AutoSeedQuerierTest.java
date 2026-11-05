package com.merci.survivalkit.wdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import com.merci.survivalkit.settings.TextSetting;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutoSeedQuerierTest {
	private WorldDownloaderModule module;
	private AutoSeedQuerier querier;
	private TextSetting seedSetting;

	@BeforeEach
	void setUp() {
		module = new WorldDownloaderModule();
		querier = module.seedQuerier();
		seedSetting = module.settings().<TextSetting>byId("seed").orElseThrow();
	}

	@Test
	void hasSeedPermissionDetectsPresenceAndAbsence() {
		CommandDispatcher<Object> withSeed = new CommandDispatcher<>();
		withSeed.register(LiteralArgumentBuilder.literal("help"));
		withSeed.register(LiteralArgumentBuilder.literal("seed"));

		CommandDispatcher<Object> withoutSeed = new CommandDispatcher<>();
		withoutSeed.register(LiteralArgumentBuilder.literal("help"));
		withoutSeed.register(LiteralArgumentBuilder.literal("msg"));

		assertTrue(AutoSeedQuerier.hasSeedPermission(withSeed), "seed ağaçta varken izin var sayılmalı");
		assertFalse(AutoSeedQuerier.hasSeedPermission(withoutSeed), "seed ağaçta yokken izin yok sayılmalı");
		assertFalse(AutoSeedQuerier.hasSeedPermission(null), "null ağaçta izin yok sayılmalı");
	}

	@Test
	void parseSeedFromVanillaTranslationWithLongArg() {
		Component msg = Component.translatable("commands.seed.success", 1234567890123L);
		Optional<String> parsed = AutoSeedQuerier.parseSeed(msg);
		assertTrue(parsed.isPresent());
		assertEquals("1234567890123", parsed.get());
	}

	@Test
	void parseSeedFromVanillaTranslationWithNegativeLong() {
		Component msg = Component.translatable("commands.seed.success", -9876543210L);
		Optional<String> parsed = AutoSeedQuerier.parseSeed(msg);
		assertTrue(parsed.isPresent());
		assertEquals("-9876543210", parsed.get());
	}

	@Test
	void parseSeedFromVanillaTranslationWithComponentArg() {
		Component arg = Component.literal("424242");
		Component msg = Component.translatable("commands.seed.success", arg);
		Optional<String> parsed = AutoSeedQuerier.parseSeed(msg);
		assertTrue(parsed.isPresent());
		assertEquals("424242", parsed.get());
	}

	@Test
	void parseSeedFromEnglishChatMessage() {
		Component msg = Component.literal("Seed: [888999111]");
		Optional<String> parsed = AutoSeedQuerier.parseSeed(msg);
		assertTrue(parsed.isPresent());
		assertEquals("888999111", parsed.get());
	}

	@Test
	void parseSeedFromTurkishChatMessage() {
		Component msg = Component.literal("Tohum: [-555666]");
		Optional<String> parsed = AutoSeedQuerier.parseSeed(msg);
		assertTrue(parsed.isPresent());
		assertEquals("-555666", parsed.get());
	}

	@Test
	void parseSeedFromCustomServerMessage() {
		Component msg = Component.literal("World seed is: 1337");
		Optional<String> parsed = AutoSeedQuerier.parseSeed(msg);
		assertTrue(parsed.isPresent());
		assertEquals("1337", parsed.get());
	}

	@Test
	void nonSeedMessageReturnsEmpty() {
		Component msg = Component.literal("<Player> Hello world!");
		Optional<String> parsed = AutoSeedQuerier.parseSeed(msg);
		assertFalse(parsed.isPresent());
	}

	@Test
	void onGameMessageIgnoredWhenNotAwaitingResponse() {
		seedSetting.set("initial");
		assertFalse(querier.isAwaitingResponse());

		Component msg = Component.translatable("commands.seed.success", 99999L);
		querier.onGameMessage(msg, false);

		assertEquals("initial", seedSetting.get(), "Cevap beklenmiyorken mesaj ayarı değiştirmemeli");
	}

	@Test
	void unsolicitedChatMessageSeedIgnoredWhenNotAwaitingResponse() {
		seedSetting.set("my_seed");
		assertFalse(querier.isAwaitingResponse());

		// Sunucu veya sohbet mesajı olarak "seed 12345" geldiğinde, sorgu penceresi kapalıysa ezilmemeli
		Component msg = Component.literal("<Player1> seed 12345");
		querier.onGameMessage(msg, false);

		assertEquals("my_seed", seedSetting.get(), "Sorgusuz gelen seed mesajı ayarı ezmemeli");
	}

	@Test
	void onGameMessageUpdatesSettingWhenAwaitingResponse() throws Exception {
		seedSetting.set("");
		// Reflectively set awaitingResponse to true to simulate pending request
		setAwaitingResponse(true);
		assertTrue(querier.isAwaitingResponse());

		Component msg = Component.translatable("commands.seed.success", 1234567890L);
		querier.onGameMessage(msg, false);

		assertFalse(querier.isAwaitingResponse(), "Mesaj alındıktan sonra bekleme bitmeli");
		assertEquals("1234567890", seedSetting.get(), "Ayara seed yazılmalı");
	}

	@Test
	void firstMatchStopsListeningAndSubsequentMessagesIgnored() throws Exception {
		seedSetting.set("");
		setAwaitingResponse(true);

		Component firstMsg = Component.literal("Seed: [111222]");
		querier.onGameMessage(firstMsg, false);
		assertEquals("111222", seedSetting.get());
		assertFalse(querier.isAwaitingResponse(), "İlk eşleşmede dinleme bırakılmalı");

		Component secondMsg = Component.literal("Seed: [999999]");
		querier.onGameMessage(secondMsg, false);
		assertEquals("111222", seedSetting.get(), "İlk eşleşmeden sonraki mesajlar ayarı değiştirmemeli");
	}

	@Test
	void timeoutExpiresAfter100TicksAndIgnoresLateMessages() throws Exception {
		seedSetting.set("original");
		setAwaitingResponse(true);

		// 101 tick işlet (5 saniye timeout simülasyonu)
		for (int i = 0; i <= 100; i++) {
			querier.onTick(null);
		}

		assertFalse(querier.isAwaitingResponse(), "100 tick sonra bekleme penceresi zaman aşımına uğramalı");

		Component lateMsg = Component.literal("Seed: [555444]");
		querier.onGameMessage(lateMsg, false);
		assertEquals("original", seedSetting.get(), "Zaman aşımından sonra gelen mesaj ayarı değiştirmemeli");
	}

	@Test
	void autoQuerySeedSettingDisabledPreventsQuery() {
		module.autoQuerySeed().set(false);
		querier.reset();

		querier.onTick(null);
		assertFalse(querier.hasQueried(), "Ayar kapalıyken sorgu tetiklenmemeli");
		assertFalse(querier.isAwaitingResponse(), "Ayar kapalıyken yanıt beklenmemeli");
	}

	private void setAwaitingResponse(boolean awaiting) throws Exception {
		java.lang.reflect.Field field = AutoSeedQuerier.class.getDeclaredField("awaitingResponse");
		field.setAccessible(true);
		field.set(querier, awaiting);
	}
}
