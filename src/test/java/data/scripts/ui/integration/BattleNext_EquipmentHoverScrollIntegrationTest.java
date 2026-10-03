package data.scripts.ui.integration;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_EquipmentTooltip;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_EquipmentHoverScrollIntegrationTest extends BattleNext_TestPageFixture {

	private static final String LONG_PARAGRAPH = "Full description 20%. " + "Complete equipment paragraph. ".repeat(100).strip();

	@ParameterizedTest
	@ValueSource(ints = {320, 800})
	void longDescriptionsScrollWithinTheHostAndKeepTheirHoverAndInputOwnership(int width) throws Exception {

		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI owner = panel(width, 700f, new BaseCustomUIPanelPlugin());
		BattleNext_OptionPicker picker = picker(owner, router, LONG_PARAGRAPH);
		open(picker);
		Object target = ((List<?>) getField(picker, "hoverTargets")).get(0);
		show(picker, target);
		CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
		CustomPanelAPI hover = (CustomPanelAPI) getField(picker, "hoverPanel");
		TooltipMakerAPI content = (TooltipMakerAPI) getField(picker, "hoverContent");
		assertEquals(Math.min(360f, popup.getPosition().getWidth() - 16f), hover.getPosition().getWidth());
		assertEquals(240f, hover.getPosition().getHeight());
		assertTrue(tooltips.get(content).isScrollable());
		assertTrue(content.getHeightSoFar() > content.getExternalScroller().getPosition().getHeight());
		assertTrue(labels.contains(LONG_PARAGRAPH));
		assertTrue(hover.getPosition().getX() >= popup.getPosition().getX() + 8f);
		assertTrue(hover.getPosition().getX() + hover.getPosition().getWidth()
				<= popup.getPosition().getX() + popup.getPosition().getWidth() - 8f);
		assertTrue(hover.getPosition().getY() >= popup.getPosition().getY() + 8f);
		assertTrue(hover.getPosition().getY() + hover.getPosition().getHeight()
				<= popup.getPosition().getY() + popup.getPosition().getHeight() - 8f);
		var grid = (com.fs.starfarer.api.ui.ScrollPanelAPI) getField(picker, "gridScroller");
		float beforeGrid = grid.getYOffset();
		for (int cycle = 0; cycle < 3; cycle++) {
			move(picker, hover.getPosition().getCenterX(), hover.getPosition().getCenterY());
			assertSame(hover, getField(picker, "hoverPanel"));
			AtomicBoolean consumed = new AtomicBoolean();
			InputEventAPI wheel = event(hover, "wheel", -120, consumed);
			picker.handleGlobalInput(List.of(wheel));
			assertTrue(consumed.get());
			assertEquals((cycle + 1) * 40f, content.getExternalScroller().getYOffset());
			assertEquals(beforeGrid, grid.getYOffset());
			assertSame(hover, getField(picker, "hoverPanel"));
		}
		for (int cycle = 0; cycle < 50; cycle++) {
			picker.handleGlobalInput(List.of(event(hover, "wheel", -120, new AtomicBoolean())));
		}
		assertEquals(content.getHeightSoFar() - content.getExternalScroller().getPosition().getHeight(),
				content.getExternalScroller().getYOffset());
		for (int cycle = 0; cycle < 50; cycle++) {
			picker.handleGlobalInput(List.of(event(hover, "wheel", 120, new AtomicBoolean())));
		}
		assertEquals(0f, content.getExternalScroller().getYOffset());
		picker.closeOverlays();
		assertNull(getField(picker, "hoverPanel"));
		assertNull(getField(picker, "hoverContent"));
		assertTrue(router.isEmpty());
		assertTrue(((Map<?, ?>) getField(router.getSession(), "REGISTERED")).isEmpty());
		picker.close();
		assertTrue(((List<?>) getField(picker, "ownedPanels")).isEmpty());
	}

	@Test
	void movingAcrossTheGapAndHoveringOverOtherTilesKeepsTheReaderAndBlocksClickThrough() throws Exception {

		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		var owner = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		var picker = picker(owner, router, LONG_PARAGRAPH);
		open(picker);
		Object target = ((List<?>) getField(picker, "hoverTargets")).get(0);
		show(picker, target);
		CustomPanelAPI tile = (CustomPanelAPI) getField(target, "tile");
		CustomPanelAPI hover = (CustomPanelAPI) getField(picker, "hoverPanel");
		float gap = (tile.getPosition().getX() + tile.getPosition().getWidth() + hover.getPosition().getX()) * 0.5f;
		move(picker, gap, tile.getPosition().getCenterY());
		assertSame(hover, getField(picker, "hoverPanel"));
		move(picker, hover.getPosition().getCenterX(), hover.getPosition().getCenterY());
		assertSame(hover, getField(picker, "hoverPanel"));
		CustomPanelAPI covered = null;
		for (Object candidate : (List<?>) getField(picker, "hoverTargets")) {
			var panel = (CustomPanelAPI) getField(candidate, "tile");
			AtomicBoolean consumed = new AtomicBoolean();
			var click = proxy(InputEventAPI.class, (method, arguments) -> switch (method) {
				case "isConsumed" -> consumed.get();
				case "isLMBDownEvent" -> true;
				case "getX" -> (int) panel.getPosition().getCenterX();
				case "getY" -> (int) panel.getPosition().getCenterY();
				case "consume" -> { consumed.set(true); yield null; }
				default -> null;
			});
			if (!hover.getPosition().containsEvent(click)) continue;
			covered = panel;
			plugins.get(panel).processInput(List.of(click));
			break;
		}
		assertNotNull(covered);
		assertTrue(((Map<?, ?>) getField(picker, "selection")).isEmpty());
		var popup = (CustomPanelAPI) getField(picker, "popup");
		move(picker, popup.getPosition().getX() - 1f, popup.getPosition().getY() - 1f);
		assertNull(getField(picker, "hoverPanel"));
		picker.close();
		assertTrue(router.isEmpty());
	}

	@Test
	void shortTooltipsFitTheirContentAndRepeatedHoveringAndClosingReleaseTheOldPanels() throws Exception {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		var owner = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		var picker = picker(owner, router, "Short paragraph.");
		for (int cycle = 0; cycle < 3; cycle++) {
			open(picker);
			List<?> targets = (List<?>) getField(picker, "hoverTargets");
			show(picker, targets.get(0));
			var first = (CustomPanelAPI) getField(picker, "hoverPanel");
			var content = (TooltipMakerAPI) getField(picker, "hoverContent");
			assertTrue(first.getPosition().getHeight() < 240f);
			assertEquals(content.getHeightSoFar() + 12f, first.getPosition().getHeight());
			show(picker, targets.get(1));
			assertTrue(removedComponents.contains(first));
			picker.closeOverlays();
			assertNull(getField(picker, "hoverContent"));
			assertTrue(router.isEmpty());
			assertTrue(((Map<?, ?>) getField(router.getSession(), "REGISTERED")).isEmpty());
		}
		picker.close();
		assertTrue(((List<?>) getField(picker, "ownedPanels")).isEmpty());
	}

	private BattleNext_OptionPicker picker(CustomPanelAPI owner, BattleNext_UiInputRouter router, String text) {
		var source = new BattleNext_OptionPicker.OptionChoiceSource() {
			@Override
			public List<BattleNext_OptionPicker.OptionChoice> getOptions() {
				List<BattleNext_OptionPicker.OptionChoice> result = new ArrayList<>();
				for (int index = 0; index < 30; index++) {
					result.add(new BattleNext_OptionPicker.OptionChoice("id_" + index, "Name " + index));
				}
				return result;
			}

			@Override
			public int getMaxCount(String id) {
				return 1;
			}

			@Override
			public String getIconPath(String id) {
				return null;
			}

			@Override
			public float getTooltipWidth() {
				return BattleNext_EquipmentTooltip.WIDTH;
			}

			@Override
			public boolean isTooltipScrollable() {
				return true;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, BattleNext_OptionPicker.OptionChoice option) {
				BattleNext_EquipmentTooltip.render(tooltip,
						new BattleNext_EquipmentTooltip.Content(option.getLabel(), option.getId(), text, List.of()));
			}
		};
		var host = owner.createUIElement(80f, 28f, false);
		var picker = new BattleNext_OptionPicker(router, owner, 80f, 28f, "...", 0f, source,
				BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE, List::of, ids -> {});
		picker.attach(host, owner);
		owner.addUIElement(host).inTL(20f, 20f);
		return picker;
	}

	private void open(BattleNext_OptionPicker picker) throws Exception {
		var method = picker.getClass().getDeclaredMethod("open");
		method.setAccessible(true);
		method.invoke(picker);
	}

	private void show(BattleNext_OptionPicker picker, Object target) throws Exception {
		var method = picker.getClass().getDeclaredMethod("showHover", target.getClass());
		method.setAccessible(true);
		method.invoke(picker, target);
	}

	private void move(BattleNext_OptionPicker picker, float horizontal, float vertical) throws Exception {
		var method = picker.getClass().getDeclaredMethod("updateHover", float.class, float.class);
		method.setAccessible(true);
		method.invoke(picker, horizontal, vertical);
	}

	private InputEventAPI event(CustomPanelAPI hover, String kind, int value, AtomicBoolean consumed) {
		return proxy(InputEventAPI.class, (method, arguments) -> switch (method) {
			case "isConsumed" -> consumed.get();
			case "isMouseScrollEvent" -> kind.equals("wheel");
			case "getEventValue" -> value;
			case "getX" -> (int) hover.getPosition().getCenterX();
			case "getY" -> (int) hover.getPosition().getCenterY();
			case "consume" -> { consumed.set(true); yield null; }
			default -> null;
		});
	}
}
