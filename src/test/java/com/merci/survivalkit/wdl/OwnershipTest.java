package com.merci.survivalkit.wdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class OwnershipTest {
	@Test
	void transferToLocalOverwritesForeignOwnerTag() {
		UUID foreignUuid = UUID.randomUUID();
		UUID localUuid = UUID.randomUUID();

		CompoundTag tag = new CompoundTag();
		tag.store("Owner", UUIDUtil.CODEC, foreignUuid);
		tag.putString("id", "minecraft:wolf");

		ClientEntitySerializer.applyOwnershipTransfer(null, tag, localUuid);

		UUID currentOwner = tag.read("Owner", UUIDUtil.CODEC).orElseThrow();
		assertEquals(localUuid, currentOwner, "Sahiplik aktarımında yabancı sahip yerel oyuncu UUID'si ile ezilmeli");
	}

	@Test
	void transferToLocalDoesNotAddOwnerToUntamedEntities() {
		UUID localUuid = UUID.randomUUID();

		CompoundTag cowTag = new CompoundTag();
		cowTag.putString("id", "minecraft:cow");

		ClientEntitySerializer.applyOwnershipTransfer(null, cowTag, localUuid);

		assertFalse(cowTag.contains("Owner"), "Evcil/sahipsiz varlığa sahte sahip atanmamalı");
	}

	@Test
	void transferToLocalPreservesWhenPlayerUuidIsNull() {
		UUID foreignUuid = UUID.randomUUID();

		CompoundTag tag = new CompoundTag();
		tag.store("Owner", UUIDUtil.CODEC, foreignUuid);

		ClientEntitySerializer.applyOwnershipTransfer(null, tag, null);

		UUID currentOwner = tag.read("Owner", UUIDUtil.CODEC).orElseThrow();
		assertEquals(foreignUuid, currentOwner, "Yerel UUID null ise etiket korunmalı");
	}

	@Test
	void moduleDefaultsToAsIsAndCycles() {
		WorldDownloaderModule module = new WorldDownloaderModule();
		assertEquals(OwnershipMode.AS_IS, module.ownership().get(), "Varsayılan sahiplik modu AS_IS (Olduğu gibi) olmalı");

		module.ownership().cycle();
		assertEquals(OwnershipMode.TRANSFER_TO_LOCAL, module.ownership().get(), "Bir sonraki seçenek TRANSFER_TO_LOCAL olmalı");

		module.ownership().cycle();
		assertEquals(OwnershipMode.AS_IS, module.ownership().get(), "Döngü başa sarmalı");
	}
}
