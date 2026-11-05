package com.merci.survivalkit.area;

import com.merci.survivalkit.vein.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class FakeBuildContext implements BuildContext {
	Place place = new Place("sp:Test", "minecraft:overworld");
	boolean wand = true;
	Pos target = new Pos(10, 64, 10);
	Pos feet = new Pos(0, 64, 0);
	final List<String> said = new ArrayList<>();
	Corners framed;

	@Override
	public Optional<Place> place() {
		return Optional.ofNullable(place);
	}

	@Override
	public boolean holdingWand() {
		return wand;
	}

	@Override
	public Optional<Pos> target() {
		return Optional.ofNullable(target);
	}

	@Override
	public Optional<Pos> feet() {
		return place == null ? Optional.empty() : Optional.of(feet);
	}

	@Override
	public void say(String message) {
		said.add(message);
	}

	@Override
	public void frame(Corners corners) {
		framed = corners;
	}
}
