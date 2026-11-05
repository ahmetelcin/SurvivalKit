package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.area.ClientBuildContext;
import com.merci.survivalkit.module.impl.BuildModeModule;
import com.merci.survivalkit.settings.Setting;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.CuboidGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Yapı Modu driven the way a player drives it: the view turned onto a block, the real mouse
 * buttons, /bolge typed into the chat. Nothing here calls the module's own methods to mark a
 * corner.
 */
final class BuildTestSupport {
	private BuildTestSupport() {
	}

	static BuildModeModule module() {
		return (BuildModeModule) SurvivalKit.modules().byId("buildmode")
			.orElseThrow(() -> new AssertionError("buildmode modülü kayıtlı değil"));
	}

	static void enable(ClientGameTestContext context, boolean on) {
		context.runOnClient(client -> module().setEnabled(on));
		context.waitTicks(2);
	}

	@SuppressWarnings("unchecked")
	static void setWand(ClientGameTestContext context, List<String> items) {
		context.runOnClient(client -> module().settings().<Setting<List<String>>>byId("wand")
			.orElseThrow(() -> new AssertionError("değnek ayarı yok"))
			.set(items));
	}

	/**
	 * Types a command into the chat, as a player would: "/" opens the chat with the slash in it,
	 * the rest is typed, Enter sends it.
	 */
	static void type(ClientGameTestContext context, String command) {
		context.getInput().pressKey(options -> options.keyCommand);
		context.waitTicks(3);
		context.getInput().typeChars(command);
		context.waitTicks(2);
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(5);
	}

	/** Turns the view onto the block and checks the crosshair is on it, so a click lands there. */
	static void lookAt(ClientGameTestContext context, BlockPos pos) {
		context.getInput().lookAt(pos);
		context.waitTicks(3);
		BlockPos actual = context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit
			&& hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null);

		if (!pos.equals(actual)) {
			throw new AssertionError("hazırlık: nişangâh " + pos + " üstünde olmalıydı, olan: " + actual);
		}
	}

	/** Holds the left button on the block for this many ticks — long enough to break dirt by hand. */
	static void holdLeft(ClientGameTestContext context, BlockPos pos, int ticks) {
		lookAt(context, pos);
		context.getInput().holdMouseFor(InputConstants.MOUSE_BUTTON_LEFT, ticks);
		context.waitTicks(5);
	}

	static void rightClick(ClientGameTestContext context, BlockPos pos) {
		lookAt(context, pos);
		context.getInput().holdMouseFor(InputConstants.MOUSE_BUTTON_RIGHT, 2);
		context.waitTicks(5);
	}

	/** The area boxes the game is about to draw, in Yapı Modu's colour. */
	static Set<AABB> framesOnScreen(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.getPerTickGizmos().stream()
			.map(instance -> instance.gizmo())
			.filter(gizmo -> gizmo instanceof CuboidGizmo cuboid && cuboid.style().stroke() == ClientBuildContext.FRAME_COLOUR)
			.map(gizmo -> ((CuboidGizmo) gizmo).aabb())
			.collect(Collectors.toSet()));
	}

	/** The box Yapı Modu draws for an area between these corners. */
	static AABB frameOf(BlockPos a, BlockPos b) {
		return new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
			Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1).inflate(0.01);
	}

	/** Whether the frames hold this box, to a float's give. */
	static boolean framed(Set<AABB> frames, AABB expected) {
		return frames.stream().anyMatch(box -> Math.abs(box.minX - expected.minX) < 1e-6 && Math.abs(box.minY - expected.minY) < 1e-6
			&& Math.abs(box.minZ - expected.minZ) < 1e-6 && Math.abs(box.maxX - expected.maxX) < 1e-6
			&& Math.abs(box.maxY - expected.maxY) < 1e-6 && Math.abs(box.maxZ - expected.maxZ) < 1e-6);
	}
}
