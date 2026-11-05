package com.merci.survivalkit.module.impl;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.hud.ActionBarEcho;
import com.merci.survivalkit.hud.ClientActionBar;
import com.merci.survivalkit.hud.HudLine;
import com.merci.survivalkit.hud.HudSource;
import com.merci.survivalkit.hud.StatusLines;
import com.merci.survivalkit.module.Category;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.settings.BoolSetting;
import com.merci.survivalkit.settings.EnumSetting;
import com.merci.survivalkit.settings.IntSetting;
import com.merci.survivalkit.settings.KeybindSetting;
import com.merci.survivalkit.settings.TextSetting;
import com.merci.survivalkit.wdl.AutoSeedQuerier;
import com.merci.survivalkit.wdl.ContainerCapture;
import com.merci.survivalkit.wdl.DownloadSession;
import net.minecraft.network.chat.Component;
import com.merci.survivalkit.wdl.WdlServerChannel;
import com.merci.survivalkit.wdl.ServerPermission;
import com.merci.survivalkit.wdl.EntityInventoryCapture;
import com.merci.survivalkit.wdl.FlushTicker;
import com.merci.survivalkit.wdl.OwnershipMode;
import com.merci.survivalkit.wdl.SaveName;
import com.merci.survivalkit.wdl.Seed;
import com.merci.survivalkit.wdl.VillagerTradeCapture;
import com.merci.survivalkit.wdl.WdlCommands;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Saves the chunks the client is shown into a world folder you can open in singleplayer.
 *
 * <p>Chunks are written as the game drops them, which is also what happens while the world
 * is torn down on disconnect, so leaving a server flushes the last ring by itself.
 */
public final class WorldDownloaderModule extends Module implements HudSource {
	private final BoolSetting continueWorld = settings()
		.add(new BoolSetting("continue_world", "wdl.continue_world", true));

	private final TextSetting seed = settings()
		.add(new TextSetting("seed", "wdl.seed", "", 32));

	private final BoolSetting autoQuerySeed = settings()
		.add(new BoolSetting("auto_query_seed", "wdl.auto_query_seed", true));

	private final EnumSetting<OwnershipMode> ownership = settings()
		.add(new EnumSetting<>("ownership", "wdl.ownership", OwnershipMode.AS_IS));

	private final IntSetting flushInterval = settings()
		.add(new IntSetting("flush_interval", "wdl.flush_interval", 30, 1, 300));

	private final BoolSetting savePlayerState = settings()
		.add(new BoolSetting("save_player_state", "wdl.save_player_state", true));

	private final BoolSetting actionBar = settings()
		.add(new BoolSetting("action_bar", "common.action_bar", false));

	private final ActionBarEcho echo = new ActionBarEcho();

	private final FlushTicker flushTicker = new FlushTicker(flushInterval::get);
	private static final int MAP_LOOK_TICKS = 20;
	private int mapLookTicks;
	private final ContainerCapture containerCapture;
	private final VillagerTradeCapture tradeCapture;
	private final EntityInventoryCapture inventoryCapture;
	private final ServerPermission serverPermission = new ServerPermission();
	private final AutoSeedQuerier seedQuerier;
	private DownloadSession session;
	private Path lastSavePath;
	private int lastSavedChunks;
	/** A download stopped in the world the player is in now: the HUD says so until the next world. */
	private boolean stoppedHere;

