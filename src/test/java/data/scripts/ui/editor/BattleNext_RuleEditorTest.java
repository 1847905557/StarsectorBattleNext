package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.model.rule.BattleNext_RuleStore;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class BattleNext_RuleEditorTest extends BattleNext_TestPageFixture {

	@Test
	void editorAttachRefreshAndInputLifecycle() throws Exception {
		data.scripts.ui.editor.BattleNext_RuleEditor editor = new BattleNext_RuleEditor(new BattleNext_RuleStore());
		editor.open(null);
		editor.setInputRouter(new BattleNext_UiInputRouter());
		CustomPanelAPI panel = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		editor.attach(panel);
		assertNotNull(editor.getStatusText());
		editor.refreshContent();
	}
}
