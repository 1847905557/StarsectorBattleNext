package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.test.fixture.BattleNext_TestPageFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

class BattleNext_DropdownFieldTest extends BattleNext_TestPageFixture {

	@ParameterizedTest
	@ValueSource(ints = {180, 400})
	void dropdownUsesTheCompleteHostDuringSelectionRefreshAndRepeatedClose(int width) throws Exception {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		CustomPanelAPI buttonPanel = panel(width, 32f, new BaseCustomUIPanelPlugin());
		root.addComponent(buttonPanel).inTL(50f, 20f);
		List<String> selected = new ArrayList<>();
		BattleNext_DropdownField field = new BattleNext_DropdownField(router, root, selected::add);
		field.attach(buttonPanel);
		BattleNext_DropdownField.DropdownChoice choice = new BattleNext_DropdownField.DropdownChoice("first", "First");
		field.setOptions(List.of(choice));
		CustomPanelAPI row = (CustomPanelAPI) getField(field, "row");
		assertEquals(buttonPanel.getPosition().getX(), row.getPosition().getX());
		assertEquals(buttonPanel.getPosition().getY(), row.getPosition().getY());
		assertEquals(buttonPanel.getPosition().getWidth(), row.getPosition().getWidth());
		assertEquals(buttonPanel.getPosition().getHeight(), row.getPosition().getHeight());
		for (int cycle = 0; cycle < 4; cycle++) {
			plugins.get(row).buttonPressed(buttonIds.get("TOGGLE"));
			assertTrue(field.isOpen());
			CustomPanelAPI popup = (CustomPanelAPI) getField(field, "popup");
			assertTrue(children.get(root).contains(popup));
			assertEquals(35f, popup.getPosition().getHeight());
			assertEquals(buttonPanel.getPosition().getWidth(), popup.getPosition().getWidth());
			field.closeOverlays();
			assertFalse(field.isOpen());
			assertEquals(1, children.get(root).size());
			assertTrue(router.isEmpty());
		}
		buttonPanel.getPosition().setSize(width + 40f, 40f);
		field.refreshContent();
		row = (CustomPanelAPI) getField(field, "row");
		assertEquals(buttonPanel.getPosition().getWidth(), row.getPosition().getWidth());
		assertEquals(buttonPanel.getPosition().getHeight(), row.getPosition().getHeight());
		plugins.get(row).buttonPressed(buttonIds.get("TOGGLE"));
		CustomPanelAPI popup = (CustomPanelAPI) getField(field, "popup");
		assertEquals(row.getPosition().getWidth(), popup.getPosition().getWidth());
		plugins.get(popup).buttonPressed(choice);
		assertEquals(List.of("first"), selected);
		assertEquals("first", field.getValue());
		assertEquals("First", buttonLabels.get("TOGGLE"));
		field.close();
		assertTrue(children.get(buttonPanel).isEmpty());
		assertTrue(((List<?>) getField(field, "ownedPanels")).isEmpty());
	}
}
