package data.scripts.ui.integration;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TextField;
import data.test.fixture.BattleNext_TestPageFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class BattleNext_OptionPickerIntegrationTest extends BattleNext_TestPageFixture {

	@ParameterizedTest
	@CsvSource({"160,true", "240,true", "320,true", "540,false", "540,true"})
	void searchRowUsesGridColumnBoundariesThroughFilteringScrollingAndReopening(int width, boolean deferred) throws Exception {

		deferTooltipWidth = deferred;
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI overlay = panel(width, 700f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI host = overlay.createUIElement(32f, 28f, false);
		BattleNext_OptionPicker.OptionChoiceSource source = new BattleNext_OptionPicker.OptionChoiceSource() {
			@Override
			public List<BattleNext_OptionPicker.OptionChoice> getOptions() {
				List<BattleNext_OptionPicker.OptionChoice> choices = new ArrayList<>();
				for (int index = 0; index < 30; index++) {
					choices.add(new BattleNext_OptionPicker.OptionChoice("id_" + index, "Option " + index));
				}
				return choices;
			}
			@Override
			public int getMaxStack(String id) {
				return 1;
			}
			@Override
			public String getIconPath(String id) {
				return null;
			}
			@Override
			public void createTooltip(TooltipMakerAPI tooltip, BattleNext_OptionPicker.OptionChoice option) {
				tooltip.addTitle(option.getLabel());
			}
		};
		BattleNext_OptionPicker picker = new BattleNext_OptionPicker(router, overlay, 32f, 28f, "...", 0f,
				source, BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE, List::of, ids -> {});
		picker.attach(host, overlay);
		overlay.addUIElement(host).inTL(20f, 40f);
		for (int cycle = 0; cycle < 2; cycle++) {
			press(picker, "row", "TOGGLE");
			assertSearchGridAlignment(picker);
			BattleNext_TextField search = (BattleNext_TextField) getField(picker, "search");
			float fieldX = search.getPosition().getX();
			float fieldWidth = search.getPosition().getWidth();
			search.setText("Option 1");
			CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
			plugins.get(popup).advance(0f);
			assertSearchGridAlignment(picker);
			CustomPanelAPI gridPanel = (CustomPanelAPI) getField(picker, "gridPanel");
			TooltipMakerAPI grid = (TooltipMakerAPI) children.get(gridPanel).get(0);
			grid.getExternalScroller().setYOffset(40f);
			assertSearchGridAlignment(picker);
			search.setText("unmatched");
			plugins.get(popup).advance(0f);
			assertEquals(fieldX, search.getPosition().getX(), 0.001f);
			assertEquals(fieldWidth, search.getPosition().getWidth(), 0.001f);
			search.setText("");
			plugins.get(popup).advance(0f);
			assertSearchGridAlignment(picker);
			search.grabFocus(false);
			assertTrue(search.hasFocus());
			press(picker, "popup", "CANCEL");
			assertFalse(router.getSession().hasInputState());
		}
		picker.close();
		assertTrue(((List<?>) getField(picker, "ownedPanels")).isEmpty());
	}

	private void assertSearchGridAlignment(BattleNext_OptionPicker picker) throws Exception {

		CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
		CustomPanelAPI gridPanel = (CustomPanelAPI) getField(picker, "gridPanel");
		TooltipMakerAPI grid = (TooltipMakerAPI) children.get(gridPanel).get(0);
		List<UIComponentAPI> tiles = tooltips.get(grid).getComponents();
		PositionAPI first = tiles.get(0).getPosition();
		float right = (float) tiles.stream().mapToDouble(tile -> tile.getPosition().getX()
				+ tile.getPosition().getWidth()).max().orElseThrow();
		LabelAPI label = children.get(popup).stream().filter(child -> child instanceof TooltipMakerAPI)
				.flatMap(child -> tooltips.get(child).getComponents().stream())
				.filter(child -> child instanceof LabelAPI).map(child -> (LabelAPI) child)
				.filter(child -> child.getText().equals(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_picker_search"))).findFirst().orElseThrow();
		PositionAPI field = ((BattleNext_TextField) getField(picker, "search")).getPosition();
		assertEquals(16f, first.getX() - popup.getPosition().getX(), 0.001f);
		assertEquals(first.getX(), label.getPosition().getX(), 0.001f);
		assertEquals(right, field.getX() + field.getWidth(), 0.001f);
		assertEquals(Math.min(80f, (right - first.getX()) * 0.5f), field.getX() - first.getX(), 0.001f);
		assertEquals(28f, field.getHeight(), 0.001f);
		assertEquals(10f, popup.getPosition().getY() + popup.getPosition().getHeight()
				- field.getY() - field.getHeight(), 0.001f);
		assertTrue(field.getWidth() > 0f);
		assertTrue(right <= popup.getPosition().getX() + popup.getPosition().getWidth() - 8f);
	}

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
			assertEquals(1, ((Map<?, ?>) getField(router.getSession(), "registrations")).size());
			Map<String, Integer> selection = (Map<String, Integer>) getField(picker, "selection");
			selection.clear();
			selection.put("second", 1);
			press(picker, "popup", "CANCEL");
			assertEquals(List.of("first"), draft);
			assertFalse(search.hasFocus());
			assertTrue(router.isEmpty());
			assertFalse(router.getSession().hasInputState());
			assertEquals(1, children.get(overlay).size());
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
		Method hitTest = BattleNext_OptionPicker.class.getDeclaredMethod("isInsideViewports", InputEventAPI.class);
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

	@Test
	void bothModesCycleThroughProviderStackAndConfirmAfterReleasingThePopup() throws Exception {
		for (BattleNext_OptionPicker.OptionSelectionMode mode : BattleNext_OptionPicker.OptionSelectionMode.values()) {
			BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
			CustomPanelAPI root = panel(800f, 700f, new BaseCustomUIPanelPlugin());
			List<String> draft = new ArrayList<>(List.of("first"));
			BattleNext_OptionPicker.OptionChoiceSource source = new BattleNext_OptionPicker.OptionChoiceSource() {
				@Override
				public List<BattleNext_OptionPicker.OptionChoice> getOptions() {
					return List.of(new BattleNext_OptionPicker.OptionChoice("first", "First"), new BattleNext_OptionPicker.OptionChoice("second", "Second"));
				}
				@Override
				public int getMaxStack(String id) {
					return id.equals("second") ? 2 : 1;
				}
				@Override
				public String getIconPath(String id) {
					return "";
				}
				@Override
				public void createTooltip(TooltipMakerAPI tooltip, BattleNext_OptionPicker.OptionChoice option) {
					tooltip.addTitle(option.getLabel());
				}
			};
			BattleNext_OptionPicker picker = new BattleNext_OptionPicker(router, root, source, mode, () -> draft, ids -> {
				assertTrue(router.isEmpty());
				draft.clear();
				draft.addAll(ids);
			});
			picker.openAt(root, () -> new BattleNext_OptionPicker.Anchor(10f, 600f, 100f, 18f));
			CustomPanelAPI gridPanel = (CustomPanelAPI) getField(picker, "gridPanel");
			TooltipMakerAPI grid = (TooltipMakerAPI) children.get(gridPanel).get(0);
			CustomPanelAPI first = (CustomPanelAPI) tooltips.get(grid).getComponents().get(0);
			CustomPanelAPI second = (CustomPanelAPI) tooltips.get(grid).getComponents().get(1);
			Map<String, Integer> selection = (Map<String, Integer>) getField(picker, "selection");
			clickTile(first);
			assertTrue(selection.isEmpty());
			clickTile(first);
			for (int count : new int[]{1, 2, 0}) {
				clickTile(second);
				assertEquals(count, selection.getOrDefault("second", 0));
				assertEquals(mode == BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE, selection.containsKey("first"));
			}
			press(picker, "popup", "CONFIRM");
			assertEquals(mode == BattleNext_OptionPicker.OptionSelectionMode.SINGLE ? List.of() : List.of("first"), draft);
			picker.openAt(root, () -> new BattleNext_OptionPicker.Anchor(10f, 600f, 100f, 18f));
			gridPanel = (CustomPanelAPI) getField(picker, "gridPanel");
			grid = (TooltipMakerAPI) children.get(gridPanel).get(0);
			second = (CustomPanelAPI) tooltips.get(grid).getComponents().get(1);
			clickTile(second);
			clickTile(second);
			press(picker, "popup", "CONFIRM");
			assertEquals(mode == BattleNext_OptionPicker.OptionSelectionMode.SINGLE
					? List.of("second", "second") : List.of("first", "second", "second"), draft);
			picker.openAt(root, () -> new BattleNext_OptionPicker.Anchor(10f, 600f, 100f, 18f));
			selection = (Map<String, Integer>) getField(picker, "selection");
			assertEquals(2, selection.get("second"));
			picker.close();
			assertFalse(router.getSession().hasInputState());
			assertTrue(children.get(root).isEmpty());
		}
	}

	private void clickTile(CustomPanelAPI tile) {
		AtomicBoolean consumed = new AtomicBoolean();
		InputEventAPI event = proxy(InputEventAPI.class, (method, arguments) -> switch (method) {
			case "isLMBDownEvent" -> true;
			case "getX" -> (int) tile.getPosition().getCenterX();
			case "getY" -> (int) tile.getPosition().getCenterY();
			case "isConsumed" -> consumed.get();
			case "consume" -> {
				consumed.set(true);
				yield null;
			}
			default -> null;
		});
		plugins.get(tile).processInput(List.of(event));
		assertTrue(consumed.get());
	}
}
