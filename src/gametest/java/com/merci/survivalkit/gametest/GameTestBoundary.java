package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.Module;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Sits between the tests rather than being one: the build puts it before every client gametest
 * and once after the last (see processGametestResources), so no test file has to clean up after
 * itself.
 *
 * <p>Each time it runs it does two things. Every module goes back to its defaults and off, so a
 * test starts the same whichever test ran before it — the parallel run deals the tests out in a
 * different order than the serial one. And it logs how long the test before it took and which test
 * comes next, one line each, which the parallel run reads back: the durations balance the next
 * run's shards, and a "starting" line with no "took" after it names the test that failed.
 */
public class GameTestBoundary implements FabricClientGameTest {
	private static int crossed;
	private static String previous;
	private static long previousStart;

	@Override
	public void runTest(ClientGameTestContext context) {
		long now = System.nanoTime();

		if (previous != null) {
			SurvivalKit.LOGGER.info("[gametest-süre] {} {}", previous, String.format(Locale.ROOT, "%.1f", (now - previousStart) / 1e9));
		}

		context.runOnClient(client -> {
			for (Module module : SurvivalKit.modules().modules()) {
				module.settings().reset();
				module.setEnabled(false);
			}
		});

		List<String> tests = FabricLoader.getInstance()
			.getEntrypointContainers("fabric-client-gametest", FabricClientGameTest.class).stream()
			.map(container -> container.getDefinition())
			.filter(definition -> !definition.equals(GameTestBoundary.class.getName()))
			.map(definition -> definition.substring(definition.lastIndexOf('.') + 1))
			.toList();

		previous = crossed < tests.size() ? tests.get(crossed) : null;
		crossed++;

		if (previous != null) {
			SurvivalKit.LOGGER.info("[gametest-başla] {}", previous);
		}

		previousStart = System.nanoTime();
	}
}
