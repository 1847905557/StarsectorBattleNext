package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_TooltipBasedWidgetTest extends BattleNext_TestPageFixture {

	@Test
	void releaseAndFullCloseUseActualParentsInChildFirstOrder() throws Exception {
		CustomPanelAPI factory = panel(500f, 300f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI host = factory.createUIElement(200f, 100f, false);
		ProbeWidget widget = new ProbeWidget();
		widget.attach(host, factory);
		CustomPanelAPI first = widget.parent;
		CustomPanelAPI child = widget.child;
		assertEquals(List.of(first), tooltips.get(host).getComponents());
		widget.releaseChild();
		assertTrue(children.get(first).isEmpty());
		assertEquals(1, ((List<?>) getField(widget, "ownedPanels")).size());
		widget.refreshContent();
		assertEquals(List.of(child, first), removedComponents);
		CustomPanelAPI rebuilt = widget.parent;
		CustomPanelAPI rebuiltChild = widget.child;
		widget.closeOverlays();
		assertEquals(2, ((List<?>) getField(widget, "ownedPanels")).size());
		widget.close();
		assertEquals(List.of(child, first, rebuiltChild, rebuilt), removedComponents);
		assertTrue(tooltips.get(host).getComponents().isEmpty());
		assertTrue(((List<?>) getField(widget, "ownedPanels")).isEmpty());
	}

	private class ProbeWidget extends BattleNext_TooltipBasedWidget {
		private CustomPanelAPI parent;
		private CustomPanelAPI child;

		@Override
		protected void buildContent(TooltipMakerAPI host, CustomPanelAPI owner) {
			parent = ownPanel(host, owner.createCustomPanel(200f, 60f, null));
			host.addCustom(parent, 0f);
			child = ownPanel(parent, owner.createCustomPanel(100f, 30f, null));
			parent.addComponent(child).inTL(0f, 0f);
		}

		private void releaseChild() {
			releasePanel(child);
		}
	}
}
