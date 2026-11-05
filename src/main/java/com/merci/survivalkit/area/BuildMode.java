package com.merci.survivalkit.area;

import com.merci.survivalkit.area.Corners.Corner;
import com.merci.survivalkit.text.Keys;
import com.merci.survivalkit.text.Lang;
import com.merci.survivalkit.vein.Pos;
import java.util.Objects;
import java.util.Optional;

/**
 * Yapı Modu's rules: the wand marks the corners, the corners are kept per place, and the box
 * between them is the area {@link AreaRule} holds the block-breaking modules to.
 *
 * <p>The module calls in here only while it is on. Off, the wand is an ordinary item, nothing is
 * drawn and {@link #area} is never asked — the module answers "no area" itself.
 */
public final class BuildMode {
	private final AreaStore store;

	public BuildMode(AreaStore store) {
		this.store = Objects.requireNonNull(store, "store");
	}

	/**
	 * A click on a block. With the wand in hand it marks a corner — the left click the first, the
	 * right click the second — and the click goes no further.
	 *
	 * @return whether the wand took the click; the game must then neither break nor place
	 */
	public boolean click(BuildContext client, Corner which) {
		if (!client.holdingWand()) {
			return false;
		}

		Optional<Pos> target = client.target();
		Optional<Place> place = client.place();

		if (target.isEmpty() || place.isEmpty()) {
			return false;
		}

		mark(client, place.get(), which, target.get());
		return true;
	}

	/**
	 * The attack key, asked every tick it is down. With the wand on a block the attack is the
	 * wand's — held as well as pressed, so holding it never starts to break the block — and only
	 * a fresh press marks the first corner.
	 *
	 * @return whether the attack must not happen
	 */
	public boolean attack(BuildContext client, boolean pressed) {
		if (!client.holdingWand() || client.target().isEmpty() || client.place().isEmpty()) {
			return false;
		}

		if (pressed) {
			click(client, Corner.FIRST);
		}

		return true;
	}

	/** The command's "here": the corner goes where the player stands. */
	public void markHere(BuildContext client, Corner which) {
		Optional<Place> place = client.place();
		Optional<Pos> feet = client.feet();

		if (place.isEmpty() || feet.isEmpty()) {
			client.say(Lang.of(Keys.BUILD_NOT_IN_WORLD));
			return;
		}

		mark(client, place.get(), which, feet.get());
	}

	/** Forgets both corners here; other places keep theirs. */
	public void clear(BuildContext client) {
		Optional<Place> place = client.place();

		if (place.isEmpty()) {
			client.say(Lang.of(Keys.BUILD_NOT_IN_WORLD));
			return;
		}

		boolean had = !store.get(place.get()).isEmpty();
		store.put(place.get(), Corners.NONE);
		client.say(Lang.of(had ? Keys.BUILD_CLEARED : Keys.BUILD_NO_AREA));
	}

	/**
	 * What is marked here, in chat.
	 *
	 * @param on whether Yapı Modu is on; off, the area is kept but holds nothing back, and it says so
	 */
	public void show(BuildContext client, boolean on) {
		Optional<Place> place = client.place();

		if (place.isEmpty()) {
			client.say(Lang.of(Keys.BUILD_NOT_IN_WORLD));
			return;
		}

		Corners corners = store.get(place.get());

		if (corners.isEmpty()) {
			client.say(Lang.of(Keys.BUILD_NO_AREA));
		} else {
			report(client, corners);
		}

		if (!on) {
			client.say(Lang.of(Keys.BUILD_OFF));
		}
	}

	/** One tick while the module is on: the corners here are drawn. */
	public void tick(BuildContext client) {
		client.place().ifPresent(place -> client.frame(store.get(place)));
	}

	/** The area marked in this place, once both corners are. */
	public Optional<Area> area(Optional<Place> place) {
		return place.flatMap(here -> store.get(here).area());
	}

	public Corners corners(Place place) {
		return store.get(place);
	}

	private void mark(BuildContext client, Place place, Corner which, Pos pos) {
		Corners before = store.get(place);
		Corners after = before.with(which, pos);

		// A held right click repeats every few ticks; the same corner again is not news.
		if (after.equals(before)) {
			return;
		}

		store.put(place, after);
		client.say(Lang.of(Keys.BUILD_CORNER, which.number(), text(pos)));
		after.area().ifPresent(area -> client.say(areaLine(after, area)));
	}

	private static void report(BuildContext client, Corners corners) {
		for (Corner which : Corner.values()) {
			if (corners.get(which) != null) {
				client.say(Lang.of(Keys.BUILD_CORNER, which.number(), text(corners.get(which))));
			}
		}

		corners.area().ifPresent(area -> client.say(areaLine(corners, area)));
	}

	private static String areaLine(Corners corners, Area area) {
		return Lang.of(Keys.BUILD_AREA, text(corners.first()), text(corners.second()),
			area.sizeX(), area.sizeY(), area.sizeZ(), area.volume());
	}

	static String text(Pos pos) {
		return pos.x() + ", " + pos.y() + ", " + pos.z();
	}
}
