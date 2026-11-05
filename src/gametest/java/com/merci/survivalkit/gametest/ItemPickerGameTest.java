package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.menu.ItemCatalogue;
import com.merci.survivalkit.menu.ItemChoice;
import com.merci.survivalkit.menu.ItemPickerScreen;
import com.merci.survivalkit.menu.SurvivalKitScreen;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.RegistryListSetting;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The list editor and its picker, driven through real clicks and real typing: three foods
 * added, dragged into a new order, and then actually eaten in that order by the module.
 */
public class ItemPickerGameTest implements FabricClientGameTest {
	private static final InputConstants.Key MENU_KEY =
		InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_RSHIFT);
	private static final InputConstants.Key ESCAPE = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_ESCAPE);

	/** What the picker is allowed to take to open with the whole registry behind it. */
	private static final long OPEN_BUDGET_MS = 100;

	private static final int HUNGRY = 12;

	/** An id from a mod that is no longer installed, which the list has to keep and mark. */
	private static final String GONE_MOD_ID = "eskimod:gizli_yemek";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestLanguage.ensureTurkish(context);
			context.waitTicks(5);
			world.getServer().runCommand("gamemode survival @a");

			priority().set(List.of());
			openMenuOn(context, "autoeat");

			Opening opening = thePickerOpensQuickly(context);
			onlyFoodsAreOffered(context);
			theTurkishNameFindsTheItem(context);
			threeFoodsAreAddedWithoutReopening(context);
			addedEntriesAreShownAsPicked(context);
			closePicker(context);
			context.takeScreenshot("menu-liste-editoru");

			theListIsReorderedByDragging(context);
			theModuleEatsInTheNewOrder(context, world);
			anUnknownIdIsGreyedAndLabelled(context);

			priority().set(List.of());
			context.getInput().pressKey(MENU_KEY);
			context.waitTicks(5);

			SurvivalKit.LOGGER.info("[gametest] liste editörü doğrulandı (seçici {} ms'de açıldı, katalogda {} item — gametest'te yalnız vanilla kayıt var, asıl ölçüm profilde; sadece yiyecek sunuldu, listedekiler soluk, 3 yiyecek eklendi, sürüklenip sıralandı, yeni sırayla yendi, bilinmeyen id gri ve '{}' etiketli)",
					opening.ms(), opening.offered(), context.computeOnClient(client -> SurvivalKitScreen.missingMark()));
		}
	}

	/** What one opening cost: how long it took, and how much it had to offer. */
	private record Opening(long ms, int offered) {
	}

	/** Opening builds the catalogue from the registry; that is what is being timed. */
	private static Opening thePickerOpensQuickly(ClientGameTestContext context) {
		MenuScroll.bringAddLineIntoView(context, "priority");
		ItemCatalogue.forget();

		long elapsed = context.computeOnClient(client -> {
			long started = System.nanoTime();
			clickAddLine(client);
			return (System.nanoTime() - started) / 1_000_000L;
		});

		context.waitTicks(3);

		if (picker(context) == null) {
			throw new AssertionError("+ Ekle seçiciyi açmalıydı");
		}

		int offered = context.computeOnClient(client -> picker(context).model().all().size());

		if (offered < 30) {
			throw new AssertionError("seçici tüm yiyecekleri sunmalıydı, bulunan " + offered);
		}

		if (elapsed > OPEN_BUDGET_MS) {
			throw new AssertionError("seçici " + OPEN_BUDGET_MS + " ms altında açılmalıydı, sürdü: " + elapsed + " ms");
		}

		return new Opening(elapsed, offered);
	}

	/**
	 * The reason the client was put into Turkish: a name typed without its diacritics still
	 * finds the item.
	 *
	 * <p>The example is the pork chop rather than the steak, because the game's Turkish for
	 * minecraft:cooked_beef is "Biftek" — the word "pişmiş" is not in it, the same way
	 * "cooked" is not in the English "Steak". The steak is checked below under the name the
	 * game actually gives it.
	 */
	private static void theTurkishNameFindsTheItem(ClientGameTestContext context) {
		type(context, "pismis");

		List<String> found = visibleIds(context);

		if (!found.contains("minecraft:cooked_porkchop")) {
			throw new AssertionError("\"pismis\" Pişmiş Domuz Pirzolası'nı bulmalıydı, bulunanlar: " + found);
		}

		if (!nameOf(context, "minecraft:cooked_porkchop").startsWith("Pişmiş")) {
			throw new AssertionError("istemci Türkçe olmalıydı, item adı: "
					+ nameOf(context, "minecraft:cooked_porkchop"));
		}

		type(context, "biftek");

		if (!visibleIds(context).contains("minecraft:cooked_beef")) {
			throw new AssertionError("\"biftek\" Biftek'i bulmalıydı, bulunanlar: " + visibleIds(context));
		}

		if (!nameOf(context, "minecraft:cooked_beef").equals("Biftek")) {
			throw new AssertionError("oyunun Türkçesinde cooked_beef \"Biftek\" olmalıydı, bulunan: "
					+ nameOf(context, "minecraft:cooked_beef"));
		}
	}

	private static List<String> visibleIds(ClientGameTestContext context) {
		return context.computeOnClient(client -> picker(context).model().visible().stream()
				.map(ItemChoice::id)
				.toList());
	}

	private static String nameOf(ClientGameTestContext context, String id) {
		return context.computeOnClient(client -> picker(context).model().all().stream()
				.filter(choice -> choice.id().equals(id))
				.map(ItemChoice::displayName)
				.findFirst()
				.orElseThrow(() -> new AssertionError("seçici bunu sunmalıydı: " + id)));
	}

	/** The picker stays open, so three entries take three clicks and no reopening. */
	private static void threeFoodsAreAddedWithoutReopening(ClientGameTestContext context) {
		addByName(context, "Ekmek", "minecraft:bread");
		addByName(context, "biftek", "minecraft:cooked_beef");
		addByName(context, "Altın Havuç", "minecraft:golden_carrot");

		if (picker(context) == null) {
			throw new AssertionError("seçici her eklemeden sonra açık kalmalıydı");
		}

		List<String> list = priority().get();

		if (!list.equals(List.of("minecraft:bread", "minecraft:cooked_beef", "minecraft:golden_carrot"))) {
			throw new AssertionError("üç yiyecek eklenme sırasıyla listeye girmeliydi, bulunan " + list);
		}
	}

	private static void addByName(ClientGameTestContext context, String search, String expectedId) {
		type(context, search);

		int index = context.computeOnClient(client -> {
			List<?> visible = picker(context).model().visible();

			for (int i = 0; i < visible.size(); i++) {
				if (picker(context).model().visible().get(i).id().equals(expectedId)) {
					return i;
				}
			}

			return -1;
		});

		if (index < 0) {
			throw new AssertionError("\"" + search + "\" araması " + expectedId + " getirmeliydi");
		}

		int x = context.computeOnClient(client -> picker(context).cellCentreX(index));
		int y = context.computeOnClient(client -> picker(context).cellCentreY(index));
		clickAt(context, x, y);

		if (!priority().contains(expectedId)) {
			throw new AssertionError(expectedId + " listeye eklenmeliydi");
		}
	}

	/** Dragging the last entry to the top has to rewrite the order. */
	private static void theListIsReorderedByDragging(ClientGameTestContext context) {
		int carrotIndex = priority().get().indexOf("minecraft:golden_carrot");
		scrollEntriesIntoView(context, carrotIndex);
		int from = entryY(context, carrotIndex);
		int to = entryY(context, 0);
		int x = context.computeOnClient(client -> screen(context).listEntryX("priority"));

		dragFromTo(context, x, from, to);

		List<String> list = priority().get();

		if (!"minecraft:golden_carrot".equals(list.getFirst())) {
			throw new AssertionError("sürüklenen girdi en üste gelmeliydi, bulunan " + list);
		}
	}

	/** The point of all of it: the module obeys the order the list now has. */
	private static void theModuleEatsInTheNewOrder(ClientGameTestContext context, TestSingleplayerContext world) {
		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);

		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("give @a minecraft:bread 3");
		world.getServer().runCommand("give @a minecraft:golden_carrot 3");
		world.getServer().runOnServer(server -> player(server).getFoodData().setFoodLevel(HUNGRY));
		context.waitTicks(3);

		setEnabled(context, true);
		world.getServer().waitFor(server -> hunger(server) > HUNGRY, 200);
		setEnabled(context, false);

		int carrots = count(world, Items.GOLDEN_CARROT);
		int bread = count(world, Items.BREAD);

		if (carrots != 2 || bread != 3) {
			throw new AssertionError("listenin başındaki altın havuç yenmeliydi, ekmek durmalıydı; havuç "
					+ carrots + ", ekmek " + bread);
		}
	}

	/** The AutoEat list is a food list, so the picker must offer food and nothing else. */
	private static void onlyFoodsAreOffered(ClientGameTestContext context) {
		List<String> notFood = context.computeOnClient(client -> picker(context).model().all().stream()
				.map(ItemChoice::id)
				.filter(id -> !isEdible(id))
				.limit(5)
				.toList());

		if (!notFood.isEmpty()) {
			throw new AssertionError("yiyecek listesinde yenmeyenler sunuldu: " + notFood);
		}

		List<String> offered = context.computeOnClient(client -> picker(context).model().all().stream()
				.map(ItemChoice::id)
				.toList());

		if (offered.contains("minecraft:stone")) {
			throw new AssertionError("taş yiyecek listesinde sunulmamalıydı");
		}

		if (!offered.contains("minecraft:bread")) {
			throw new AssertionError("ekmek yiyecek listesinde sunulmalıydı");
		}
	}

	private static boolean isEdible(String id) {
		return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id))).get(DataComponents.FOOD) != null;
	}

	/** What is on the list is drawn faded, and this is the state the fading reads. */
	private static void addedEntriesAreShownAsPicked(ClientGameTestContext context) {
		List<String> wrong = context.computeOnClient(client -> picker(context).model().all().stream()
				.filter(choice -> picker(context).model().isPicked(choice) != priority().contains(choice.id()))
				.map(ItemChoice::id)
				.toList());

		if (!wrong.isEmpty()) {
			throw new AssertionError("listedekiler soluk, geri kalanı normal gösterilmeliydi; uymayanlar: " + wrong);
		}
	}

	/**
	 * A list keeps an id whose mod is gone, so the row has to say it is dead rather than
	 * quietly show something else.
	 */
	private static void anUnknownIdIsGreyedAndLabelled(ClientGameTestContext context) {
		priority().set(List.of("minecraft:bread", GONE_MOD_ID));
		openMenuOn(context, "autoeat");

		if (entryY(context, 1) <= 0) {
			throw new AssertionError("bilinmeyen id de bir satır olarak çizilmeliydi");
		}

		boolean missing = context.computeOnClient(client -> SurvivalKitScreen.isMissing(GONE_MOD_ID));
		String label = context.computeOnClient(client -> SurvivalKitScreen.entryLabel(GONE_MOD_ID));
		int colour = context.computeOnClient(client -> SurvivalKitScreen.entryColor(GONE_MOD_ID));
		boolean breadMissing = context.computeOnClient(client -> SurvivalKitScreen.isMissing("minecraft:bread"));
		int breadColour = context.computeOnClient(client -> SurvivalKitScreen.entryColor("minecraft:bread"));

		if (!missing || breadMissing) {
			throw new AssertionError("yalnızca bilinmeyen id eksik sayılmalıydı");
		}

		if (!label.equals(GONE_MOD_ID)) {
			throw new AssertionError("bilinmeyen id ham halıyle yazılmalıydı, yazılan: " + label);
		}

		if (colour == breadColour) {
			throw new AssertionError("bilinmeyen id gri, bilinen normal renkte olmalıydı");
		}

		String mark = context.computeOnClient(client -> SurvivalKitScreen.missingMark());

		if (!"eksik".equals(mark)) {
			throw new AssertionError("etiket 'eksik' olmalıydı, bulunan: " + mark);
		}

		context.takeScreenshot("menu-eksik-id");
	}
	// --- plumbing ---

	private static void type(ClientGameTestContext context, String text) {
		context.runOnClient(client -> picker(context).searchBox().setValue(""));
		context.getInput().typeChars(text);
		context.waitTicks(3);
	}

	/** The box still holds the last search, and Escape undoes that before the screen. */
	private static void closePicker(ClientGameTestContext context) {
		context.getInput().pressKey(ESCAPE);
		context.waitTicks(3);

		if (picker(context) == null) {
			throw new AssertionError("ilk Esc yalnızca aramayı silmeliydi");
		}

		context.getInput().pressKey(ESCAPE);
		context.waitTicks(5);

		if (screen(context) == null) {
			throw new AssertionError("ikinci Esc menüye dönmeliydi");
		}
	}

	private static void clickAddLine(net.minecraft.client.Minecraft client) {
		SurvivalKitScreen menu = (SurvivalKitScreen) client.gui.screen();
		int y = menu.addLineY("priority").orElseThrow(() -> new AssertionError("+ Ekle satırı ekranda değil"));
		menu.clickFor(menu.listEntryX("priority"), y);
	}

	private static int entryY(ClientGameTestContext context, int index) {
		return context.computeOnClient(client -> screen(context).listEntryY("priority", index)
				.orElseThrow(() -> new AssertionError("girdi ekranda değil: " + index)));
	}

	/**
	 * Wheels the panel until the first entry and the {@code last}th are both inside the visible
	 * band, where the panel takes clicks. The headings pushed the list far enough down that
	 * its lower entries start off the bottom of the default window.
	 */
	private static void scrollEntriesIntoView(ClientGameTestContext context, int last) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());

		for (int attempt = 0; attempt < 60; attempt++) {
			int top = context.computeOnClient(client -> screen(context).settingsViewTop());
			int bottom = context.computeOnClient(client -> screen(context).settingsViewBottom());
			int firstY = entryY(context, 0);
			int lastY = entryY(context, last);

			if (firstY >= top && lastY <= bottom) {
				return;
			}

			int cursorX = context.computeOnClient(client -> screen(context).listEntryX("priority"));
			context.getInput().setCursorPos(cursorX * scale, (top + bottom) / 2.0 * scale);
			context.getInput().scroll(lastY > bottom ? -1 : 1);
			context.waitTick();
		}

		throw new AssertionError("girdiler birlikte görünür alana kaydırılamadı");
	}

	private static void dragFromTo(ClientGameTestContext context, int x, int fromY, int toY) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());

		context.getInput().setCursorPos(x * scale, fromY * scale);
		context.waitTick();
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
		context.getInput().setCursorPos(x * scale, toY * scale);
		context.waitTicks(2);
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static void clickAt(ClientGameTestContext context, int guiX, int guiY) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());

		context.getInput().setCursorPos(guiX * scale, guiY * scale);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static void openMenuOn(ClientGameTestContext context, String moduleId) {
		context.getInput().pressKey(MENU_KEY);
		context.waitTicks(5);

		Module module = module(moduleId);
		int y = screen(context).rowCenterY(module).orElseThrow(() -> new AssertionError("modül listede yok"));
		clickAt(context, screen(context).nameCenterX(), y);
	}

	private static void setEnabled(ClientGameTestContext context, boolean enabled) {
		context.runOnClient(client -> module("autoeat").setEnabled(enabled));
	}

	private static SurvivalKitScreen screen(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.gui.screen() instanceof SurvivalKitScreen open ? open : null);
	}

	private static ItemPickerScreen picker(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.gui.screen() instanceof ItemPickerScreen open ? open : null);
	}

	private static RegistryListSetting priority() {
		return module("autoeat").settings().<RegistryListSetting>byId("priority").orElseThrow();
	}

	private static Module module(String id) {
		return SurvivalKit.modules().byId(id).orElseThrow(() -> new AssertionError("modül kayıtlı değil: " + id));
	}

	private static int hunger(MinecraftServer server) {
		return player(server).getFoodData().getFoodLevel();
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static int count(TestSingleplayerContext world, Item item) {
		return world.getServer().computeOnServer(server -> player(server).getInventory()
				.getNonEquipmentItems()
				.stream()
				.filter(stack -> stack.is(item))
				.mapToInt(ItemStack::getCount)
				.sum());
	}
}
