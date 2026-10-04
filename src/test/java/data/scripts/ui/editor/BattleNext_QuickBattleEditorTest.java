package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.test.fixture.BattleNext_TestPageFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.List;

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

	@Test
	void editorAttachRefreshAndInputLifecycle() throws Exception {
		BattleNext_QuickBattleEditor editor = new BattleNext_QuickBattleEditor(() -> {}, new BaseCustomUIPanelPlugin());
		
		editor.setInputRouter(new BattleNext_UiInputRouter());
		CustomPanelAPI panel = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		attachEditor(editor, panel);
		assertNotNull(editor.getStatusText());
		editor.refreshContent();
	}
}
