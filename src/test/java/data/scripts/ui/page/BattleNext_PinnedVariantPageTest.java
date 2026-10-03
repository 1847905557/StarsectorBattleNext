package data.scripts.ui.page;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.test.fixture.BattleNext_TestPageFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

class BattleNext_PinnedVariantPageTest extends BattleNext_TestPageFixture {

	@Test
	void attachRefreshAndCloseOwnThePagePanel() throws Exception {
		BattleNext_PinnedVariantPage page = new BattleNext_PinnedVariantPage(() -> {}, Runnable::run, (description, action) -> {});
		CustomPanelAPI root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());

		page.attach(root);
		assertNotNull(getField(page, "panel"));
		assertEquals(1, children.get(root).size());
		page.refreshContent();
		assertEquals(1, children.get(root).size());
		page.close();
		assertEquals(0, children.get(root).size());
	}
}
