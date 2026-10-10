package data.scripts.tools;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
	private boolean vanillaCnText;
	private int vanillaCnReads;

	@BeforeEach
	void configureSettings() throws ReflectiveOperationException {
		previousSettings = Global.getSettings();
		valuesField = BattleNext_Settings.class.getDeclaredField("values");
		valuesField.setAccessible(true);
		previousValues = valuesField.get(null);
		SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "getBoolean" -> {
						assertEquals("showCNTranslationCredits", args[0]);
						vanillaCnReads++;
						yield vanillaCnText;
					}
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
		BattleNext_Settings.save(false, true, false, true, 500f, 1f, 3f, 50, 0);
		assertTrue(stored.get().optBoolean("rttInBattle"));
		BattleNext_Settings.load();
		assertTrue(BattleNext_Settings.isRttInBattle());
		BattleNext_Settings.save(false, true, false, false, 500f, 1f, 3f, 50, 0);
		assertFalse(stored.get().optBoolean("rttInBattle", true));
	}

	@ParameterizedTest
	@ValueSource(ints = {-7, 0, 120})
	void deploymentLimitPersistsSignedIntegersAndFailedWritesKeepThePublishedValue(int limit) throws Exception {
		assertEquals(0, BattleNext_Settings.getDeploymentPointLimit());
		BattleNext_Settings.save(false, true, false, false, 500f, 1f, 3f, 50, limit);
		assertEquals(limit, stored.get().getInt("deploymentPointLimit"));
		BattleNext_Settings.load();
		assertEquals(limit, BattleNext_Settings.getDeploymentPointLimit());
		failWrite = true;
		assertThrows(IllegalStateException.class, () -> BattleNext_Settings.save(false, true, false, false, 500f, 1f, 3f, 50, limit + 1));
		assertEquals(limit, BattleNext_Settings.getDeploymentPointLimit());
		assertEquals(limit, stored.get().getInt("deploymentPointLimit"));
	}

	@Test
	void infoLogDefaultsOffAndPersistsBothStates() {
		assertFalse(BattleNext_Settings.isInfoLogEnabled());
		BattleNext_Settings.save(false, true, true, false, 500f, 1f, 3f, 50, 0);
		assertTrue(stored.get().optBoolean("infoLog"));
		assertTrue(BattleNext_Settings.isInfoLogEnabled());
		BattleNext_Settings.load();
		assertTrue(BattleNext_Settings.isInfoLogEnabled());
		BattleNext_Settings.save(false, true, false, false, 500f, 1f, 3f, 50, 0);
		assertFalse(stored.get().optBoolean("infoLog", true));
		BattleNext_Settings.load();
		assertFalse(BattleNext_Settings.isInfoLogEnabled());
	}

	@Test
	void failedWritePreservesCommittedInfoLogSetting() {
		BattleNext_Settings.save(false, true, true, false, 500f, 1f, 3f, 50, 0);
		failWrite = true;
		assertThrows(IllegalStateException.class, () -> BattleNext_Settings.save(false, true, false, false, 500f, 1f, 3f, 50, 0));
		assertTrue(BattleNext_Settings.isInfoLogEnabled());
		assertTrue(stored.get().optBoolean("infoLog"));
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void missingCnTextUsesCurrentVanillaDefaultUntilExplicitlySaved(boolean enabled) throws Exception {
		vanillaCnText = enabled;
		vanillaCnReads = 0;
		stored.set(new JSONObject().put("fullGraphicEffect", false));
		BattleNext_Settings.load();
		assertEquals(enabled, BattleNext_Settings.isCnTextEnabled());
		assertFalse(BattleNext_Settings.isFullGraphicEffect());
		assertEquals(1, vanillaCnReads);
		assertFalse(stored.get().has("cnText"));
		vanillaCnText = !enabled;
		BattleNext_Settings.load();
		assertEquals(!enabled, BattleNext_Settings.isCnTextEnabled());
		BattleNext_Settings.save(enabled, true, false, false, 500f, 1f, 3f, 50, 0);
		assertEquals(enabled, stored.get().getBoolean("cnText"));
		vanillaCnReads = 0;
		BattleNext_Settings.load();
		assertEquals(enabled, BattleNext_Settings.isCnTextEnabled());
		assertEquals(0, vanillaCnReads);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void failedWritePreservesCommittedLanguage(boolean enabled) {
		BattleNext_Settings.save(enabled, true, false, false, 500f, 1f, 3f, 50, 0);
		failWrite = true;
		assertThrows(IllegalStateException.class, () -> BattleNext_Settings.save(!enabled, true, false, false, 500f, 1f, 3f, 50, 0));
		assertEquals(enabled, BattleNext_Settings.isCnTextEnabled());
		assertEquals(enabled, stored.get().optBoolean("cnText"));
	}
}
