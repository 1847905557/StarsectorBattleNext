package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattleNext_StatusBarTest extends BattleNext_TestPageFixture {

	@Test
	void statusUsesExplicitDimensionsDuringDeferredGeometry() throws Exception {
		deferTooltipWidth = true;
		CustomPanelAPI owner = panel(800f, 600f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI host = owner.createUIElement(260f, 30f, false);
		BattleNext_StatusBar bar = new BattleNext_StatusBar(260f, 30f);
		bar.attach(host, owner);
		assertEquals(0f, host.getPosition().getWidth());
		assertEquals(250f, getField(bar, "textWidth"));
		assertEquals(30f, tooltips.get(host).getUsedHeight());
		bar.showError("error");
		assertEquals("error", ((com.fs.starfarer.api.ui.LabelAPI) getField(bar, "statusLabel")).getText());
	}
}
