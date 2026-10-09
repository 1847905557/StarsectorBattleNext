package data.scripts.ui.page;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import data.scripts.ui.widget.BattleNext_BattleSourceField;
import data.scripts.ui.widget.BattleNext_DropdownField;
import data.test.fixture.BattleNext_TestPageFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BattleNext_BattleSelectorPageTest extends BattleNext_TestPageFixture {

	@Test
	void sourceButtonAndPopupFollowTheCallerPanel() throws Exception {
		BattleNext_BattleSelectorPage page = new BattleNext_BattleSelectorPage(() -> {}, new BaseCustomUIPanelPlugin());
		page.attach(panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		CustomPanelAPI owner = (CustomPanelAPI) getField(page, "panel");
		BattleNext_BattleSourceField source = (BattleNext_BattleSourceField) getField(page, "sourceField");
		BattleNext_DropdownField dropdown = (BattleNext_DropdownField) getField(source, "dropdown");
		CustomPanelAPI row = (CustomPanelAPI) getField(dropdown, "panel");
		CustomPanelAPI sourceContent = (CustomPanelAPI) children.get(source.getPanel()).get(0);
		assertSame(sourceContent.getPosition(), geometry.get(row.getPosition()).getParent());
		assertEquals(583f, row.getPosition().getX() - owner.getPosition().getX(), 0.01f);
		assertEquals(44f, owner.getPosition().getY() + owner.getPosition().getHeight()
				- row.getPosition().getY() - row.getPosition().getHeight(), 0.01f);
		assertEquals(420f, row.getPosition().getWidth());
		assertEquals(30f, row.getPosition().getHeight());

		CustomPanelAPI buttonRow = (CustomPanelAPI) getField(dropdown, "row");
		plugins.get(buttonRow).buttonPressed(((ButtonAPI) getField(dropdown, "button")).getCustomData());
		CustomPanelAPI popup = (CustomPanelAPI) getField(dropdown, "popup");
		assertSame(owner.getPosition(), geometry.get(popup.getPosition()).getParent());
		assertEquals(row.getPosition().getX(), popup.getPosition().getX(), 0.01f);
		assertEquals(420f, popup.getPosition().getWidth(), 0.01f);
		assertTrue(popup.getPosition().getHeight() > 0f);
		dropdown.closeOverlays();
		assertSame(sourceContent.getPosition(), geometry.get(row.getPosition()).getParent());
		dropdown.close();
		assertTrue(children.get(row).isEmpty());
		CustomPanelAPI sourcePanel = source.getPanel();
		source.close();
		assertTrue(children.get(sourcePanel).isEmpty());
	}
}
