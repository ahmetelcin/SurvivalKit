package com.merci.survivalkit.wdl;

import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Holds trade offers, profession level, and experience captured from an open merchant screen.
 */
public record VillagerTradeData(MerchantOffers offers, int level, int xp) {
	public VillagerTradeData {
		if (offers == null) {
			offers = new MerchantOffers();
		}
	}
}
