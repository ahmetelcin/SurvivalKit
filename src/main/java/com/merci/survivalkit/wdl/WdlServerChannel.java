package com.merci.survivalkit.wdl;

import com.merci.survivalkit.SurvivalKit;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ServerboundPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The old World Downloader mod's permission channels, so a server that forbids downloading can
 * say so and be listened to.
 *
 * <p>Servers run a companion plugin that listens on {@code wdl:init}; a client says hello there
 * when it joins, and the server answers on {@code wdl:control}. The hello is only sent to a
 * server that has said it listens on that channel. Nothing here hides anything from a server:
 * the control channel is registered like any other, and the game announces it on joining.
 *
 * <p>The pre-1.13 names ({@code WDL|INIT}, {@code WDL|CONTROL}) are not valid channel names any
 * more, so only the current ones are spoken.
 */
public final class WdlServerChannel {
	private static final String UPDATE_NOTE = "SurvivalKit World Downloader";

	/** What the server sends: one control packet, left as bytes for {@link WdlControlPacket}. */
	public record Control(byte[] bytes) implements CustomPacketPayload {
		public static final Type<Control> TYPE = new Type<>(Identifier.parse("wdl:control"));
		public static final StreamCodec<FriendlyByteBuf, Control> CODEC =
			CustomPacketPayload.codec((payload, buf) -> buf.writeBytes(payload.bytes()), buf -> new Control(rest(buf)));

		@Override
		public Type<Control> type() {
			return TYPE;
		}
	}

	/** The hello: a small JSON object, the way the old mod writes it. */
	public record Init(byte[] bytes) implements CustomPacketPayload {
		public static final Type<Init> TYPE = new Type<>(Identifier.parse("wdl:init"));
		public static final StreamCodec<FriendlyByteBuf, Init> CODEC =
			CustomPacketPayload.codec((payload, buf) -> buf.writeBytes(payload.bytes()), buf -> new Init(rest(buf)));

		@Override
		public Type<Init> type() {
			return TYPE;
		}
	}

	private WdlServerChannel() {
	}

	/**
	 * @param permission told every control packet, and reset for every new server
	 * @param onAnswer   run after each control packet, on the client thread
	 */
	public static void register(ServerPermission permission, Runnable onAnswer) {
		try {
			PayloadTypeRegistry.clientboundPlay().register(Control.TYPE, Control.CODEC);
			PayloadTypeRegistry.serverboundPlay().register(Init.TYPE, Init.CODEC);
		} catch (IllegalArgumentException alreadyTaken) {
			// Another mod speaking the same protocol got there first; it will be the one listening.
			SurvivalKit.LOGGER.warn("[wdl] wdl:control kanalı başka bir modda kayıtlı, sunucu izni dinlenemiyor", alreadyTaken);
			return;
		}

		ClientPlayNetworking.registerGlobalReceiver(Control.TYPE, (payload, context) -> {
			permission.accept(payload.bytes());
			onAnswer.run();
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			permission.reset();

			if (ClientPlayNetworking.canSend(Init.TYPE)) {
				sayHello();
			}
		});

		ServerboundPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> {
			if (channels.contains(Init.TYPE.id())) {
				sayHello();
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> permission.reset());
	}

	private static void sayHello() {
		String json = "{\"X-RTFM\":\"https://wiki.vg/Plugin_channels/World_downloader\",\"X-UpdateNote\":\"" + UPDATE_NOTE
			+ "\",\"Version\":\"" + SurvivalKit.MOD_ID + "\",\"State\":\"Init?\"}";
		ClientPlayNetworking.send(new Init(json.getBytes(StandardCharsets.UTF_8)));
	}

	private static byte[] rest(FriendlyByteBuf buf) {
		byte[] bytes = new byte[buf.readableBytes()];
		buf.readBytes(bytes);
		return bytes;
	}
}
