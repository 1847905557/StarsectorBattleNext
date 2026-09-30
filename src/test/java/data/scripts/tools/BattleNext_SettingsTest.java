package data.scripts.tools;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

class BattleNext_SettingsTest {

	@Test
	void pvpRttDefaultsOffAndPersistsWhenSaved() throws ReflectiveOperationException {
		SettingsAPI previousSettings = Global.getSettings();
		Field valuesField = BattleNext_Settings.class.getDeclaredField("values");
		valuesField.setAccessible(true);
		Object previousValues = valuesField.get(null);
		AtomicReference<JSONObject> stored = new AtomicReference<>();
		SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "readJSONFromCommon" -> stored.get();
					case "writeJSONToCommon" -> {
						stored.set((JSONObject) args[1]);
						yield null;
					}
					default -> null;
				});
		try {
			Global.setSettings(settings);
			stored.set(new JSONObject());
			BattleNext_Settings.load();
			assertFalse(BattleNext_Settings.isRttInBattle());

			BattleNext_Settings.save(true, true, 500f, 1f, 3f, 50);
			assertTrue(stored.get().optBoolean("rttInBattle"));
			BattleNext_Settings.load();
			assertTrue(BattleNext_Settings.isRttInBattle());

			BattleNext_Settings.save(true, false, 500f, 1f, 3f, 50);
			assertFalse(stored.get().optBoolean("rttInBattle", true));
		} finally {
			Global.setSettings(previousSettings);
			valuesField.set(null, previousValues);
		}
	}
}
