package com.merci.survivalkit.wdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.module.impl.WorldDownloaderModule;
import com.mojang.brigadier.CommandDispatcher;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WdlCommandsTest {
	private CommandDispatcher<FabricClientCommandSource> dispatcher;
	private WorldDownloaderModule module;
	private FabricClientCommandSource source;
	private List<Component> feedbacks;
	private List<Component> errors;

	@BeforeEach
	void setUp() {
		dispatcher = new CommandDispatcher<>();
		module = new WorldDownloaderModule();
		feedbacks = new ArrayList<>();
		errors = new ArrayList<>();

		source = (FabricClientCommandSource) Proxy.newProxyInstance(
			FabricClientCommandSource.class.getClassLoader(),
			new Class<?>[]{FabricClientCommandSource.class},
			(proxy, method, args) -> {
				if ("sendFeedback".equals(method.getName())) {
					feedbacks.add((Component) args[0]);
					return null;
				}
				if ("sendError".equals(method.getName())) {
					errors.add((Component) args[0]);
					return null;
				}
				if ("getClient".equals(method.getName())) {
					return null;
				}
				return null;
			}
		);

		WdlCommands.register(dispatcher, module);
	}

	@Test
	void commandsAreRegisteredInDispatcher() {
		assertNotNull(dispatcher.getRoot().getChild("wdl"));
		assertNotNull(dispatcher.getRoot().getChild("wdl").getChild("start"));
		assertNotNull(dispatcher.getRoot().getChild("wdl").getChild("stop"));
		assertNotNull(dispatcher.getRoot().getChild("wdl").getChild("status"));
		assertNotNull(dispatcher.getRoot().getChild("wdl").getChild("save"));
		assertNotNull(dispatcher.getRoot().getChild("wdl").getChild("help"));
	}

	@Test
	void helpCommandPrintsHelp() throws Exception {
		int result = dispatcher.execute("wdl help", source);
		assertEquals(1, result);
		assertEquals(1, feedbacks.size());
		assertTrue(feedbacks.getFirst().getContents() instanceof TranslatableContents tc
			&& tc.getKey().equals("survivalkit.wdl.command.help"));
	}

	@Test
	void statusWhenDisabledReportsInactive() throws Exception {
		int result = dispatcher.execute("wdl status", source);
		assertEquals(1, result);
		assertEquals(1, feedbacks.size());
		assertTrue(feedbacks.getFirst().getContents() instanceof TranslatableContents tc
			&& tc.getKey().equals("survivalkit.wdl.command.status.inactive"));
	}

	@Test
	void rootCommandExecutesStatus() throws Exception {
		int result = dispatcher.execute("wdl", source);
		assertEquals(1, result);
		assertEquals(1, feedbacks.size());
		assertTrue(feedbacks.getFirst().getContents() instanceof TranslatableContents tc
			&& tc.getKey().equals("survivalkit.wdl.command.status.inactive"));
	}

	@Test
	void stopWhenDisabledReportsNotRunning() throws Exception {
		int result = dispatcher.execute("wdl stop", source);
		assertEquals(0, result);
		assertEquals(1, errors.size());
		assertTrue(errors.getFirst().getContents() instanceof TranslatableContents tc
			&& tc.getKey().equals("survivalkit.wdl.command.not_running"));
	}

	@Test
	void saveWhenDisabledReportsNotRunning() throws Exception {
		int result = dispatcher.execute("wdl save", source);
		assertEquals(0, result);
		assertEquals(1, errors.size());
		assertTrue(errors.getFirst().getContents() instanceof TranslatableContents tc
			&& tc.getKey().equals("survivalkit.wdl.command.not_running"));
	}

	@Test
	void startWhenNotInWorldReportsNotInWorld() throws Exception {
		int result = dispatcher.execute("wdl start", source);
		assertEquals(0, result);
		assertEquals(1, errors.size());
		assertTrue(errors.getFirst().getContents() instanceof TranslatableContents tc
			&& tc.getKey().equals("survivalkit.wdl.command.not_in_world"));
	}

	@Test
	void startWhenAlreadyEnabledReportsAlreadyRunning() throws Exception {
		Field runningField = Module.class.getDeclaredField("running");
		runningField.setAccessible(true);
		runningField.set(module, true);

		int result = dispatcher.execute("wdl start", source);
		assertEquals(0, result);
		assertEquals(1, errors.size());
		assertTrue(errors.getFirst().getContents() instanceof TranslatableContents tc
			&& tc.getKey().equals("survivalkit.wdl.command.already_running"));
	}
}
