package data.scripts.ui.page;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_TextField;
import data.simulator.BattleNext_SimulatorWidget;
import data.test.fixture.BattleNext_TestPageFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class BattleNext_SettingsPageTest extends BattleNext_TestPageFixture {
	private final AtomicReference<JSONObject> savedSettings = new AtomicReference<>();
	private boolean failSettingsWrite;

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
		BattleNext_SettingsPage page = new BattleNext_SettingsPage(action -> {});
		CustomPanelAPI root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());
		try {
			page.attach(root);
			ButtonAPI logging = (ButtonAPI) getField(getField(page, "editor"), "infoLogCheckbox");
			assertFalse(logging.isChecked());
			assertEquals(BattleNext_TestTextCatalog.text("Settings", "BattleNext_settings_full_graphic_effect"), buttonLabels.get("fullGraphicEffect"));
			assertEquals(BattleNext_TestTextCatalog.text("Settings", "BattleNext_settings_info_log"), buttonLabels.get("infoLog"));
			assertEquals(33f, buttonPositions.get("cnText").getY() - buttonPositions.get("fullGraphicEffect").getY());
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
	@CsvSource({"180,43", "1400,384"})
	void settingsAndSimulatorFillTheirOwnRegionsAndPreserveDraftOnRefresh(int width, int columnWidth) throws Exception {

		deferTooltipWidth = true;
		BattleNext_SettingsPage page = new BattleNext_SettingsPage(action -> {});
		CustomPanelAPI root = panel(width, 900f, new BaseCustomUIPanelPlugin());
		page.attach(root);
		Object editor = getField(page, "editor");
		CustomPanelAPI editorPanel = (CustomPanelAPI) getField(editor, "panel");
		CustomPanelAPI owner = (CustomPanelAPI) getField(editor, "owner");
		assertEquals(8f, owner.getPosition().getX() - root.getPosition().getX());
		assertEquals(columnWidth, owner.getPosition().getWidth());
		assertEquals(owner.getPosition().getX(), editorPanel.getPosition().getX());
		assertEquals(owner.getPosition().getY(), editorPanel.getPosition().getY());
		assertEquals(owner.getPosition().getWidth(), editorPanel.getPosition().getWidth());
		assertEquals(owner.getPosition().getHeight(), editorPanel.getPosition().getHeight());
		float expectedWidth = owner.getPosition().getWidth();
		BattleNext_SimulatorWidget simulator = (BattleNext_SimulatorWidget) getField(page, "simulatorWidget");
		PositionAPI simulatorBounds = simulator.getPanel().getPosition();
		assertEquals(owner.getPosition().getX() + columnWidth + 10f, simulatorBounds.getX());
		assertEquals(root.getPosition().getX() + width - 8f, simulatorBounds.getX() + simulatorBounds.getWidth());
		assertEquals(owner.getPosition().getY() + owner.getPosition().getHeight(),
				simulatorBounds.getY() + simulatorBounds.getHeight());
		PositionAPI enable = buttonPositions.get("simulator_enable");
		assertEquals(simulatorBounds.getCenterX(), enable.getCenterX(), 0.5f);
		assertEquals(buttonPositions.get("settings_save").getY(), simulatorBounds.getY());
		assertEquals(Misc.getButtonTextColor(), buttonColors.get(enable));
		CustomPanelAPI simulatorContent = (CustomPanelAPI) children.get(simulator.getPanel()).get(0);
		List<LabelAPI> warnings = children.get(simulatorContent).stream().filter(TooltipMakerAPI.class::isInstance)
				.flatMap(child -> tooltips.get(child).getComponents().stream()).filter(LabelAPI.class::isInstance)
				.map(LabelAPI.class::cast).filter(label -> label.getText().equals(BattleNext_TestTextCatalog.text("Settings", "BattleNext_settings_simulator_warning"))
						|| label.getText().equals(BattleNext_TestTextCatalog.text("Settings", "BattleNext_settings_simulator_warned"))).toList();
		assertEquals(2, warnings.size());
		PositionAPI description = warnings.get(0).getPosition();
		PositionAPI emphasized = warnings.get(1).getPosition();
		assertEquals(Misc.getTextColor(), labelColors.get(description));
		assertEquals(Misc.getNegativeHighlightColor(), labelColors.get(emphasized));
		assertEquals(simulatorBounds.getCenterX(), description.getCenterX(), 0.5f);
		assertEquals(simulatorBounds.getCenterX(), emphasized.getCenterX(), 0.5f);
		assertEquals(description.getY() - 12f, emphasized.getY() + emphasized.getHeight(), 0.5f);
		assertEquals(emphasized.getY() - 12f, enable.getY() + enable.getHeight(), 0.5f);
		assertEquals(simulatorBounds.getCenterY(), (description.getY() + description.getHeight() + enable.getY()) * 0.5f, 0.5f);
		TooltipMakerAPI border = (TooltipMakerAPI) children.get(simulatorContent).get(0);
		PositionAPI rectangle = tooltips.get(border).getComponents().get(0).getPosition();
		assertEquals(simulatorBounds.getX(), rectangle.getX());
		assertEquals(simulatorBounds.getY(), rectangle.getY());
		assertEquals(simulatorBounds.getWidth(), rectangle.getWidth());
		assertEquals(simulatorBounds.getHeight(), rectangle.getHeight());
		assertEquals(Misc.getNegativeHighlightColor(), rectangleColors.get(rectangle));
		assertEquals(2f, rectangleThicknesses.get(rectangle));
		List<String> names = List.of("battleTimeLimitField", "defaultTimeMultField", "boostTimeMultField", "reportKeepCountField");
		List<String> values = List.of("700.5", "pending", "4.5", "75");
		assertFalse(page.hasUnsavedChanges());
		for (int index = 0; index < names.size(); index++) {
			TextFieldAPI field = (TextFieldAPI) getField(getField(page, "editor"), names.get(index));
			assertEquals(expectedWidth, field.getPosition().getWidth(), names.get(index));
			assertEquals(owner.getPosition().getX(), field.getPosition().getX(), names.get(index));
			assertEquals(28f, field.getPosition().getHeight(), names.get(index));
			field.setText(values.get(index));
		}
		assertTrue(page.hasUnsavedChanges());
		page.refreshContent();
		assertNull(simulator.getPanel());
		assertTrue(removedComponents.contains(simulatorContent));
		for (int index = 0; index < names.size(); index++) {
			TextFieldAPI field = (TextFieldAPI) getField(getField(page, "editor"), names.get(index));
			assertEquals(expectedWidth, field.getPosition().getWidth(), names.get(index));
			assertEquals(owner.getPosition().getX(), field.getPosition().getX(), names.get(index));
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

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void languageDraftCommitRefreshDiscardAndReopenPreserveTheSession(boolean initial) throws Exception {

		installSettings(new JSONObject(), initial);
		BattleNext_SettingsPage page = new BattleNext_SettingsPage(action -> {});
		CustomPanelAPI root = panel(600f, 300f, new BaseCustomUIPanelPlugin());
		page.attach(root);
		Object editor = getField(page, "editor");
		ButtonAPI checkbox = (ButtonAPI) getField(editor, "cnTextCheckbox");
		assertEquals(initial, checkbox.isChecked());
		assertLanguageLabel(checkbox, initial);
		assertEquals(buttonPositions.get("fullGraphicEffect").getX(), checkbox.getPosition().getX());
		assertEquals(buttonPositions.get("fullGraphicEffect").getWidth(), checkbox.getPosition().getWidth());
		assertEquals(33f, checkbox.getPosition().getY() - buttonPositions.get("fullGraphicEffect").getY());
		checkbox.setChecked(!initial);
		assertTrue(page.hasUnsavedChanges());
		assertEquals(initial, BattleNext_Settings.isCnTextEnabled());
		page.refreshContent();
		assertEquals(!initial, ((ButtonAPI) getField(editor, "cnTextCheckbox")).isChecked());
		page.discard();
		assertEquals(initial, ((ButtonAPI) getField(editor, "cnTextCheckbox")).isChecked());
		assertFalse(page.hasUnsavedChanges());
		((ButtonAPI) getField(editor, "cnTextCheckbox")).setChecked(!initial);
		BattleNext_TextField field = (BattleNext_TextField) getField(editor, "battleTimeLimitField");
		field.restoreEditState(new BattleNext_TextField.EditState("700.50", 4, 1, 0f, "500.0", true));
		BattleNext_TextField.EditState input = field.captureEditState();
		Map<?, ?> viewports = (Map<?, ?>) getField(editor, "viewports");
		var scroller = ((TooltipMakerAPI) viewports.get("settings")).getExternalScroller();
		scroller.setYOffset(64f);
		float offset = scroller.getYOffset();
		assertTrue(offset > 0f);
		assertTrue(page.save(), page.getStatusText());
		assertEquals(!initial, savedSettings.get().getBoolean("cnText"));
		assertEquals(!initial, BattleNext_Settings.isCnTextEnabled());
		assertLanguageLabel((ButtonAPI) getField(editor, "cnTextCheckbox"), !initial);
		assertEquals(input, ((BattleNext_TextField) getField(editor, "battleTimeLimitField")).captureEditState());
		viewports = (Map<?, ?>) getField(editor, "viewports");
		assertEquals(offset, ((TooltipMakerAPI) viewports.get("settings")).getExternalScroller().getYOffset());
		assertEquals(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_saved"), page.getStatusText());
		assertFalse(page.hasUnsavedChanges());
		page.attach(root);
		assertEquals(!initial, ((ButtonAPI) getField(editor, "cnTextCheckbox")).isChecked());
		assertLanguageLabel((ButtonAPI) getField(editor, "cnTextCheckbox"), !initial);
		((ButtonAPI) getField(editor, "cnTextCheckbox")).setChecked(initial);
		assertTrue(page.save());
		assertLanguageLabel((ButtonAPI) getField(editor, "cnTextCheckbox"), initial);
		page.close();
		assertTrue(children.get(root).isEmpty());
		assertFalse(((BattleNext_UiInputRouter) getField(page, "inputRouter")).getSession().hasInputState());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void rejectedSaveKeepsDraftLanguageCommittedTextAndInputState(boolean initial) throws Exception {
		installSettings(new JSONObject().put("cnText", initial), !initial);
		BattleNext_SettingsPage page = new BattleNext_SettingsPage(action -> {});
		page.attach(panel(600f, 500f, new BaseCustomUIPanelPlugin()));
		Object editor = getField(page, "editor");
		((ButtonAPI) getField(editor, "cnTextCheckbox")).setChecked(!initial);
		BattleNext_TextField field = (BattleNext_TextField) getField(editor, "battleTimeLimitField");
		field.setText("pending");
		assertFalse(page.save());
		assertEquals("pending", field.getText());
		assertEquals(initial, BattleNext_Settings.isCnTextEnabled());
		assertLanguageLabel((ButtonAPI) getField(editor, "cnTextCheckbox"), initial);
		field.restoreEditState(new BattleNext_TextField.EditState("700.50", 4, 1, 0f, "500.0", true));
		var input = field.captureEditState();
		failSettingsWrite = true;
		assertFalse(page.save());
		assertEquals(initial, savedSettings.get().getBoolean("cnText"));
		assertEquals(initial, BattleNext_Settings.isCnTextEnabled());
		assertEquals(input, field.captureEditState());
		assertTrue(page.hasUnsavedChanges());
		assertLanguageLabel((ButtonAPI) getField(editor, "cnTextCheckbox"), initial);
		page.close();
	}

	private void installSettings(JSONObject initial, boolean vanillaDefault) {
		savedSettings.set(initial);
		SettingsAPI delegate = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
			if (method.getName().equals("getString")) return BattleNext_TestTextCatalog.lookup(args[0], args[1]);
			if (method.getName().equals("getBoolean") && args[0].equals("showCNTranslationCredits")) return vanillaDefault;
			if (method.getName().equals("readJSONFromCommon")) return savedSettings.get();
			if (method.getName().equals("writeJSONToCommon")) {
				if (failSettingsWrite) throw new IOException("Injected settings write failure");
				savedSettings.set(new JSONObject(((JSONObject) args[1]).toString()));
				return null;
			}
			try {
				return method.invoke(delegate, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
	}

	private void assertLanguageLabel(ButtonAPI checkbox, boolean enabled) {
		String category = "Settings";
		assertEquals(BattleNext_TestTextCatalog.text(category, "BattleNext_settings_cn_text"), checkbox.getText());
		assertEquals(BattleNext_TestTextCatalog.text(category, "BattleNext_settings_full_graphic_effect"), buttonLabels.get("fullGraphicEffect"));
	}
}
