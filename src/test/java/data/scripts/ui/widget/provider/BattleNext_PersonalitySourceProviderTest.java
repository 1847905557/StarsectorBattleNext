package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import data.test.fixture.BattleNext_TestUiFixture;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_PersonalitySourceProviderTest extends BattleNext_TestUiFixture {

	@Test
	void mergedCatalogSortsValidIdentitiesUsesFallbackNamesAndRendersTheRegisteredDescription() throws Exception {
		var rows = new JSONArray()
				.put(new JSONObject().put("id", " steady ").put("name", " ").put("desc", "Balances aggression and defense.\nSecond paragraph."))
				.put(new JSONObject().put("id", "").put("name", "Unknown"))
				.put("invalid row")
				.put(new JSONObject().put("id", "aggressive").put("name", "Aggressive"));
		SettingsAPI delegate = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
			if (method.getName().equals("getMergedSpreadsheetData")) {
				assertEquals("id", args[0]);
				assertEquals("data/characters/personalities.csv", args[1]);
				return rows;
			}
			try {
				return method.invoke(delegate, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		var provider = new BattleNext_PersonalitySourceProvider();
		var options = provider.getOptions();
		assertEquals(List.of("aggressive", "steady"), options.stream().map(OptionChoice::getId).toList());
		assertEquals(List.of("Aggressive", "steady"), options.stream().map(OptionChoice::getLabel).toList());
		var tooltip = tooltip(provider.getTooltipWidth(), 0f, false);
		provider.createTooltip(tooltip, options.get(1));
		assertEquals(List.of("steady", "steady", "Balances aggression and defense."),
				tooltips.get(tooltip).getComponents().stream().filter(LabelAPI.class::isInstance).map(LabelAPI.class::cast).map(LabelAPI::getText).toList());
	}
}
