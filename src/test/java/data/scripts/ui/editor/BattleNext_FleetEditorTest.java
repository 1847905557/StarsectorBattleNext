package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.widget.BattleNext_OptionField;
import com.fs.starfarer.api.ui.TextFieldAPI;
import java.util.List;
import java.util.Map;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_FleetEditorTest extends BattleNext_TestPageFixture {

	@Test
	void editorAttachRefreshAndInputLifecycle() throws Exception {
		data.scripts.ui.editor.BattleNext_FleetEditor editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		editor.open(null, false);
		editor.setInputRouter(new BattleNext_UiInputRouter());
		CustomPanelAPI panel = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		editor.attach(panel);
		assertNotNull(editor.getStatusText());
		editor.refreshContent();
	}

	@Test
	void allFiveFleetSelectorsUseExplicitWidthsRootHostAndTheirViewport() throws Exception {
		deferTooltipWidth = true;
		BattleNext_FleetEditor editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		editor.open(null, false);
		editor.setInputRouter(router);
		editor.setOverlayHost(root);
		editor.attach(root);
		((TextFieldAPI) getField(editor, "id")).setText("draft_id");
		editor.buttonPressed("add_member");
		float usableWidth = 1000f - BattleNext_PageLayout.EDITOR_COLUMN_GAP;
		float metaWidth = (float) Math.floor(usableWidth * BattleNext_PageLayout.EDITOR_LEFT_COLUMN_SHARE);
		BattleNext_OptionField skills = (BattleNext_OptionField) getField(editor, "skillsPicker");
		assertEquals(Math.max(72f, metaWidth - 24f), getField(skills, "width"));
		assertSame(root, getField(skills, "overlayHost"));
		List<BattleNext_OptionField> members = List.copyOf((List<BattleNext_OptionField>) getField(editor, "memberPickers"));
		assertEquals(4, members.size());
		for (BattleNext_OptionField member : members) {
			assertEquals(usableWidth - metaWidth - 16f, getField(member, "width"));
			assertSame(root, getField(member, "overlayHost"));
			assertEquals(1, ((com.fs.starfarer.api.ui.TooltipMakerAPI[]) getField(member, "viewports")).length);
		}
		assertEquals(14, ((Map<?, ?>) getField(router.getSession(), "REGISTERED")).size());
		editor.closePicker();
		assertEquals("draft_id", editor.getDraftId());
		editor.refreshContent();
		assertEquals("draft_id", editor.getDraftId());
		assertEquals(14, ((Map<?, ?>) getField(router.getSession(), "REGISTERED")).size());
		assertTrue(((List<?>) getField(skills, "ownedPanels")).isEmpty());
		for (BattleNext_OptionField member : members) {
			assertTrue(((List<?>) getField(member, "ownedPanels")).isEmpty());
		}
	}
}
