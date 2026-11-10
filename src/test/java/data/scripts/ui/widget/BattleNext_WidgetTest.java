package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.test.fixture.BattleNext_TestPageFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

class BattleNext_WidgetTest extends BattleNext_TestPageFixture {

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void bothHostsDispatchInputAndCloseOverlaysBeforeContentAndOwnedPanels(boolean tooltipHost) {
		List<String> calls = new ArrayList<>();
		List<Integer> childrenDuringClear = new ArrayList<>();
		CustomPanelAPI root = panel(400f, 300f, new BaseCustomUIPanelPlugin());
		BattleNext_Widget widget;
		if (tooltipHost) {
			BattleNext_TooltipBasedWidget tooltip = new BattleNext_TooltipBasedWidget() {
				@Override
				protected void buildContent(TooltipMakerAPI host, CustomPanelAPI owner) {
					CustomPanelAPI child = ownPanel(owner, owner.createCustomPanel(100f, 50f, null));
					owner.addComponent(child).inTL(0f, 0f);
				}

				@Override
				protected void clearOverlays() {
					calls.add("overlays");
				}

				@Override
				protected void clearContent() {
					calls.add("content");
					childrenDuringClear.add(children.getOrDefault(root, List.of()).size());
				}

				@Override
				protected boolean handleWidgetInput(List<InputEventAPI> events) {
					calls.add("input");
					return true;
				}
			};
			tooltip.attach(root.createUIElement(200f, 100f, false), root);
			widget = tooltip;
		} else {
			BattleNext_PanelBasedWidget panel = new BattleNext_PanelBasedWidget() {
				@Override
				protected void buildContent(CustomPanelAPI owner) {
					CustomPanelAPI child = ownPanel(owner, owner.createCustomPanel(100f, 50f, null));
					owner.addComponent(child).inTL(0f, 0f);
				}

				@Override
				protected void clearOverlays() {
					calls.add("overlays");
				}

				@Override
				protected void clearContent() {
					calls.add("content");
					childrenDuringClear.add(children.getOrDefault(root, List.of()).size());
				}

				@Override
				protected boolean handleWidgetInput(List<InputEventAPI> events) {
					calls.add("input");
					return true;
				}
			};
			panel.attach(root);
			widget = panel;
		}
		calls.clear();
		childrenDuringClear.clear();
		assertTrue(widget.handleGlobalInput(List.of()));
		widget.closeOverlays();
		assertEquals(1, children.get(root).size());
		widget.close();
		assertEquals(List.of("input", "overlays", "overlays", "content"), calls);
		assertEquals(List.of(1), childrenDuringClear);
		assertEquals(1, removedComponents.size());
		assertTrue(children.get(root).isEmpty());
	}
}
