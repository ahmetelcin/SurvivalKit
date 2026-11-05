package com.merci.survivalkit.wdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class DoubleChestSplitterTest {

	@Test
	void clickedRightHalfAssignsClickedAsRightAndConnectedAsLeft() {
		DoubleChestSplitter.SplitPositions<String> positions =
				DoubleChestSplitter.resolvePositions("pos_A", "pos_B", true);

		assertEquals("pos_A", positions.rightPos());
		assertEquals("pos_B", positions.leftPos());
	}

	@Test
	void clickedLeftHalfAssignsConnectedAsRightAndClickedAsLeft() {
		DoubleChestSplitter.SplitPositions<String> positions =
				DoubleChestSplitter.resolvePositions("pos_A", "pos_B", false);

		assertEquals("pos_B", positions.rightPos());
		assertEquals("pos_A", positions.leftPos());
	}

	@Test
	void resolvePositionsRejectsNulls() {
		assertThrows(NullPointerException.class, () ->
				DoubleChestSplitter.resolvePositions(null, "pos_B", true));
		assertThrows(NullPointerException.class, () ->
				DoubleChestSplitter.resolvePositions("pos_A", null, false));
	}

	@Test
	void splitItemsSplits54ItemsIntoTwo27ItemHalves() {
		List<String> items = new ArrayList<>();
		for (int i = 0; i < 54; i++) {
			items.add("item_" + i);
		}

		DoubleChestSplitter.SplitResult<String> split = DoubleChestSplitter.splitItems(items);

		assertEquals(27, split.rightHalf().size());
		assertEquals(27, split.leftHalf().size());

		assertEquals("item_0", split.rightHalf().get(0));
		assertEquals("item_26", split.rightHalf().get(26));

		assertEquals("item_27", split.leftHalf().get(0));
		assertEquals("item_53", split.leftHalf().get(26));
	}

	@Test
	void splitItemsRejectsNullList() {
		assertThrows(NullPointerException.class, () -> DoubleChestSplitter.splitItems(null));
	}

	@Test
	void splitItemsRejectsWrongSize() {
		List<String> tooFew = Collections.nCopies(27, "item");
		assertThrows(IllegalArgumentException.class, () -> DoubleChestSplitter.splitItems(tooFew));

		List<String> tooMany = Collections.nCopies(55, "item");
		assertThrows(IllegalArgumentException.class, () -> DoubleChestSplitter.splitItems(tooMany));

		List<String> empty = List.of();
		assertThrows(IllegalArgumentException.class, () -> DoubleChestSplitter.splitItems(empty));
	}

	@Test
	void splitResultListsAreUnmodifiable() {
		List<String> items = new ArrayList<>();
		for (int i = 0; i < 54; i++) {
			items.add("item_" + i);
		}

		DoubleChestSplitter.SplitResult<String> split = DoubleChestSplitter.splitItems(items);

		assertThrows(UnsupportedOperationException.class, () -> split.rightHalf().add("extra"));
		assertThrows(UnsupportedOperationException.class, () -> split.leftHalf().add("extra"));
	}
}
