package com.merci.survivalkit.gametest;

import com.merci.survivalkit.SurvivalKit;
import com.merci.survivalkit.module.Module;
import com.merci.survivalkit.util.SettingsAudit;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * House rule, checked against the live registry rather than a list someone has to maintain:
 * every module says what it is, and every setting explains itself. A module added later is
 * held to this without anybody remembering to come back here.
 */
public class SettingsAuditGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		List<Module> modules = context.computeOnClient(client -> List.copyOf(SurvivalKit.modules().modules()));

		if (modules.isEmpty()) {
			throw new AssertionError("hiç modül kayıtlı değil, tarama bir şey kanıtlamaz");
		}

		List<String> offenders = new ArrayList<>();

		for (Module module : modules) {
			if (module.description().isBlank()) {
				offenders.add(module.id() + " (modül açıklaması)");
			}

			offenders.addAll(SettingsAudit.settingsWithoutTooltip(module.settings()));
		}

		if (!offenders.isEmpty()) {
			throw new AssertionError("tooltip'siz ayar ya da açıklamasız modül var: " + offenders);
		}

		// Every setting the check walked, at every depth; headings are not settings. This used to
		// count each module's top level only, so a whole section read as one and settings added
		// under a heading never moved the number.
		int settings = modules.stream().mapToInt(module -> SettingsAudit.settingsChecked(module.settings())).sum();
		SurvivalKit.LOGGER.info("[gametest] tooltip taraması geçti ({} modül, {} ayar, hepsi dolu)",
				modules.size(), settings);
	}
}
