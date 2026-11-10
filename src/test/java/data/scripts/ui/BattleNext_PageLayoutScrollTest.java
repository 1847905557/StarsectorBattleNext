package data.scripts.ui;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.test.fixture.BattleNext_TestPageFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BattleNext_PageLayoutScrollTest extends BattleNext_TestPageFixture {

	@ParameterizedTest
	@CsvSource({"1000,300,500,500", "1000,300,900,700", "200,300,700,0",
			"300,300,100,0", "1000,600,500,400", "1000,200,500,500"})
	void restoresOffsetWithinTheMountedContent(float height, float viewportHeight, float offset, float expected) {
		CustomPanelAPI root = panel(400f, viewportHeight, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI content = root.createUIElement(400f, viewportHeight, true);
		content.setHeightSoFar(height);
		root.addUIElement(content).inTL(0f, 0f);

		BattleNext_PageLayout.restoreScrollOffset(content, viewportHeight, offset);

		assertEquals(expected, content.getExternalScroller().getYOffset());
	}

	@Test
	void shrinkingAndReexpandingAtTheBottomUsesTheCurrentContentAndViewport() {
		CustomPanelAPI root = panel(400f, 300f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI content = root.createUIElement(400f, 300f, true);
		content.setHeightSoFar(1200f);
		root.addUIElement(content).inTL(0f, 0f);
		content.getExternalScroller().setYOffset(900f);

		content.setHeightSoFar(500f);
		BattleNext_PageLayout.restoreScrollOffset(content, 300f, content.getExternalScroller().getYOffset());
		assertEquals(200f, content.getExternalScroller().getYOffset());

		content.setHeightSoFar(1200f);
		BattleNext_PageLayout.restoreScrollOffset(content, 300f, content.getExternalScroller().getYOffset());
		assertEquals(200f, content.getExternalScroller().getYOffset());

		content.getPosition().setSize(400f, 1100f);
		BattleNext_PageLayout.restoreScrollOffset(content, 1100f, content.getExternalScroller().getYOffset());
		assertEquals(100f, content.getExternalScroller().getYOffset());
	}
}
