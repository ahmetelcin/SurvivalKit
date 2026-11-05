package com.merci.survivalkit.area;

import com.merci.survivalkit.vein.Pos;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Wires {@link BuildMode} to the running client: where the player is, what is in hand; draws. */
public final class ClientBuildContext implements BuildContext {
	/** Amber, fully opaque: the area's box. Apart from Damar Kırıcı's cyan and vanilla's black. */
	public static final int FRAME_COLOUR = 0xFFFFA318;
	/** Magenta: a marked corner, so a lone first corner shows before there is a box. */
	public static final int CORNER_COLOUR = 0xFFF04FD8;

	private final Minecraft minecraft;
	private final Predicate<ItemStack> wand;

	public ClientBuildContext(Minecraft minecraft, Predicate<ItemStack> wand) {
		this.minecraft = minecraft;
		this.wand = wand;
	}

	/** Who a server is, the same way Dünya İndirici names its folders: the address, or the save's folder. */
	@Override
	public Optional<Place> place() {
		if (minecraft.player == null || minecraft.level == null) {
			return Optional.empty();
		}

		String dimension = minecraft.level.dimension().identifier().toString();
		ServerData server = minecraft.getCurrentServer();

		if (server != null) {
			return Optional.of(new Place("mp:" + server.ip, dimension));
		}

		IntegratedServer local = minecraft.getSingleplayerServer();

		if (local == null) {
			return Optional.empty();
		}

		return Optional.of(new Place("sp:" + local.getWorldPath(LevelResource.ROOT).normalize().getFileName(), dimension));
	}

	@Override
	public boolean holdingWand() {
		return minecraft.player != null && wand.test(minecraft.player.getMainHandItem());
	}

	@Override
	public Optional<Pos> target() {
		if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			return Optional.of(pos(hit.getBlockPos()));
		}

		return Optional.empty();
	}

	@Override
	public Optional<Pos> feet() {
		return minecraft.player == null ? Optional.empty() : Optional.of(pos(minecraft.player.blockPosition()));
	}

	@Override
	public void say(String message) {
		minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
	}

	/**
	 * Vanilla's gizmos, like Damar Kırıcı's outline: called during the client tick, drawn until the
	 * next. The box a hair outside the blocks, the corners a hair inside it, so neither hides the other.
	 */
	@Override
	public void frame(Corners corners) {
		corners.area().ifPresent(area -> Gizmos.cuboid(
			new AABB(area.min().x(), area.min().y(), area.min().z(), area.max().x() + 1, area.max().y() + 1, area.max().z() + 1).inflate(0.01),
			GizmoStyle.stroke(FRAME_COLOUR, 2.5f)));

		for (Pos corner : new Pos[] {corners.first(), corners.second()}) {
			if (corner != null) {
				Gizmos.cuboid(new AABB(new BlockPos(corner.x(), corner.y(), corner.z())).inflate(0.004), GizmoStyle.stroke(CORNER_COLOUR, 2.0f));
			}
		}
	}

	private static Pos pos(BlockPos pos) {
		return new Pos(pos.getX(), pos.getY(), pos.getZ());
	}
}
