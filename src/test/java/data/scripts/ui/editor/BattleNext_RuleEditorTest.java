package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.model.rule.BattleNext_RuleStore;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TableWidget;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import java.util.List;
import java.util.Map;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

	@Test
	void restrictionButtonUsesTableWidthTitleAndNestedViewports() throws Exception {
		deferTooltipWidth = true;
		BattleNext_RuleEditor editor = new BattleNext_RuleEditor(new BattleNext_RuleStore());
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		editor.open(null);
		editor.setInputRouter(router);
		editor.attach(root);
		BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(editor, "restrictionPicker");
		BattleNext_TableWidget table = (BattleNext_TableWidget) getField(editor, "tableWidget");
		float rowWidth = table.getContent().getWidthSoFar() - BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH;
		assertEquals(Math.min(190f, rowWidth), getField(picker, "buttonWidth"));
		assertEquals(28f, getField(picker, "buttonHeight"));
		assertEquals("BattleNext_ui_rules_add_restriction", buttonLabels.get("TOGGLE"));
		assertSame(root, getField(picker, "overlayHost"));
		TooltipMakerAPI[] viewports = (TooltipMakerAPI[]) getField(picker, "viewports");
		assertEquals(2, viewports.length);
		assertSame(table.getContent(), viewports[0]);
		((TextFieldAPI) getField(editor, "idField")).setText("rule_draft");
		editor.refreshContent();
		assertEquals("rule_draft", editor.getDraftId());
		assertTrue(((List<?>) getField(picker, "ownedPanels")).isEmpty());
		assertEquals(2, ((Map<?, ?>) getField(router.getSession(), "REGISTERED")).size());
	}
}
