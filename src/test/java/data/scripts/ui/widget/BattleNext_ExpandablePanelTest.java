package data.scripts.ui.widget;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_ExpandablePanelTest {
	private static final Color PANEL = new Color(40, 80, 120);

	@Test
	void commonGeometryKeepsImagesTextAndMetricsInBounds() {
		assertEquals(40f, BattleNext_ExpandablePanel.HEADER_HEIGHT);
		assertEquals(32f, BattleNext_ExpandablePanel.ICON_SIZE);
		assertEquals(4f, BattleNext_ExpandablePanel.ROW_GAP);
		assertEquals(40f, BattleNext_ExpandablePanel.panelHeight(false, 200f));
		assertEquals(240f, BattleNext_ExpandablePanel.panelHeight(true, 200f));
		BattleNext_ExpandablePanel.HeaderLayout fleet =
				BattleNext_ExpandablePanel.headerLayout(400f, true, false);
		assertEquals(84f, fleet.textX());
		assertEquals(0f, fleet.metricsWidth());
		assertTrue(fleet.textX() + fleet.textWidth() <= 400f);
		BattleNext_ExpandablePanel.HeaderLayout withoutPortrait =
				BattleNext_ExpandablePanel.headerLayout(400f, false, false);
		assertEquals(48f, withoutPortrait.textX());
		assertEquals(8f, withoutPortrait.textX() - BattleNext_ExpandablePanel.HEADER_SIDE_INSET
				- BattleNext_ExpandablePanel.ICON_SIZE);
		assertEquals(8f, fleet.textX() - BattleNext_ExpandablePanel.HEADER_SIDE_INSET
				- (BattleNext_ExpandablePanel.ICON_SIZE * 2f + 4f));
		assertEquals(392f, fleet.textX() + fleet.textWidth());
		assertEquals(392f, withoutPortrait.textX() + withoutPortrait.textWidth());
		BattleNext_ExpandablePanel.HeaderLayout report =
				BattleNext_ExpandablePanel.headerLayout(1200f, false, true);
		assertEquals(48f, report.textX());
		assertEquals(260f, report.metricsWidth());
		assertEquals(932f, report.metricsX());
		assertTrue(report.textX() + report.textWidth() < report.metricsX());
	}

	@Test
	void focusedColorAndMissingImageSlotsFollowSharedStyle() {
		assertEquals(PANEL, BattleNext_ExpandablePanel.headerBackground(true, PANEL));
		assertEquals(PANEL.darker(), BattleNext_ExpandablePanel.headerBackground(false, PANEL));
	}

	@Test
	void headerConsumesOnlyInsideClickAndBodyForwardsButton() {
		AtomicInteger toggles = new AtomicInteger();
		AtomicReference<InputEventAPI> insideEvent = new AtomicReference<>();
		PositionAPI position = proxy(PositionAPI.class, (name, args) ->
				"containsEvent".equals(name) && args[0] == insideEvent.get());
		PositionAPI hidden = proxy(PositionAPI.class, (name, args) -> false);
		AtomicBoolean consumed = new AtomicBoolean();
		InputEventAPI outside = event(true, consumed);
		StringBuilder order = new StringBuilder();
		assertFalse(BattleNext_ExpandablePanel.handleHeaderInput(List.of(outside), position, position,
				() -> order.append('S'), () -> { order.append('T'); toggles.incrementAndGet(); }));
		assertEquals(0, toggles.get());
		InputEventAPI inside = event(true, consumed);
		insideEvent.set(inside);
		assertFalse(BattleNext_ExpandablePanel.handleHeaderInput(List.of(inside), position, hidden,
				() -> order.append('S'), () -> { order.append('T'); toggles.incrementAndGet(); }));
		assertTrue(BattleNext_ExpandablePanel.handleHeaderInput(List.of(inside), position, position,
				() -> order.append('S'), () -> { order.append('T'); toggles.incrementAndGet(); }));
		assertTrue(consumed.get());
		assertEquals(1, toggles.get());
		assertEquals("ST", order.toString());
		AtomicReference<Object> action = new AtomicReference<>();
		new BattleNext_ExpandablePanel.BodyPlugin(action::set).buttonPressed("remove_member:2");
		assertEquals("remove_member:2", action.get());
	}

	@Test
	void hoverSoundsOnlyOnEntryAndVisibleHitTestUsesScreenCoordinates() {
		assertTrue(BattleNext_ExpandablePanel.hoverEntered(false, true));
		assertFalse(BattleNext_ExpandablePanel.hoverEntered(true, true));
		assertFalse(BattleNext_ExpandablePanel.hoverEntered(true, false));
		assertTrue(BattleNext_ExpandablePanel.hoverEntered(false, true));
		PositionAPI bounds = proxy(PositionAPI.class, (name, args) -> switch (name) {
			case "getX" -> 10f;
			case "getY" -> 20f;
			case "getWidth" -> 100f;
			case "getHeight" -> 40f;
			default -> null;
		});
		assertTrue(BattleNext_ExpandablePanel.contains(bounds, 10f, 20f));
		assertTrue(BattleNext_ExpandablePanel.contains(bounds, 109f, 59f));
		assertFalse(BattleNext_ExpandablePanel.contains(bounds, 110f, 59f));
		assertFalse(BattleNext_ExpandablePanel.contains(bounds, 109f, 60f));
	}

	private InputEventAPI event(boolean inside, AtomicBoolean consumed) {
		return proxy(InputEventAPI.class, (name, args) -> switch (name) {
			case "isConsumed" -> consumed.get();
			case "isLMBDownEvent" -> inside;
			case "consume" -> {
				consumed.set(true);
				yield null;
			}
			default -> null;
		});
	}

	private interface Call {
		Object invoke(String name, Object[] args);
	}

	private <T> T proxy(Class<T> type, Call call) {
		return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
				(instance, method, args) -> {
					Object value = call.invoke(method.getName(), args);
					if (value == null && method.getReturnType() == boolean.class) return false;
					return value;
				}));
	}
}
