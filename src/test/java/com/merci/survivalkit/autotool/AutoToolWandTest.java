package com.merci.survivalkit.autotool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.merci.survivalkit.autotool.FakeToolContext.Item;
import com.merci.survivalkit.hands.Hands;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Otomatik Alet leaves Yapı Modu's wand where it is, whatever the sword settings say. */
class AutoToolWandTest {
	private static final int WAND = 0;
	private static final int PICKAXE = 2;

	private final FakeToolSettings settings = new FakeToolSettings();
	private final AutoTool autoTool = new AutoTool(settings, new Hands());
	private final FakeToolContext client = new FakeToolContext();

	@BeforeEach
	void theWandInHandAndAPickaxeBesideIt() {
		// The two settings that would otherwise keep a sword in hand are off: only the wand rule is left.
		settings.keepSword = false;
		settings.onlyWithToolInHand = false;
		client.inventory.put(WAND, Item.sword("wand"));
		client.inventory.put(PICKAXE, Item.tool("pickaxe", 8));
		client.selected = WAND;
		client.reserved.add(WAND);
	}

	private void breakFor(int ticks) {
		client.startBreaking();

		for (int i = 0; i < ticks; i++) {
			autoTool.tick(client);
		}
	}

	@Test
	void breakingWithTheWandInHandKeepsTheWand() {
		breakFor(5);

		assertEquals("wand", client.held());
		assertEquals(List.of(), client.selections);
	}

	@Test
	void negativeControlWithoutTheReservationThePickaxeComesOut() {
		client.reserved.clear();
		breakFor(1);

		assertEquals("pickaxe", client.held());
	}

	@Test
	void aWandThatIsAToolIsNeverTakenOut() {
		client.inventory.put(WAND, Item.plain("bread"));
		client.reserved.clear();
		client.inventory.put(PICKAXE, Item.tool("wand pickaxe", 8));
		client.reserved.add(PICKAXE);
		breakFor(3);

		assertEquals("bread", client.held());
	}
}
