package com.merci.survivalkit.autoeat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.autototem.AutoTotem;
import com.merci.survivalkit.autototem.TotemContext;
import com.merci.survivalkit.autototem.TotemMode;
import com.merci.survivalkit.autototem.TotemSettings;
import com.merci.survivalkit.hands.Hands;
import com.merci.survivalkit.util.FoodSlot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The two modules in the same game, with the real hand lock between them: a meal in the off
 * hand and health falling to the point where that hand is needed for a totem.
 *
 * <p>Both modules look at one off hand here rather than two fakes' worth. That is the whole
 * point — the collision is real, so what one puts there the other has to see.
 */
class AutoEatUnderAutoTotemTest {
	private static final int HUNGRY = 14;
	private static final FoodSlot BREAD = new FoodSlot(3, "minecraft:bread", 5, 6.0f, false);
	private static final int SWORD_SLOT = 4;
	private static final int TOTEM_SLOT = 7;
	private static final int THRESHOLD = 10;

	/** One off hand, one health bar, two modules. */
	private final class SharedWorld implements TotemContext {
		final Map<Integer, String> inventory = new LinkedHashMap<>();
		final List<Integer> offhandSwaps = new ArrayList<>();
		final List<String> said = new ArrayList<>();

		@Override
		public boolean ready() {
			return client.ready;
		}

		@Override
		public float health() {
			return client.health;
		}

		@Override
		public String offhandId() {
			return client.offhandId;
		}

		@Override
		public OptionalInt slotOf(String id) {
			return inventory.entrySet().stream()
				.filter(entry -> entry.getValue().equals(id))
				.mapToInt(Map.Entry::getKey)
				.min();
		}

		@Override
		public int count(String id) {
			return (int) inventory.values().stream().filter(id::equals).count();
		}

		@Override
		public void say(String message) {
			said.add(message);
		}

		@Override
		public void swapToOffhand(int inventorySlot) {
			offhandSwaps.add(inventorySlot);

			String there = inventory.getOrDefault(inventorySlot, "");

			if (client.offhandId.isEmpty()) {
				inventory.remove(inventorySlot);
			} else {
				inventory.put(inventorySlot, client.offhandId);
			}

			client.offhandId = there;
		}
	}

	private static final class TotemSettingsInHealthMode implements TotemSettings {
		@Override
		public List<String> fallback() {
			return List.of();
		}

		@Override
		public boolean warnWhenOut() {
			return true;
		}

		@Override
		public TotemMode mode() {
			return TotemMode.HEALTH_THRESHOLD;
		}

		@Override
		public int healthThreshold() {
			return THRESHOLD;
		}

		@Override
		public boolean restoreOffhand() {
			return true;
		}
	}

	private FakeEatSettings eatSettings;
	private FakeEatContext client;
	private Hands hands;
	private AutoEat autoEat;
	private AutoTotem autoTotem;
	private SharedWorld world;

	@BeforeEach
	void setUp() {
		hands = new Hands();

		eatSettings = new FakeEatSettings();
		eatSettings.eatingHand = EatingHand.OFF_HAND;
		autoEat = new AutoEat(eatSettings, hands);

		client = new FakeEatContext();
		client.selectedSlot = SWORD_SLOT;
		client.foodLevel = HUNGRY;
		client.food = List.of(BREAD);

		autoTotem = new AutoTotem(new TotemSettingsInHealthMode(), hands);
		world = new SharedWorld();
		world.inventory.put(TOTEM_SLOT, AutoTotem.TOTEM);
	}

	/** Eats until the bite has actually started, so the interruption has something to hit. */
	private void eatInTheOffHand() {
		for (int tick = 1; tick <= 10 && !client.usingItem; tick++) {
			autoEat.tick(client);
		}

		assertTrue(client.usingItem, "hazırlık: diğer elde yemek başlamalıydı");
		assertEquals(BREAD.id(), client.offhandId, "hazırlık: ekmek diğer elde olmalıydı");
	}

	@Test
	void theMealIsDroppedWhenHealthFallsToWhereATotemIsWanted() {
		eatInTheOffHand();

		client.health = 6.0f;
		autoTotem.tick(world);

		assertFalse(hands.holds(autoEat), "AutoEat eli bırakmalıydı");
		assertFalse(client.useKeyHeld, "kullanma tuşu bırakılmalıydı");
	}

	@Test
	void theBreadGoesBackToItsOwnSlot() {
		eatInTheOffHand();

		client.health = 6.0f;
		autoTotem.tick(world);

		assertEquals(List.of(BREAD.slot(), BREAD.slot()), client.offhandSwaps,
			"ekmek diğer elden geldiği slota dönmeliydi");
		assertTrue(client.food.contains(BREAD), "ekmek yine yenebilir durumda olmalıydı");
	}

	@Test
	void theTotemTakesTheHandTheMealWasUsing() {
		eatInTheOffHand();

		client.health = 6.0f;
		autoTotem.tick(world);

		assertEquals(AutoTotem.TOTEM, client.offhandId);
		assertEquals(List.of(TOTEM_SLOT), world.offhandSwaps);
	}

	/**
	 * Negative control: while health is fine the totem is not wanted, so the meal is left
	 * alone. Without this the test above could be passing on a module that interrupts always.
	 */
	@Test
	void aboveTheThresholdTheMealIsLeftAlone() {
		eatInTheOffHand();

		client.health = 20.0f;
		autoTotem.tick(world);

		assertTrue(hands.holds(autoEat), "can iyiyken yemek kesilmemeliydi");
		assertEquals(BREAD.id(), client.offhandId);
		assertEquals(List.of(), world.offhandSwaps);
	}

	/**
	 * Interrupted is not switched off. The next attempt goes to the main hand, because
	 * AutoEat's own rule is that a totem in the off hand is not something to move.
	 */
	@Test
	void theNextMealGoesToTheMainHandRatherThanMovingTheTotem() {
		eatInTheOffHand();
		client.health = 6.0f;
		autoTotem.tick(world);
		autoTotem.tick(world);
		client.slotSelections.clear();
		client.offhandSwaps.clear();
		world.offhandSwaps.clear();

		autoEat.tick(client);

		assertTrue(client.useKeyHeld, "ana elde yemeye başlamalıydı");
		assertEquals(List.of(BREAD.slot()), client.slotSelections, "ekmek ana ele alınmalıydı");
		assertEquals(List.of(), client.offhandSwaps, "totem yerinden oynamamalıydı");
		assertEquals(List.of(), world.offhandSwaps, "totem yerinden oynamamalıydı");
	}
}
