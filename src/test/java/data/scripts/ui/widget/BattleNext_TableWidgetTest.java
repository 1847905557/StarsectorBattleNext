package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
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
				(index, width, cells, content) -> content.addCustom(panel(width, 42f, root.getPlugin()), 0f),
				(width, content) -> content.addCustom(panel(width, 30f, root.getPlugin()), 5f));
		table.attach(root);
		TooltipState state = tooltips.get(table.getContent());
		assertEquals(count * 42f + 35f, state.getMountedHeight());
		assertEquals(Math.min(222f, state.getMountedHeight() + 10f), state.getViewport().getHeight());
		assertTrue(state.isScrollable());
		state.getScroller().setYOffset(10000f);
		assertEquals(Math.max(0f, state.getMountedHeight() - state.getViewport().getHeight()), state.getScroller().getYOffset());
	}
}
