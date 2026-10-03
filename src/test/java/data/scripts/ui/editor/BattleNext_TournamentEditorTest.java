package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_ConfirmationDialog;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.test.fixture.BattleNext_TestPageFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class BattleNext_TournamentEditorTest extends BattleNext_TestPageFixture {

	@Test
	void editorAttachRefreshAndInputLifecycle() throws Exception {
		BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(new BattleNext_TournamentStore(), new BattleNext_FleetStore(), new BattleNext_RuleStore());
		editor.open(null);
		editor.setInputRouter(new BattleNext_UiInputRouter());
		CustomPanelAPI panel = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		editor.attach(panel);
		assertNotNull(editor.getStatusText());
		editor.refreshContent();
	}

	@Test
	void cancelingSaveConfirmationKeepsFleetFieldEditableAndRefreshKeepsDraft() throws Exception {
		deferTooltipWidth = true;
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(new BattleNext_TournamentStore(),
				new BattleNext_FleetStore(), new BattleNext_RuleStore());
		CustomPanelAPI root = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		editor.open(null);
		editor.setInputRouter(router);
		editor.attach(root);
		BattleNext_OptionField picker = (BattleNext_OptionField) getField(editor, "fleetPicker");
		TextFieldAPI fleet = picker.getField();
		fleet.setText("first,second");
		assertEquals(360f, getField(picker, "width"));
		assertSame(root, getField(picker, "overlayHost"));
		editor.closePicker();
		BattleNext_ConfirmationDialog dialog = new BattleNext_ConfirmationDialog(
				new BattleNext_ConfirmationDialog.ConfirmationDialogHost() {
					@Override
					public boolean saveCurrentPage() {
						return editor.save();
					}

					@Override
					public String getSaveError() {
						return editor.getStatusText();
					}

					@Override
					public void discardCurrentPage() {
						editor.discard();
					}
				}, router);
		dialog.attach(root);
		dialog.request(() -> {});
		dialog.handle("confirm_cancel");
		assertSame(fleet, picker.getField());
		fleet.grabFocus(false);
		assertTrue(fleet.hasFocus());
		fleet.setText("second,first");
		editor.refreshContent();
		assertEquals("second,first", ((TextFieldAPI) getField(editor, "fleetField")).getText());
		assertEquals(7, ((Map<?, ?>) getField(router.getSession(), "REGISTERED")).size());
		assertTrue(((List<?>) getField(picker, "ownedPanels")).isEmpty());
		assertTrue(((List<?>) getField(dialog, "ownedPanels")).isEmpty());
		dialog.close();
		assertEquals(7, ((Map<?, ?>) getField(router.getSession(), "REGISTERED")).size());
	}
}
