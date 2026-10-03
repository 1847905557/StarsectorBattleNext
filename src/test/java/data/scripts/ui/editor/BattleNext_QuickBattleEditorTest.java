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
	void rejectedBatchKeepsRawRowModelAlignedWithItsControlsUntilPromotion() throws Exception {

		BattleNext_QuickBattleEditor editor = new BattleNext_QuickBattleEditor(() -> {}, new BaseCustomUIPanelPlugin());
		editor.attach(panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		List<BattleNext_QuickBattleModel> entries = (List<BattleNext_QuickBattleModel>) getField(editor, "entries");
		entries.clear();
		entries.add(BattleNext_QuickBattleModel.parseLine("# unfinished", false));
		entries.add(new BattleNext_QuickBattleModel("first", "second", -1, 1f));
		editor.refreshContent();
		List<?> rows = (List<?>) getField(editor, "rows");
		((TextFieldAPI) getField(rows.get(0), "raw")).setText("first,second,4,2");
		((TextFieldAPI) getField(rows.get(1), "map")).setText("pending");
		editor.buttonPressed("add");
		assertEquals(BattleNext_QuickBattleModel.QuickBattleRowType.RAW, entries.get(0).getRowType());
		assertEquals(-1, entries.get(1).getMap());
		assertNotNull(getField(rows.get(0), "raw"));
		assertDoesNotThrow(editor::refreshRowStatuses);
		rows = (List<?>) getField(editor, "rows");
		assertEquals("first", ((TextFieldAPI) getField(rows.get(0), "player")).getText());
		assertEquals("pending", ((TextFieldAPI) getField(rows.get(1), "map")).getText());
	}

	@Test
	void rawPromotionPreservesOtherRowsUnfinishedDraftAndCheckboxes() throws Exception {

		BattleNext_QuickBattleEditor editor = new BattleNext_QuickBattleEditor(() -> {}, new BaseCustomUIPanelPlugin());
		CustomPanelAPI root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());
		editor.attach(root);
		List<BattleNext_QuickBattleModel> entries = (List<BattleNext_QuickBattleModel>) getField(editor, "entries");
		entries.clear();
		entries.add(new BattleNext_QuickBattleModel("first", "second", -1, 1f));
		entries.add(BattleNext_QuickBattleModel.parseLine("# raw", false));
		entries.add(BattleNext_QuickBattleModel.parseLine("# unfinished", false));
		editor.refreshContent();
		List<?> rows = (List<?>) getField(editor, "rows");
		((TextFieldAPI) getField(rows.get(0), "player")).setText("edited_player");
		((TextFieldAPI) getField(rows.get(0), "map")).setText("pending");
		((ButtonAPI) getField(rows.get(0), "enabled")).setChecked(false);
		((TextFieldAPI) getField(rows.get(2), "raw")).setText("# unfinished draft");
		((TextFieldAPI) getField(rows.get(1), "raw")).setText("first,second,4,2");
		editor.refreshRowStatuses();
		rows = (List<?>) getField(editor, "rows");
		assertEquals("edited_player", ((TextFieldAPI) getField(rows.get(0), "player")).getText());
		assertEquals("pending", ((TextFieldAPI) getField(rows.get(0), "map")).getText());
		assertFalse(((ButtonAPI) getField(rows.get(0), "enabled")).isChecked());
		assertEquals("# unfinished draft", ((TextFieldAPI) getField(rows.get(2), "raw")).getText());
		assertEquals("first", ((TextFieldAPI) getField(rows.get(1), "player")).getText());
		assertEquals("4", ((TextFieldAPI) getField(rows.get(1), "map")).getText());
		assertFalse(((ButtonAPI) getField(rows.get(1), "enabled")).isChecked());
		String state = editor.serializeState();
		editor.refreshContent();
		assertEquals(state, editor.serializeState());
		editor.attach(root);
		assertEquals(state, editor.serializeState());
	}

	@Test
	void editorAttachRefreshAndInputLifecycle() throws Exception {
		BattleNext_QuickBattleEditor editor = new BattleNext_QuickBattleEditor(() -> {}, new BaseCustomUIPanelPlugin());
		
		editor.setInputRouter(new BattleNext_UiInputRouter());
		CustomPanelAPI panel = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		editor.attach(panel);
		assertNotNull(editor.getStatusText());
		editor.refreshContent();
	}
}
