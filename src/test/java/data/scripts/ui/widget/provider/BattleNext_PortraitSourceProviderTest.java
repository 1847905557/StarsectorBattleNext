package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.FactionSpecAPI;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.LabelAPI;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import data.test.fixture.BattleNext_TestTextCatalog;
import data.test.fixture.BattleNext_TestUiFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;

class BattleNext_PortraitSourceProviderTest extends BattleNext_TestUiFixture {

	@Test
	void factionAndCustomPortraitsUseTheirRegisteredDescriptionAndPreserveTheirIdentity() {
		FactionSpecAPI faction = proxy(FactionSpecAPI.class, (method, args) -> switch (method) {
			case "getId" -> "hegemony";
			case "getDisplayName" -> "Hegemony";
			case "getAllPortraits" -> List.of("graphics/portraits/hegemony.png");
			default -> null;
		});
		Description description = new Description("hegemony", Description.Type.FACTION);
		description.setText1("The Hegemony descends from a Domain fleet.\nSecond paragraph.");
		SettingsAPI delegate = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
			if (method.getName().equals("getAllFactionSpecs")) return List.of(faction);
			if (method.getName().equals("getDescription")) {
				assertEquals("hegemony", args[0]);
				assertEquals(Description.Type.FACTION, args[1]);
				return description;
			}
			try {
				return method.invoke(delegate, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		var provider = new BattleNext_PortraitSourceProvider();
		var factionTooltip = tooltip(provider.getTooltipWidth(), 0f, false);
		provider.createTooltip(factionTooltip, new OptionChoice("graphics/portraits/hegemony.png", "Hegemony"));
		assertEquals(List.of("Hegemony", "graphics/portraits/hegemony.png", "The Hegemony descends from a Domain fleet."),
				tooltips.get(factionTooltip).getComponents().stream().filter(LabelAPI.class::isInstance).map(LabelAPI.class::cast).map(LabelAPI::getText).toList());
		var customTooltip = tooltip(provider.getTooltipWidth(), 0f, false);
		provider.createTooltip(customTooltip, new OptionChoice("graphics/portraits/custom.png", "custom"));
		assertEquals(List.of("custom", "graphics/portraits/custom.png", BattleNext_TestTextCatalog.text("Member", "BattleNext_member_custom_portrait")),
				tooltips.get(customTooltip).getComponents().stream().filter(LabelAPI.class::isInstance).map(LabelAPI.class::cast).map(LabelAPI::getText).toList());
	}
}
