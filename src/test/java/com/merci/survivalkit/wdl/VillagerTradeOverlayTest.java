package com.merci.survivalkit.wdl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VillagerTradeOverlayTest {
	private VillagerTradeOverlay overlay;
	private UUID villagerUuid;

	@BeforeAll
	static void initMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		net.minecraft.core.registries.BuiltInRegistries.ITEM.listElements().forEach(h -> h.bindComponents(net.minecraft.core.component.DataComponents.COMMON_ITEM_COMPONENTS));
	}

	@BeforeEach
	void setUp() {
		overlay = new VillagerTradeOverlay();
		villagerUuid = UUID.randomUUID();
	}

	@Test
	void captureStoresDataAndCanBeRetrieved() {
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD, 6), 16, 2, 0.05f));

		overlay.capture(villagerUuid, new VillagerTradeData(offers, 2, 10));

		assertTrue(overlay.has(villagerUuid));
		assertEquals(1, overlay.size());

		Optional<VillagerTradeData> retrieved = overlay.get(villagerUuid);
		assertTrue(retrieved.isPresent());
		assertEquals(2, retrieved.get().level());
		assertEquals(10, retrieved.get().xp());
		assertEquals(1, retrieved.get().offers().size());
	}

	@Test
	void applyWritesOffersTagToCompoundTag() {
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.APPLE, 4), 12, 1, 0.05f));

		overlay.capture(villagerUuid, new VillagerTradeData(offers, 3, 50));

		CompoundTag tag = new CompoundTag();
		tag.store("UUID", UUIDUtil.CODEC, villagerUuid);

		CompoundTag villagerData = new CompoundTag();
		villagerData.putString("profession", "minecraft:farmer");
		villagerData.putInt("level", 1);
		tag.put("VillagerData", villagerData);

		overlay.apply(villagerUuid, tag);

		assertTrue(tag.contains("Offers"));
		assertEquals(50, tag.getIntOr("Xp", 0));
		assertEquals(3, tag.getCompoundOrEmpty("VillagerData").getIntOr("level", 0));
		assertEquals("minecraft:farmer", tag.getCompoundOrEmpty("VillagerData").getStringOr("profession", ""));

		Optional<MerchantOffers> readOffers = tag.read("Offers", MerchantOffers.CODEC);
		assertTrue(readOffers.isPresent());
		assertEquals(1, readOffers.get().size());
		MerchantOffer firstOffer = readOffers.get().get(0);
		assertEquals(Items.EMERALD, firstOffer.getItemCostA().item().value());
		assertEquals(2, firstOffer.getItemCostA().count());
		assertEquals(Items.APPLE, firstOffer.getResult().getItem());
		assertEquals(4, firstOffer.getResult().getCount());
	}

	@Test
	void applyIgnoresUncapturedUuid() {
		CompoundTag tag = new CompoundTag();
		tag.store("UUID", UUIDUtil.CODEC, villagerUuid);

		overlay.apply(UUID.randomUUID(), tag);

		assertFalse(tag.contains("Offers"));
		assertFalse(tag.contains("Xp"));
	}

	@Test
	void applyIgnoresNullOrEmptyOffers() {
		overlay.capture(villagerUuid, new VillagerTradeData(new MerchantOffers(), 1, 0));

		CompoundTag tag = new CompoundTag();
		overlay.apply(villagerUuid, tag);

		assertFalse(tag.contains("Offers"));
	}

	@Test
	void clearRemovesAllCaptures() {
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD, 1), 1, 1, 0.05f));
		overlay.capture(villagerUuid, new VillagerTradeData(offers, 1, 1));
		assertEquals(1, overlay.size());

		overlay.clear();
		assertEquals(0, overlay.size());
		assertFalse(overlay.has(villagerUuid));
	}
}
