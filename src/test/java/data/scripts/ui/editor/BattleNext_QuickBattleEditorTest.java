package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.missions.BattleNext_MissionBattleMap;
import data.scripts.tools.BattleNext_Settings;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.test.fixture.BattleNext_TestPageFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;

class BattleNext_QuickBattleEditorTest extends BattleNext_TestPageFixture {

	@Test
	void addingRowsPreservesUnfinishedInputsAndAllowsValidRawPromotion() throws Exception {

		BattleNext_QuickBattleEditor editor = new BattleNext_QuickBattleEditor(() -> {}, new BaseCustomUIPanelPlugin());
		attachEditor(editor, panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		List<BattleNext_QuickBattleModel> entries = (List<BattleNext_QuickBattleModel>) getField(editor, "entries");
		entries.clear();
		entries.add(BattleNext_QuickBattleModel.parseLine("# unfinished", false));
		entries.add(new BattleNext_QuickBattleModel("first", "second", -1, 1f));
		editor.refreshContent();
		List<?> rows = (List<?>) getField(editor, "rows");
		((TextFieldAPI) getField(rows.get(0), "rawField")).setText("first,second,4,2");
		((TextFieldAPI) getField(rows.get(1), "mapField")).setText("pending");
		editor.buttonPressed("add");
		assertEquals(BattleNext_QuickBattleModel.QuickBattleRowType.BATTLE, entries.get(0).getRowType());
		assertEquals(3, entries.size());
		assertEquals(-1, entries.get(1).getMap());
		assertNotNull(getField(((List<?>) getField(editor, "rows")).get(0), "playerField"));
		assertDoesNotThrow(editor::refreshRowStatuses);
		rows = (List<?>) getField(editor, "rows");
		assertEquals("first", ((TextFieldAPI) getField(rows.get(0), "playerField")).getText());
		assertEquals("pending", ((TextFieldAPI) getField(rows.get(1), "mapField")).getText());
	}

	@Test
	void rawPromotionPreservesOtherRowsUnfinishedDraftAndCheckboxes() throws Exception {

		BattleNext_QuickBattleEditor editor = new BattleNext_QuickBattleEditor(() -> {}, new BaseCustomUIPanelPlugin());
		CustomPanelAPI root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());
		attachEditor(editor, root);
		List<BattleNext_QuickBattleModel> entries = (List<BattleNext_QuickBattleModel>) getField(editor, "entries");
		entries.clear();
		entries.add(new BattleNext_QuickBattleModel("first", "second", -1, 1f));
		entries.add(BattleNext_QuickBattleModel.parseLine("# raw", false));
		entries.add(BattleNext_QuickBattleModel.parseLine("# unfinished", false));
		editor.refreshContent();
		List<?> rows = (List<?>) getField(editor, "rows");
		((TextFieldAPI) getField(rows.get(0), "playerField")).setText("edited_player");
		((TextFieldAPI) getField(rows.get(0), "mapField")).setText("pending");
		((ButtonAPI) getField(rows.get(0), "enabledCheckbox")).setChecked(false);
		((TextFieldAPI) getField(rows.get(2), "rawField")).setText("# unfinished draft");
		((TextFieldAPI) getField(rows.get(1), "rawField")).setText("first,second,4,2");
		editor.refreshRowStatuses();
		rows = (List<?>) getField(editor, "rows");
		assertEquals("edited_player", ((TextFieldAPI) getField(rows.get(0), "playerField")).getText());
		assertEquals("pending", ((TextFieldAPI) getField(rows.get(0), "mapField")).getText());
		assertFalse(((ButtonAPI) getField(rows.get(0), "enabledCheckbox")).isChecked());
		assertEquals("# unfinished draft", ((TextFieldAPI) getField(rows.get(2), "rawField")).getText());
		assertEquals("first", ((TextFieldAPI) getField(rows.get(1), "playerField")).getText());
		assertEquals("4", ((TextFieldAPI) getField(rows.get(1), "mapField")).getText());
		assertFalse(((ButtonAPI) getField(rows.get(1), "enabledCheckbox")).isChecked());
		String state = editor.serializeState();
		editor.refreshContent();
		assertEquals(state, editor.serializeState());
		attachEditor(editor, root);
		assertEquals(state, editor.serializeState());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void mapValidationTooltipsAndSaveMessagesUseTheCurrentMapLimit(boolean cnText) throws Exception {

		SettingsAPI delegate = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
			if (method.getName().equals("getString")) return BattleNext_TestTextCatalog.text(args[0], args[1]);
			try {
				return method.invoke(delegate, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		BattleNext_Settings.save(cnText, true, false, false, 500f, 1f, 3f, 50);
		BattleNext_QuickBattleEditor editor = new BattleNext_QuickBattleEditor(() -> {}, new BaseCustomUIPanelPlugin());
		attachEditor(editor, panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		List<BattleNext_QuickBattleModel> entries = (List<BattleNext_QuickBattleModel>) getField(editor, "entries");
		entries.clear();
		entries.add(new BattleNext_QuickBattleModel("first", "second", BattleNext_MissionBattleMap.RANDOM_MAP_ID, 1f));
		editor.refreshContent();
		Object row = ((List<?>) getField(editor, "rows")).get(0);
		String category = cnText ? "Battle_CN" : "Battle";
		String rowError = String.format(BattleNext_TestTextCatalog.text(category, "BattleNext_battle_invalid_map"), BattleNext_MissionBattleMap.LAST_MAP_ID);
		String saveError = String.format(BattleNext_TestTextCatalog.text(category, "BattleNext_battle_invalid_map_settings"), BattleNext_MissionBattleMap.LAST_MAP_ID);
		for (String input : List.of(Integer.toString(BattleNext_MissionBattleMap.LAST_MAP_ID + 1), "pending")) {
			((TextFieldAPI) getField(row, "mapField")).setText(input);
			TooltipMakerAPI tooltip = tooltip(360f, 0f, false);
			attachedTooltips.get(getField(row, "statusLabel")).createTooltip(tooltip, false, null);
			assertTrue(tooltips.get(tooltip).getComponents().stream().filter(LabelAPI.class::isInstance)
					.map(LabelAPI.class::cast).map(LabelAPI::getText).anyMatch(rowError::equals));
			assertFalse(editor.save());
			assertEquals(saveError, editor.getStatusText());
		}
		editor.close();
	}
}
