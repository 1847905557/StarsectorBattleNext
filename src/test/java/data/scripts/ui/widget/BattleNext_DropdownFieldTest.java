package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_DropdownFieldTest extends BattleNext_TestPageFixture {

	@Test
	void dropdownButtonRoutesSelectionAndRepeatedCloseReleasesOnlyPopup() throws Exception {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		CustomPanelAPI buttonPanel = panel(200f, 32f, new BaseCustomUIPanelPlugin());
		root.addComponent(buttonPanel).inTL(50f, 20f);
		List<String> selected = new ArrayList<>();
		BattleNext_DropdownField field = new BattleNext_DropdownField(router, root, 200f, selected::add);
		field.attach(buttonPanel);
		BattleNext_DropdownField.DropdownChoice choice = new BattleNext_DropdownField.DropdownChoice("first", "First");
		field.setOptions(List.of(choice));
		CustomPanelAPI row = (CustomPanelAPI) getField(field, "row");
		for (int cycle = 0; cycle < 4; cycle++) {
			plugins.get(row).buttonPressed(buttonIds.get("TOGGLE"));
			assertTrue(field.isOpen());
			CustomPanelAPI popup = (CustomPanelAPI) getField(field, "popup");
			assertTrue(children.get(root).contains(popup));
			assertEquals(35f, popup.getPosition().getHeight());
			field.closeOverlays();
			assertFalse(field.isOpen());
			assertEquals(1, ((List<?>) getField(field, "ownedPanels")).size());
			assertEquals(1, children.get(root).size());
			assertTrue(router.isEmpty());
		}
		plugins.get(row).buttonPressed(buttonIds.get("TOGGLE"));
		CustomPanelAPI popup = (CustomPanelAPI) getField(field, "popup");
		plugins.get(popup).buttonPressed(choice);
		assertEquals(List.of("first"), selected);
		assertEquals("first", field.getValue());
		assertEquals("First", buttonLabels.get("TOGGLE"));
		field.close();
		assertTrue(children.get(buttonPanel).isEmpty());
		assertTrue(((List<?>) getField(field, "ownedPanels")).isEmpty());
	}
}
