package data.scripts.ui.integration;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TextField;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_OptionPickerIntegrationTest extends BattleNext_TestPageFixture {

	@ParameterizedTest
	@ValueSource(ints = {12, 658})
	void popupUsesIndependentHostActualButtonHeightAndBoundaries(int top) throws Exception {
		deferTooltipWidth = true;
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI overlay = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		CustomPanelAPI owner = panel(120f, 30f, new BaseCustomUIPanelPlugin());
		overlay.addComponent(owner).inTL(610f, top);
		TooltipMakerAPI host = owner.createUIElement(120f, 30f, false);
		BattleNext_OptionPicker picker = new BattleNext_OptionPicker(router, overlay, 120f, 30f, "S-mod", 0f,
				optionSource(), BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE, List::of, ids -> {});
		picker.attach(host, owner);
		owner.addUIElement(host).inTL(0f, 0f);
		press(picker, "row", "TOGGLE");
		CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
		assertTrue(children.get(overlay).contains(popup));
		assertFalse(children.get(owner).contains(popup));
		assertEquals(540f, popup.getPosition().getWidth());
		assertEquals(420f, popup.getPosition().getHeight());
		assertEquals(252f, popup.getPosition().getX() - overlay.getPosition().getX());
		float popupTop = overlay.getPosition().getY() + 700f
				- popup.getPosition().getY() - popup.getPosition().getHeight();
		assertEquals(top == 12 ? 44f : 236f, popupTop);
		assertEquals("S-mod", buttonLabels.get("TOGGLE"));
		picker.closeOverlays();
		assertEquals(1, children.get(overlay).size());
		assertFalse(router.getSession().hasInputState());
		assertEquals(1, ((List<?>) getField(picker, "ownedPanels")).size());
		picker.close();
		assertTrue(tooltips.get(host).getComponents().isEmpty());
	}

	@Test
	void repeatedConfirmCancelAndFullCloseReleaseSearchRegistrations() throws Exception {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI overlay = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI host = overlay.createUIElement(32f, 28f, false);
		List<String> draft = new ArrayList<>(List.of("first"));
		BattleNext_OptionPicker picker = new BattleNext_OptionPicker(router, overlay, 32f, 28f, "...", 0f,
				optionSource(), BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE,
				() -> draft, ids -> {
					draft.clear();
					draft.addAll(ids);
				});
		picker.attach(host, overlay);
		overlay.addUIElement(host).inTL(20f, 40f);
		for (int cycle = 0; cycle < 4; cycle++) {
			press(picker, "row", "TOGGLE");
			BattleNext_TextField search = (BattleNext_TextField) getField(picker, "search");
			search.grabFocus(false);
			assertTrue(search.hasFocus());
			assertEquals(1, ((Map<?, ?>) getField(router.getSession(), "REGISTERED")).size());
			Map<String, Integer> selection = (Map<String, Integer>) getField(picker, "selection");
			selection.clear();
			selection.put("second", 1);
			press(picker, "popup", "CANCEL");
			assertEquals(List.of("first"), draft);
			assertFalse(search.hasFocus());
			assertTrue(router.isEmpty());
			assertFalse(router.getSession().hasInputState());
			assertEquals(1, children.get(overlay).size());
			assertEquals(1, ((List<?>) getField(picker, "ownedPanels")).size());
		}
		press(picker, "row", "TOGGLE");
		Map<String, Integer> selection = (Map<String, Integer>) getField(picker, "selection");
		selection.clear();
		selection.put("second", 1);
		press(picker, "popup", "CONFIRM");
		assertEquals(List.of("second"), draft);
		press(picker, "row", "TOGGLE");
		picker.close();
		assertTrue(router.isEmpty());
		assertFalse(router.getSession().hasInputState());
		assertEquals(1, children.get(overlay).size());
		assertTrue(((List<?>) getField(picker, "ownedPanels")).isEmpty());
	}

	@Test
	void scrollingClipsButtonHitsAndClosesPopupWhenRowLeavesViewport() throws Exception {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI overlay = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI viewport = overlay.createUIElement(400f, 100f, true);
		BattleNext_OptionPicker picker = new BattleNext_OptionPicker(router, overlay, 190f, 28f, "Add", 2f,
				optionSource(), BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE, List::of, ids -> {}, viewport);
		viewport.addSpacer(50f);
		picker.attach(viewport, overlay);
		viewport.addSpacer(400f);
		overlay.addUIElement(viewport).inTL(0f, 0f);
		CustomPanelAPI row = (CustomPanelAPI) getField(picker, "row");
		PositionAPI rowPosition = row.getPosition();
		assertEquals(52f, viewport.getPosition().getY() + viewport.getPosition().getHeight()
				- rowPosition.getY() - rowPosition.getHeight());
		assertEquals(190f, rowPosition.getWidth());
		press(picker, "row", "TOGGLE");
		CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
		viewport.getExternalScroller().setYOffset(100f);
		InputEventAPI clippedClick = proxy(InputEventAPI.class, (method, args) -> switch (method) {
			case "isMouseDownEvent" -> true;
			case "getX" -> (int) rowPosition.getCenterX();
			case "getY" -> (int) rowPosition.getCenterY();
			default -> null;
		});
		java.lang.reflect.Method hitTest = BattleNext_OptionPicker.class.getDeclaredMethod("isInsideViewports", InputEventAPI.class);
		hitTest.setAccessible(true);
		assertEquals(false, hitTest.invoke(picker, clippedClick));
		plugins.get(popup).advance(0f);
		assertNull(getField(picker, "popup"));
		plugins.get(row).advance(0f);
		assertFalse(((ButtonAPI) getField(picker, "toggle")).isEnabled());
		viewport.getExternalScroller().setYOffset(0f);
		plugins.get(row).advance(0f);
		assertTrue(((ButtonAPI) getField(picker, "toggle")).isEnabled());
		picker.setEnabled(false);
		plugins.get(row).advance(0f);
		assertFalse(((ButtonAPI) getField(picker, "toggle")).isEnabled());
		picker.close();
	}

	private void press(BattleNext_OptionPicker picker, String panelField, String action) throws Exception {
		CustomPanelAPI target = (CustomPanelAPI) getField(picker, panelField);
		plugins.get(target).buttonPressed(buttonIds.get(action));
	}
}
