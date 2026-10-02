package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class BattleNext_TournamentEditorTest extends BattleNext_TestPageFixture {

	@Test
	void editorAttachRefreshAndInputLifecycle() throws Exception {
		data.scripts.ui.editor.BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(new BattleNext_TournamentStore(), new BattleNext_FleetStore(), new BattleNext_RuleStore());
		editor.open(null);
		editor.setInputRouter(new BattleNext_UiInputRouter());
		CustomPanelAPI panel = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		editor.attach(panel);
		assertNotNull(editor.getStatusText());
		editor.refreshContent();
	}
}
