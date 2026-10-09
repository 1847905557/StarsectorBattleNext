package data.scripts.ui.editor;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.*;
import data.missions.BattleNext_MissionBattleMap;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay.Column;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.BattleNext_TextField;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.tools.BattleNext_Settings;
import data.test.fixture.BattleNext_TestPageFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

class BattleNext_QuickBattleEditorTest extends BattleNext_TestPageFixture {

	@Test
	void fleetSelectionSupportsCancelClearRefreshAndSavedIds() throws Exception {

		SettingsAPI delegate = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
					if (method.getName().equals("loadText") && Files.exists(directory.resolve((String) args[0]))) {
						return Files.readString(directory.resolve((String) args[0]), StandardCharsets.UTF_8);
					}
					try {
						return method.invoke(delegate, args);
					} catch (InvocationTargetException exception) {
						throw exception.getCause();
					}
				}));
		var editor = new BattleNext_QuickBattleEditor(() -> {}, new BaseCustomUIPanelPlugin());
		attachEditor(editor, panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		var fleets = (BattleNext_FleetStore) getField(editor, "fleetStore");
		fleets.save(new BattleNext_FleetModel("first", "First fleet", List.of(), List.of(), List.of()));
		fleets.save(new BattleNext_FleetModel("second", "Second fleet", List.of(), List.of(), List.of()));
		((List<?>) getField(editor, "entries")).clear();
		editor.refreshContent();
		editor.buttonPressed("add");
		var display = (BattleNext_QuickBattleDisplay) getField(editor, "display");
		selectQuickBattleFleet(display, 0, Column.PLAYER, "first");
		selectQuickBattleFleet(display, 0, Column.ENEMY, "second");
		assertTrue(editor.hasUnsavedChanges());
		assertEquals("first", display.getRows().get(0).getFleetId(Column.PLAYER));
		assertEquals("second", display.getRows().get(0).getFleetId(Column.ENEMY));
		var fleetFields = (Map<Column, BattleNext_OptionField>) getField(display.getRows().get(0), "fleetFields");
		assertEquals("first", fleetFields.get(Column.PLAYER).getField().getText());
		assertTrue(((BattleNext_TextField) fleetFields.get(Column.PLAYER).getField()).isEditable());
		assertEquals("...", buttonLabels.get("TOGGLE"));
		assertEquals(32f, buttonPositions.get("TOGGLE").getWidth());
		fleetFields.get(Column.PLAYER).getField().setText("typed_id");
		editor.refreshContent();
		display = (BattleNext_QuickBattleDisplay) getField(editor, "display");
		fleetFields = (Map<Column, BattleNext_OptionField>) getField(display.getRows().get(0), "fleetFields");
		assertEquals("typed_id", display.getRows().get(0).getFleetId(Column.PLAYER));
		fleetFields.get(Column.PLAYER).getField().setText("first");
		TooltipMakerAPI hover = tooltip(400f, 0f, false);
		attachedTooltips.get((CustomPanelAPI) getField(fleetFields.get(Column.PLAYER), "row")).createTooltip(hover, false, null);
		assertTrue(tooltips.get(hover).getComponents().stream().filter(LabelAPI.class::isInstance)
				.map(LabelAPI.class::cast).map(LabelAPI::getText).anyMatch(text -> text.contains("First fleet")));
		var picker = openQuickBattleFleet(display, 0, Column.PLAYER);
		((CustomPanelAPI) getField(picker, "popup")).getPlugin().buttonPressed(buttonIds.get("CANCEL"));
		assertEquals("first", display.getRows().get(0).getFleetId(Column.PLAYER));
		selectQuickBattleFleet(display, 0, Column.PLAYER, "first");
		assertEquals("", display.getRows().get(0).getFleetId(Column.PLAYER));
		assertFalse(editor.save());
		selectQuickBattleFleet(display, 0, Column.PLAYER, "second");
		editor.buttonPressed("add");
		editor.buttonPressed("delete:1");
		display = (BattleNext_QuickBattleDisplay) getField(editor, "display");
		assertEquals("second", display.getRows().get(0).getFleetId(Column.PLAYER));
		assertEquals("second", display.getRows().get(0).getFleetId(Column.ENEMY));
		assertTrue(editor.save());
		editor.deserialize();
		display = (BattleNext_QuickBattleDisplay) getField(editor, "display");
		assertTrue(display.getRows().stream().anyMatch(row -> row.getModel().getPlayer().equals("second")
				&& row.getModel().getEnemy().equals("second")));
		openQuickBattleFleet(display, 0, Column.ENEMY);
		editor.close();
		assertTrue(((BattleNext_UiInputRouter) getField(editor, "inputRouter")).isEmpty());
	}

	@Test
	void addingRowsPreservesUnfinishedInputsAndAllowsValidRawPromotion() throws Exception {

		BattleNext_QuickBattleEditor editor = new BattleNext_QuickBattleEditor(() -> {}, new BaseCustomUIPanelPlugin());
		attachEditor(editor, panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		assertEquals("", ((LabelAPI) getField(editor, "statusLabel")).getText());
		List<BattleNext_QuickBattleModel> entries = (List<BattleNext_QuickBattleModel>) getField(editor, "entries");
		entries.clear();
		entries.add(BattleNext_QuickBattleModel.parseLine("# unfinished", false));
		entries.add(new BattleNext_QuickBattleModel("first", "second", -1, 1f));
		editor.refreshContent();
		List<?> rows = (List<?>) getField(getField(editor, "display"), "rows");
		((TextFieldAPI) getField(rows.get(0), "rawField")).setText("first,second,4,2");
		((TextFieldAPI) getField(rows.get(1), "mapField")).setText("pending");
		editor.buttonPressed("add");
		assertEquals(BattleNext_QuickBattleModel.QuickBattleRowType.BATTLE, entries.get(0).getRowType());
		assertEquals(3, entries.size());
		assertEquals(-1, entries.get(1).getMap());
		assertEquals("first", ((BattleNext_QuickBattleDisplay) getField(editor, "display")).getRows().get(0).getFleetId(Column.PLAYER));
		assertDoesNotThrow(editor::refreshRowStatuses);
		rows = (List<?>) getField(getField(editor, "display"), "rows");
		assertEquals("first", ((BattleNext_QuickBattleDisplay.Row) rows.get(0)).getFleetId(Column.PLAYER));
		assertEquals("pending", ((TextFieldAPI) getField(rows.get(1), "mapField")).getText());
		editor.buttonPressed("delete:2");
		String deleted = BattleNext_TestTextCatalog.text("Common", "BattleNext_common_deleted");
		assertEquals(deleted, editor.getStatusText());
		assertEquals(deleted, ((LabelAPI) getField(editor, "statusLabel")).getText());
		editor.refreshContent();
		assertEquals(deleted, ((LabelAPI) getField(editor, "statusLabel")).getText());
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
		List<?> rows = (List<?>) getField(getField(editor, "display"), "rows");
		((BattleNext_FleetStore) getField(editor, "fleetStore")).save(new BattleNext_FleetModel("edited_player", "Edited", List.of(), List.of(), List.of()));
		selectQuickBattleFleet((BattleNext_QuickBattleDisplay) getField(editor, "display"), 0, Column.PLAYER, "edited_player");
		((TextFieldAPI) getField(rows.get(0), "mapField")).setText("pending");
		((ButtonAPI) getField(rows.get(0), "enabledCheckbox")).setChecked(false);
		((TextFieldAPI) getField(rows.get(2), "rawField")).setText("# unfinished draft");
		((TextFieldAPI) getField(rows.get(1), "rawField")).setText("first,second,4,2");
		editor.refreshRowStatuses();
		rows = (List<?>) getField(getField(editor, "display"), "rows");
		assertEquals("edited_player", ((BattleNext_QuickBattleDisplay.Row) rows.get(0)).getFleetId(Column.PLAYER));
		assertEquals("pending", ((TextFieldAPI) getField(rows.get(0), "mapField")).getText());
		assertFalse(((ButtonAPI) getField(rows.get(0), "enabledCheckbox")).isChecked());
		assertEquals("# unfinished draft", ((TextFieldAPI) getField(rows.get(2), "rawField")).getText());
		assertEquals("first", ((BattleNext_QuickBattleDisplay.Row) rows.get(1)).getFleetId(Column.PLAYER));
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
			if (method.getName().equals("getString")) return BattleNext_TestTextCatalog.lookup(args[0], args[1]);
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
		Object row = ((List<?>) getField(getField(editor, "display"), "rows")).get(0);
		String category = "Battle";
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
			editor.refreshContent();
			assertEquals(saveError, ((LabelAPI) getField(editor, "statusLabel")).getText());
			row = ((List<?>) getField(getField(editor, "display"), "rows")).get(0);
		}
		editor.close();
	}
}
