package data.scripts.ui.page;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.scripts.ui.widget.BattleNext_DropdownField;
import data.scripts.ui.page.BattleNext_BattleSelectorPage;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_BattleSelectorPageTest extends BattleNext_TestPageFixture {

	@Test
	void sourceButtonAndPopupFollowTheCallerPanel() throws Exception {
		BattleNext_BattleSelectorPage page = new BattleNext_BattleSelectorPage(() -> {}, new BaseCustomUIPanelPlugin());
		page.attach(panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		CustomPanelAPI owner = (CustomPanelAPI) getField(page, "panel");
		BattleNext_DropdownField dropdown = (BattleNext_DropdownField) getField(page, "sourceDropdown");
		CustomPanelAPI row = (CustomPanelAPI) getField(dropdown, "panel");
		assertSame(owner.getPosition(), geometry.get(row.getPosition()).getParent());
		assertEquals(583f, row.getPosition().getX() - owner.getPosition().getX(), 0.01f);
		assertEquals(44f, owner.getPosition().getY() + owner.getPosition().getHeight()
				- row.getPosition().getY() - row.getPosition().getHeight(), 0.01f);
		assertEquals(420f, row.getPosition().getWidth());
		assertEquals(30f, row.getPosition().getHeight());

		CustomPanelAPI buttonRow = (CustomPanelAPI) getField(dropdown, "row");
		plugins.get(buttonRow).buttonPressed(buttonIds.get("TOGGLE"));
		CustomPanelAPI popup = (CustomPanelAPI) getField(dropdown, "popup");
		assertSame(owner.getPosition(), geometry.get(popup.getPosition()).getParent());
		assertEquals(row.getPosition().getX(), popup.getPosition().getX(), 0.01f);
		assertEquals(420f, popup.getPosition().getWidth(), 0.01f);
		assertTrue(popup.getPosition().getHeight() > 0f);
		dropdown.closeOverlays();
		assertSame(owner.getPosition(), geometry.get(row.getPosition()).getParent());
		dropdown.close();
		assertTrue(children.get(owner).contains(row));
		assertTrue(children.get(row).isEmpty());
	}

	@Test
	void attachRefreshAndCloseOwnThePagePanel() throws Exception {
		BattleNext_BattleSelectorPage page = new BattleNext_BattleSelectorPage(() -> {}, new BaseCustomUIPanelPlugin());
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
