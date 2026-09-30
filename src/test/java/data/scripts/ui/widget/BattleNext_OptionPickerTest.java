package data.scripts.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattleNext_OptionPickerTest {
	private SettingsAPI previousSettings;

	@BeforeEach
	void installSettings() {
		previousSettings = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "getColor" -> Color.GRAY;
					case "getFloat" -> 1f;
					case "getBoolean" -> false;
					case "getInt" -> 1;
					default -> null;
				}));
	}

	@AfterEach
	void restoreSettings() {
		Global.setSettings(previousSettings);
	}

	@Test
	void textTilesUseAtMostThreeWholeCharactersOnOneLine() {
		assertEquals("Agg", BattleNext_OptionPicker.tileLabel("Aggressive", value -> value.length() * 10d));
		assertEquals("慎重", BattleNext_OptionPicker.tileLabel("慎重型", value -> value.length() * 24d));
		assertEquals("A😀B", BattleNext_OptionPicker.tileLabel("A😀B cautious", value -> 40d));
		assertEquals("A B", BattleNext_OptionPicker.tileLabel("  A  B\nC  ", value -> 40d));
	}
}
