package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.UIComponentAPI;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.test.fixture.BattleNext_TestUiFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BattleNext_TableWidgetTest extends BattleNext_TestUiFixture {

	@ParameterizedTest
	@ValueSource(ints = {0, 20})
	void tablesMeasureRowsAndAdditionalContentBeforeMount(int count) {
		CustomPanelAPI root = panel(600f, 240f, new BaseCustomUIPanelPlugin());
		BattleNext_TableWidget table = new BattleNext_TableWidget(
				root.getPlugin(), new String[]{"Name"}, new float[]{1f}, count,
				(index, width, cells, content) -> {
					assertEquals(root.getPosition().getWidth(), width);
					content.addCustom(panel(width, 42f, root.getPlugin()), 0f);
				},
				(width, content) -> content.addCustom(panel(width, 30f, root.getPlugin()), 5f));
		table.attach(root);
		assertEquals(root, table.getPanel());
		TooltipMakerAPI header = (TooltipMakerAPI) children.get(root).get(0);
		assertEquals(root.getPosition().getX(), header.getPosition().getX());
		assertEquals(root.getPosition().getWidth(), tooltips.get(header).getViewport().getWidth());
		TooltipState state = tooltips.get(table.getContent());
		assertEquals(count * 42f + 35f, state.getMountedHeight());
		assertEquals(Math.min(222f, state.getMountedHeight() + 10f), state.getViewport().getHeight());
		assertTrue(state.isScrollable());
		state.getScroller().setYOffset(10000f);
		assertEquals(Math.max(0f, state.getMountedHeight() - state.getViewport().getHeight()), state.getScroller().getYOffset());
	}

	@ParameterizedTest
	@ValueSource(ints = {180, 1400})
	void sectionHeadingReturnsItsLabelAndRefreshUsesThatLabelAcrossSiblingChanges(int width) {
		deferTooltipWidth = true;
		shortenLabels = true;
		CustomPanelAPI owner = panel(width, 200f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI content = owner.createUIElement(width, 100f, false);
		LabelAPI heading = content.addSectionHeading("Heading", BattleNext_UiTheme.TEXT,
				BattleNext_UiTheme.PANEL, Alignment.MID, 0f);
		UIComponentAPI anchor = content.getPrev();
		assertTrue(anchor != heading);
		assertTrue(!(anchor instanceof LabelAPI));
		assertEquals(content.getWidthSoFar(), anchor.getPosition().getWidth());
		assertEquals(anchor.getPosition().getWidth() + 10f, heading.getPosition().getWidth());
		LabelAPI following = content.addPara("Following paragraph", 5f);
		assertEquals(anchor.getPosition().getX(), following.getPosition().getX());
		assertEquals(anchor.getPosition().getY() - following.getPosition().getHeight() - 5f,
				following.getPosition().getY());
		assertEquals(following, content.getPrev());
		for (String title : new String[]{"Management heading with a long title 50%", "管理窗口标题与中文文本 50%"}) {
			BattleNext_PageLayout.refreshHeading(content, heading, width, title);
			assertEquals(content.shortenString(title, content.getWidthSoFar()), heading.getText());
			assertEquals("Following paragraph", following.getText());
		}
		owner.addUIElement(content).inTL(0f, 0f);
	}
}
