package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

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

	@ParameterizedTest
	@ValueSource(strings = {"\n"})
	void descriptionRendersCompleteFirstParagraphBeforeNativeBoundsAreAssigned(String separator) {
		List<String> calls = new ArrayList<>();
		String paragraph = "Shields absorb 50% of damage. " + "Complete %s description. ".repeat(100).strip();
		TooltipMakerAPI tooltip = (TooltipMakerAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{TooltipMakerAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "setParaFontDefault" -> { calls.add("default"); yield null; }
					case "setParaFontColor" -> {
						assertEquals(BattleNext_UiTheme.TEXT, args[0]);
						calls.add("color");
						yield null;
					}
					case "addPara" -> {
						assertEquals(BattleNext_UiTheme.TEXT, args[1]);
						assertEquals(8f, args[2]);
						calls.add((String) args[0]);
						yield null;
					}
					case "getPosition", "getHeightSoFar", "createLabel" ->
							throw new AssertionError("Native tooltip content is built before its bounds are assigned");
					default -> null;
				});
		new TestProvider().renderDescription(tooltip, " \t" + paragraph + separator + "Second paragraph");
		assertEquals(List.of("default", "color", paragraph), calls);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\t\r\n"})
	void emptyDescriptionsKeepTheirHeader(String text) {
		List<String> rendered = new ArrayList<>();
		TooltipMakerAPI tooltip = (TooltipMakerAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{TooltipMakerAPI.class}, (instance, method, args) -> {
					if ("addPara".equals(method.getName())) rendered.add((String) args[0]);
					return null;
				});
		new TestProvider().renderDescription(tooltip, text);
		assertTrue(rendered.isEmpty());
	}

	private static class TestProvider extends BattleNext_BaseSourceProvider {
		private void renderHeader(TooltipMakerAPI tooltip, String key) {
			addHeader(tooltip, "Portrait", key);
		}

		private void renderDescription(TooltipMakerAPI tooltip, String text) {
			addDescription(tooltip, text);
		}

		@Override
		public List<OptionChoice> getOptions() {
			return List.of();
		}

		@Override
		public int getMaxStack(String id) {
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
