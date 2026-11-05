package com.merci.survivalkit.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Holds namespaced ids (items, dimensions) as plain strings — no registry lookup here. */
class RegistryListSettingTest {
	/** An id no build of Minecraft knows: nothing may quietly drop it. */
	private static final String UNKNOWN_ID = "bazımod:efsanevi_meyve";

	private static RegistryListSetting items() {
		return new RegistryListSetting("autoeat.foods", "test.autoeat.foods", List.of());
	}

	private static RegistryListSetting dimensions() {
		return new RegistryListSetting("wdl.dimensions", "test.wdl.dimensions", List.of("minecraft:overworld"));
	}

	@Test
	void aFreshSettingHoldsItsDefaults() {
		RegistryListSetting setting = dimensions();

		assertEquals(List.of("minecraft:overworld"), setting.get());
	}

	@Test
	void addAppendsAnId() {
		RegistryListSetting setting = dimensions();

		setting.add("minecraft:the_nether");

		assertEquals(List.of("minecraft:overworld", "minecraft:the_nether"), setting.get());
	}

	@Test
	void addIgnoresADuplicate() {
		RegistryListSetting setting = dimensions();

		setting.add("minecraft:overworld");

		assertEquals(List.of("minecraft:overworld"), setting.get());
	}

	@Test
	void anIdWithoutANamespaceIsRejected() {
		RegistryListSetting setting = dimensions();

		assertThrows(IllegalArgumentException.class, () -> setting.add("the_nether"));
	}

	@Test
	void removeDropsAnId() {
		RegistryListSetting setting = dimensions();

		setting.remove("minecraft:overworld");

		assertTrue(setting.get().isEmpty());
	}

	@Test
	void containsAnswersMembership() {
		RegistryListSetting setting = dimensions();

		assertTrue(setting.contains("minecraft:overworld"));
		assertFalse(setting.contains("minecraft:the_end"));
	}

	@Test
	void theExposedListCannotBeModifiedFromOutside() {
		List<String> values = dimensions().get();

		assertThrows(UnsupportedOperationException.class, () -> values.add("minecraft:the_end"));
	}

	@Test
	void writeEmitsAJsonArray() {
		RegistryListSetting setting = dimensions();
		JsonArray expected = new JsonArray();
		expected.add("minecraft:overworld");

		assertEquals(expected, setting.write());
	}

	@Test
	void readSkipsMalformedEntriesButKeepsTheRest() {
		RegistryListSetting setting = dimensions();
		JsonArray stored = new JsonArray();
		stored.add("minecraft:the_end");
		stored.add("bozuk");
		stored.add(new JsonPrimitive(7));

		setting.read(stored);

		assertEquals(List.of("minecraft:the_end"), setting.get());
	}

	@Test
	void readFallsBackToTheDefaultOnANonArray() {
		RegistryListSetting setting = dimensions();
		setting.remove("minecraft:overworld");

		setting.read(new JsonPrimitive("hepsi"));

		assertEquals(List.of("minecraft:overworld"), setting.get());
	}

	@Test
	void anIdThisBuildHasNeverHeardOfSurvivesASaveAndLoad() {
		RegistryListSetting setting = items();
		setting.add(UNKNOWN_ID);
		setting.add("minecraft:bread");

		RegistryListSetting reloaded = items();
		reloaded.read(setting.write());

		assertEquals(List.of(UNKNOWN_ID, "minecraft:bread"), reloaded.get());
	}

	@Test
	void anUnknownIdKeepsItsPlaceInTheOrder() {
		RegistryListSetting setting = items();
		setting.add("minecraft:bread");
		setting.add(UNKNOWN_ID);
		setting.add("minecraft:apple");

		RegistryListSetting reloaded = items();
		reloaded.read(setting.write());

		assertEquals(1, reloaded.get().indexOf(UNKNOWN_ID));
	}

	@Test
	void aRoundTripThroughJsonTextChangesNothing() {
		RegistryListSetting setting = items();
		setting.add(UNKNOWN_ID);

		RegistryListSetting reloaded = items();
		reloaded.read(JsonParser.parseString(setting.write().toString()));

		assertEquals(setting.get(), reloaded.get());
	}

	@Test
	void resetRestoresTheDefaultList() {
		RegistryListSetting setting = dimensions();
		setting.add("minecraft:the_nether");
		setting.remove("minecraft:overworld");

		setting.reset();

		assertEquals(List.of("minecraft:overworld"), setting.get());
	}

	@Test
	void resetAfterAnUnknownIdLeavesNoTrace() {
		RegistryListSetting setting = items();
		setting.add(UNKNOWN_ID);

		setting.reset();

		assertEquals(List.of(), setting.get());
		assertFalse(setting.contains(UNKNOWN_ID));
	}

	@Test
	void theDefaultListSurvivesAResetAfterMutation() {
		RegistryListSetting setting = dimensions();
		setting.add("minecraft:the_end");
		setting.reset();
		setting.add("minecraft:the_nether");

		setting.reset();

		assertEquals(List.of("minecraft:overworld"), setting.get());
	}

	// --- reordering, which the menu drives by dragging ---

	private static RegistryListSetting threeFoods() {
		RegistryListSetting setting = items();
		setting.add("minecraft:bread");
		setting.add("minecraft:cooked_beef");
		setting.add("minecraft:golden_carrot");
		return setting;
	}

	@Test
	void movingAnEntryUpShiftsTheRestDown() {
		RegistryListSetting setting = threeFoods();

		setting.move(2, 0);

		assertEquals(List.of("minecraft:golden_carrot", "minecraft:bread", "minecraft:cooked_beef"), setting.get());
	}

	@Test
	void movingAnEntryDownShiftsTheRestUp() {
		RegistryListSetting setting = threeFoods();

		setting.move(0, 2);

		assertEquals(List.of("minecraft:cooked_beef", "minecraft:golden_carrot", "minecraft:bread"), setting.get());
	}

	@Test
	void movingSomethingOntoItselfChangesNothing() {
		RegistryListSetting setting = threeFoods();

		setting.move(1, 1);

		assertEquals(List.of("minecraft:bread", "minecraft:cooked_beef", "minecraft:golden_carrot"), setting.get());
	}

	/** A dragging finger overshoots; the end of the list is what it means. */
	@Test
	void droppingPastTheEndLandsAtTheEnd() {
		RegistryListSetting setting = threeFoods();

		setting.move(0, 99);

		assertEquals(List.of("minecraft:cooked_beef", "minecraft:golden_carrot", "minecraft:bread"), setting.get());
	}

	@Test
	void droppingAboveTheTopLandsAtTheTop() {
		RegistryListSetting setting = threeFoods();

		setting.move(2, -5);

		assertEquals(List.of("minecraft:golden_carrot", "minecraft:bread", "minecraft:cooked_beef"), setting.get());
	}

	@Test
	void movingWithinAnEmptyListDoesNothing() {
		RegistryListSetting setting = items();

		setting.move(0, 1);

		assertEquals(List.of(), setting.get());
	}

	// --- what the picker is told to offer ---

	@Test
	void aListSaysWhichRegistryItMeans() {
		assertEquals(RegistryListSetting.Kind.ITEM, items().kind());
		assertEquals(RegistryListSetting.Kind.FOOD,
				new RegistryListSetting("a", "test.a", List.of(), RegistryListSetting.Kind.FOOD).kind());
	}
}