	public WorldDownloaderModule() {
		super("wdl", Category.MISC, KeybindSetting.UNBOUND);

		this.containerCapture = new ContainerCapture(this);
		this.tradeCapture = new VillagerTradeCapture(this);
		this.inventoryCapture = new EntityInventoryCapture(this);
		WdlServerChannel.register(serverPermission, this::onServerAnswer);
		this.seedQuerier = new AutoSeedQuerier(this);
		WdlCommands.register(this);

		ClientChunkEvents.CHUNK_UNLOAD.register(this::onChunkUnload);
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onLevelChange);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop());
		// What was downloaded in another world is no news in this one: the HUD forgets it.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> stoppedHere = false);
	}

	public void setSeed(String seedValue) {
		seed.set(seedValue);
		if (session != null) {
			session.setSeed(Seed.parse(seedValue));
		}
	}

	public AutoSeedQuerier seedQuerier() {
		return seedQuerier;
	}

	public VillagerTradeCapture tradeCapture() {
		return tradeCapture;
	}

	public int lastSavedChunks() {
		return session != null ? session.savedChunks() : lastSavedChunks;
	}

	public IntSetting flushInterval() {
		return flushInterval;
	}

	public BoolSetting autoQuerySeed() {
		return autoQuerySeed;
	}

	public EnumSetting<OwnershipMode> ownership() {
		return ownership;
	}

	public BoolSetting continueWorld() {
		return continueWorld;
	}

	public BoolSetting savePlayerState() {
		return savePlayerState;
	}

	/** The currently active download session, if downloading is on. */
	public Optional<DownloadSession> session() {
		return Optional.ofNullable(session);
	}

	/** The folder the most recent download went to, once it has been closed. */
	public Optional<Path> lastSavePath() {
		return Optional.ofNullable(lastSavePath);
	}

	@Override
	protected void onEnable() {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft == null || minecraft.level == null) {
			SurvivalKit.LOGGER.warn("[wdl] bir dünyaya bağlı değilken başlatılamaz");
			setEnabled(false);
			return;
		}

		// The server's word goes: a refusal means no download starts, and the player is told why.
		if (!serverPermission.allowed()) {
			refuse(minecraft);
			setEnabled(false);
			return;
		}

		// Continuing, every download from this server lands in one folder: chunks seen again
		// are written over, the ones not seen this time stay as they were.
		String name = continueWorld.get()
			? SaveName.forServer(address(minecraft))
			: SaveName.of(address(minecraft), LocalDateTime.now());

		try {
			session = DownloadSession.open(minecraft.getLevelSource(), name, name);
		} catch (IOException e) {
			SurvivalKit.LOGGER.error("[wdl] kayıt klasörü açılamadı: {}", name, e);
			setEnabled(false);
			return;
		}

		lastSavePath = session.levelPath();
		flushTicker.reset();
		session.dimensionChanged(minecraft.level.dimension());
		session.setSavePlayerState(savePlayerState.get());
		applyOwnership();
		applySeed();
		saveLoadedChunks(minecraft, minecraft.level);
		SurvivalKit.LOGGER.info("[wdl] indirme başladı -> saves/{}", name);
	}

	@Override
	protected void onDisable() {
		stop();
	}

	@Override
	protected void onTick() {
		if (actionBar.get()) {
			echo.next(hudLines()).ifPresent(ClientActionBar::say);
		} else {
			echo.reset();
		}

		if (session == null || !isEnabled()) {
			return;
		}

		session.setSavePlayerState(savePlayerState.get());
		applyOwnership();

		// A second is often enough to catch a map passing through the hand.
		if (++mapLookTicks >= MAP_LOOK_TICKS) {
			mapLookTicks = 0;
			lookAtMaps(Minecraft.getInstance());
		}

		if (flushTicker.tick()) {
			flush();
		}
	}

	/**
	 * Downloading, with the chunks saved so far; after a download, that it stopped and what it
	 * saved, until the player is in another world; refused by this server, that — even switched
	 * off, since that is why it is off. Nothing before the first download here.
	 */
	@Override
	public List<HudLine> hudLines() {
		if (!serverPermission.allowed()) {
			return StatusLines.downloader(true, false, 0);
		}

		if (session != null) {
			return StatusLines.downloader(false, true, session.savedChunks());
		}

		return stoppedHere ? StatusLines.downloader(false, false, lastSavedChunks) : List.of();
	}

	/** A refusal that arrives mid-download ends it, the way the old World Downloader did. */
	private void onServerAnswer() {
		if (isEnabled() && !serverPermission.allowed()) {
			refuse(Minecraft.getInstance());
			setEnabled(false);
		}
	}

	private static void refuse(Minecraft minecraft) {
		SurvivalKit.LOGGER.info("[wdl] sunucu dünya indirmeye izin vermiyor, indirme yapılmadı");

		if (minecraft.player != null) {
			minecraft.player.sendSystemMessage(Component.translatable("survivalkit.wdl.forbidden"));
		}
	}

	public ServerPermission serverPermission() {
		return serverPermission;
	}

	/** Flushes loaded chunks and all open dimension writers to disk. */
	public void flush() {
		if (session == null) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			saveLoadedChunks(minecraft, minecraft.level);
		}

		if (savePlayerState.get() && minecraft.player != null) {
			session.savePlayer(minecraft.player);
		}

		lookAtMaps(minecraft);
		session.flush();
	}

	private void onLevelChange(Minecraft client, ClientLevel newLevel) {
		if (session == null || !isEnabled() || newLevel == null) {
			return;
		}

		session.dimensionChanged(newLevel.dimension());
		saveLoadedChunks(client, newLevel);
	}

	private void onChunkUnload(ClientLevel level, LevelChunk chunk) {
		if (session != null && isEnabled()) {
			session.saveChunk(level, chunk);
		}
	}

	/** Everything already on screen when the module is switched on, so nothing is missed. */
	private void saveLoadedChunks(Minecraft minecraft, ClientLevel level) {
		if (minecraft.player == null) {
			return;
		}

		ChunkPos centre = minecraft.player.chunkPosition();
		int radius = Math.max(2, minecraft.options.renderDistance().get());

		for (int x = centre.x() - radius; x <= centre.x() + radius; x++) {
			for (int z = centre.z() - radius; z <= centre.z() + radius; z++) {
				LevelChunk chunk = level.getChunkSource().getChunk(x, z, false);

				if (chunk != null) {
					session.saveChunk(level, chunk);
				}
			}
		}
	}

	private void stop() {
		if (session == null) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			saveLoadedChunks(minecraft, minecraft.level);
		}

		if (savePlayerState.get() && minecraft.player != null) {
			session.savePlayer(minecraft.player);
		}

		lookAtMaps(minecraft);
		applySeed();
		session.flush();
		lastSavedChunks = session.savedChunks();
		stoppedHere = true;
		session.close();
		session = null;
		flushTicker.reset();
	}

	private void lookAtMaps(Minecraft minecraft) {
		if (session != null && minecraft.level != null && minecraft.player != null) {
			session.lookAtMaps(minecraft.level, minecraft.player);
		}
	}

	private void applySeed() {
		if (session == null || seed.isBlank()) {
			return;
		}

		session.setSeed(Seed.parse(seed.get()));
	}

	private void applyOwnership() {
		if (session == null) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		session.setOwnership(ownership.get(), minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null);
	}

	/**
	 * What names the download folder. In singleplayer there is no server, and "the same
	 * server" means the same world, so its folder name stands in for the address.
	 */
	private static String address(Minecraft minecraft) {
		ServerData server = minecraft.getCurrentServer();

		if (server != null) {
			return server.ip;
		}

		var local = minecraft.getSingleplayerServer();
		return local == null
			? "tek-oyunculu"
			: "tek-oyunculu-" + local.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName();
	}
}
