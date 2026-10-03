package data.scripts.tools;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

class BattleNext_SettingsTest {
	private SettingsAPI previousSettings;
	private Field valuesField;
	private Object previousValues;
	private final AtomicReference<JSONObject> stored = new AtomicReference<>();
	private boolean failWrite;

	@BeforeEach
	void configureSettings() throws ReflectiveOperationException {
		previousSettings = Global.getSettings();
		valuesField = BattleNext_Settings.class.getDeclaredField("values");
		valuesField.setAccessible(true);
		previousValues = valuesField.get(null);
		SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "readJSONFromCommon" -> stored.get();
					case "writeJSONToCommon" -> {
						if (failWrite) throw new IOException("Injected settings write failure");
						stored.set((JSONObject) args[1]);
						yield null;
					}
					default -> null;
				});
		Global.setSettings(settings);
		stored.set(new JSONObject());
		BattleNext_Settings.load();
	}

	@AfterEach
	void restoreSettings() throws ReflectiveOperationException {
		Global.setSettings(previousSettings);
		valuesField.set(null, previousValues);
	}

	@Test
	void pvpRttDefaultsOffAndPersistsWhenSaved() {
		assertFalse(BattleNext_Settings.isRttInBattle());
		BattleNext_Settings.save(true, false, true, 500f, 1f, 3f, 50);
		assertTrue(stored.get().optBoolean("rttInBattle"));
		BattleNext_Settings.load();
		assertTrue(BattleNext_Settings.isRttInBattle());
		BattleNext_Settings.save(true, false, false, 500f, 1f, 3f, 50);
		assertFalse(stored.get().optBoolean("rttInBattle", true));
	}

	@Test
	void infoLogDefaultsOffAndPersistsBothStates() {
		assertFalse(BattleNext_Settings.isInfoLogEnabled());
		BattleNext_Settings.save(true, true, false, 500f, 1f, 3f, 50);
		assertTrue(stored.get().optBoolean("infoLog"));
		assertTrue(BattleNext_Settings.isInfoLogEnabled());
		BattleNext_Settings.load();
		assertTrue(BattleNext_Settings.isInfoLogEnabled());
		BattleNext_Settings.save(true, false, false, 500f, 1f, 3f, 50);
		assertFalse(stored.get().optBoolean("infoLog", true));
		BattleNext_Settings.load();
		assertFalse(BattleNext_Settings.isInfoLogEnabled());
	}

	@Test
	void failedWritePreservesCommittedInfoLogSetting() {
		BattleNext_Settings.save(true, true, false, 500f, 1f, 3f, 50);
		failWrite = true;
		assertThrows(IllegalStateException.class, () -> BattleNext_Settings.save(true, false, false, 500f, 1f, 3f, 50));
		assertTrue(BattleNext_Settings.isInfoLogEnabled());
		assertTrue(stored.get().optBoolean("infoLog"));
	}
}
