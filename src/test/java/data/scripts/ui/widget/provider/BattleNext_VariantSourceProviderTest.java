package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

class BattleNext_VariantSourceProviderTest {

	@Test
	void variantsAreSingleSelection() {
		BattleNext_VariantSourceProvider provider = new BattleNext_VariantSourceProvider();

		assertEquals(1, provider.getMaxCount("any_variant"));
	}

	@Test
	void percentageInVariantIdUsesPlainParagraphOverload() {
		SettingsAPI previousSettings = Global.getSettings();
		SectorAPI previousSector = Global.getSector();
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
		List<String> rendered = new ArrayList<>();
		TooltipMakerAPI tooltip = (TooltipMakerAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{TooltipMakerAPI.class}, (instance, method, args) -> {
					if ("addPara".equals(method.getName())) {
						assertEquals(3, method.getParameterCount());
						rendered.add((String) args[0]);
					}
					return null;
				});

		try {
			new BattleNext_VariantSourceProvider().createTooltip(tooltip,
					new OptionChoice("variant_50%_ready", "Variant"));
			assertEquals("variant_50%_ready", rendered.get(0));
			assertEquals(2, rendered.size());
			Assertions.assertTrue(rendered.get(1).contains("Unknown variant: variant_50%_ready"));
		} finally {
			Global.setSettings(previousSettings);
			Global.setSector(previousSector);
		}
	}
}
