package data.scripts.ui.page;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.test.fixture.BattleNext_TestPageFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

class BattleNext_SettingsPageTest extends BattleNext_TestPageFixture {

	@Test
	void infoLogCheckboxFollowsGraphicsAndPersistsThroughSaveRefreshDiscardAndReopen() throws Exception {

		SettingsAPI delegate = Global.getSettings();
		Field valuesField = BattleNext_Settings.class.getDeclaredField("values");
		valuesField.setAccessible(true);
		Object previousValues = valuesField.get(null);
		AtomicReference<JSONObject> stored = new AtomicReference<>(new JSONObject());
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "readJSONFromCommon" -> stored.get();
					case "writeJSONToCommon" -> {
						stored.set(new JSONObject(((JSONObject) args[1]).toString()));
						yield null;
					}
					default -> method.invoke(delegate, args);
				}));
		BattleNext_SettingsPage page = new BattleNext_SettingsPage();
		CustomPanelAPI root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());
		try {
			page.attach(root);
			ButtonAPI logging = (ButtonAPI) getField(getField(page, "editor"), "infoLogCheckbox");
			assertFalse(logging.isChecked());
			assertEquals("Enable full graphic effects", BattleNext_TestTextCatalog.text("Settings", buttonLabels.get("fullGraphicEffect")));
			assertEquals("Enable info log", BattleNext_TestTextCatalog.text("Settings", buttonLabels.get("infoLog")));
			assertEquals(33f, buttonPositions.get("fullGraphicEffect").getY() - buttonPositions.get("infoLog").getY());
			assertEquals(33f, buttonPositions.get("infoLog").getY() - buttonPositions.get("rttInBattle").getY());
			logging.setChecked(true);
			assertTrue(page.hasUnsavedChanges());
			assertFalse(BattleNext_Settings.isInfoLogEnabled());
			page.refreshContent();
			assertTrue(((ButtonAPI) getField(getField(page, "editor"), "infoLogCheckbox")).isChecked());
			assertTrue(page.hasUnsavedChanges());
			page.discard();
			assertFalse(((ButtonAPI) getField(getField(page, "editor"), "infoLogCheckbox")).isChecked());
			assertFalse(page.hasUnsavedChanges());
			((ButtonAPI) getField(getField(page, "editor"), "infoLogCheckbox")).setChecked(true);
			assertTrue(page.save());
			assertTrue(stored.get().getBoolean("infoLog"));
			assertTrue(BattleNext_Settings.isInfoLogEnabled());
			assertFalse(page.hasUnsavedChanges());
			page.attach(root);
			assertTrue(((ButtonAPI) getField(getField(page, "editor"), "infoLogCheckbox")).isChecked());
			((ButtonAPI) getField(getField(page, "editor"), "infoLogCheckbox")).setChecked(false);
			page.discard();
			assertTrue(((ButtonAPI) getField(getField(page, "editor"), "infoLogCheckbox")).isChecked());
			((ButtonAPI) getField(getField(page, "editor"), "infoLogCheckbox")).setChecked(false);
			assertTrue(page.save());
			assertFalse(BattleNext_Settings.isInfoLogEnabled());
			page.attach(root);
			assertFalse(((ButtonAPI) getField(getField(page, "editor"), "infoLogCheckbox")).isChecked());
		} finally {
			page.close();
			Global.setSettings(delegate);
			valuesField.set(null, previousValues);
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {180, 1400})
	void deferredGeometryUsesLayoutWidthForAllFieldsAndPreservesDraftOnRefresh(int width) throws Exception {

		deferTooltipWidth = true;
		BattleNext_SettingsPage page = new BattleNext_SettingsPage();
		CustomPanelAPI root = panel(width, 900f, new BaseCustomUIPanelPlugin());
		page.attach(root);
		float expectedWidth = width - 12f;
		List<String> names = List.of("battleTimeLimitField", "defaultTimeMultField", "boostTimeMultField", "reportKeepCountField");
		List<String> values = List.of("700.5", "pending", "4.5", "75");
		assertFalse(page.hasUnsavedChanges());
		for (int index = 0; index < names.size(); index++) {
			TextFieldAPI field = (TextFieldAPI) getField(getField(page, "editor"), names.get(index));
			assertEquals(expectedWidth, field.getPosition().getWidth(), names.get(index));
			assertEquals(28f, field.getPosition().getHeight(), names.get(index));
			field.setText(values.get(index));
		}
		assertTrue(page.hasUnsavedChanges());
		page.refreshContent();
		for (int index = 0; index < names.size(); index++) {
			TextFieldAPI field = (TextFieldAPI) getField(getField(page, "editor"), names.get(index));
			assertEquals(expectedWidth, field.getPosition().getWidth(), names.get(index));
			assertEquals(28f, field.getPosition().getHeight(), names.get(index));
			assertEquals(values.get(index), field.getText(), names.get(index));
		}
		assertTrue(page.hasUnsavedChanges());
		assertEquals(1, children.get(root).size());
		BattleNext_UiInputRouter router = (BattleNext_UiInputRouter) getField(page, "inputRouter");
		assertEquals(4, ((Map<?, ?>) getField(router.getSession(), "registrations")).size());
		page.close();
		assertTrue(children.get(root).isEmpty());
		assertFalse(router.getSession().hasInputState());
	}

	@Test
	void attachRefreshAndCloseOwnThePagePanel() throws Exception {
		BattleNext_SettingsPage page = new BattleNext_SettingsPage();
		CustomPanelAPI root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());

		page.attach(root);
		assertNotNull(getField(page, "panel"));
		assertEquals(1, children.get(root).size());
		page.refreshContent();
		assertEquals(1, children.get(root).size());
		page.close();
		assertEquals(0, children.get(root).size());
	}
}
