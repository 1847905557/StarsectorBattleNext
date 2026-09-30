package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_BaseSourceProviderTest {
	private SettingsAPI previousSettings;
	private SectorAPI previousSector;

	@BeforeEach
	void installSettings() {
		previousSettings = Global.getSettings();
		previousSector = Global.getSector();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "getColor" -> Color.GRAY;
					case "getFloat" -> 1f;
					case "getBoolean" -> false;
					case "getInt" -> 1;
					default -> null;
				}));
		FactionAPI faction = (FactionAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{FactionAPI.class}, (instance, method, args) -> Color.GRAY);
		Global.setSector((SectorAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SectorAPI.class}, (instance, method, args) ->
						"getPlayerFaction".equals(method.getName()) ? faction : null));
	}

	@AfterEach
	void restoreSettings() {
		Global.setSettings(previousSettings);
		Global.setSector(previousSector);
	}

	@Test
	void headerUsesTitleThenFullSmallTechnicalKeyAndRestoresDefaultFont() {
		List<String> calls = new ArrayList<>();
		TooltipMakerAPI tooltip = (TooltipMakerAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{TooltipMakerAPI.class}, (instance, method, args) -> {
					if ("addTitle".equals(method.getName())) calls.add("title:" + args[0]);
					if ("setParaSmallInsignia".equals(method.getName())) calls.add("small");
					if ("addPara".equals(method.getName())) {
						assertEquals(3, method.getParameterCount());
						calls.add("key:" + args[0]);
					}
					if ("setParaFontDefault".equals(method.getName())) calls.add("default");
					return null;
				});
		String path = "graphics/portraits/custom/50%_" + "long/".repeat(15) + "captain.png";
		new TestProvider().renderHeader(tooltip, path);

		assertEquals(List.of("title:Portrait", "small", "key:" + path, "default"), calls);
	}

	@Test
	void excerptPrefersCompleteSentencesAndPreservesPercentText() {
		String prose = "Shields absorb 50% of damage. Engines respond slowly. Crew remain ready.";

		assertEquals("Shields absorb 50% of damage...",
				BattleNext_BaseSourceProvider.fitExcerpt(prose, 40f,
						candidate -> candidate.length() <= 37 ? 40d : 80d));
		assertEquals(prose, BattleNext_BaseSourceProvider.fitExcerpt(prose, 120f, ignored -> 40d));
		assertTrue(BattleNext_BaseSourceProvider.fitExcerpt(prose, 0f, ignored -> 40d).isEmpty());
	}

	private static class TestProvider extends BattleNext_BaseSourceProvider {
		private void renderHeader(TooltipMakerAPI tooltip, String key) {
			addHeader(tooltip, "Portrait", key);
		}

		@Override
		public List<OptionChoice> getOptions() {
			return List.of();
		}

		@Override
		public int getMaxCount(String id) {
			return 1;
		}

		@Override
		public String getIconPath(String id) {
			return "";
		}

		@Override
		public void createTooltip(TooltipMakerAPI tooltip, OptionChoice option) {
			addHeader(tooltip, option.getLabel(), option.getId());
		}
	}
}
