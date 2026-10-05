package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.test.fixture.BattleNext_TestPageFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

class BattleNext_PanelBasedWidgetTest extends BattleNext_TestPageFixture {

	@Test
	void closingOwnedChildrenPreservesSharedInputAndReleasesInReverseOrder() throws Exception {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(400f, 300f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI textHost = root.createUIElement(180f, 28f, false);
		BattleNext_TextField field = new BattleNext_TextField(router, root, 180f, 28f, textHost);
		field.attach(textHost, root);
		root.addUIElement(textHost).inTL(0f, 0f);
		field.grabFocus(false);
		OwnedProbeWidget widget = new OwnedProbeWidget(router);
		widget.attach(root);
		CustomPanelAPI parent = widget.parent;
		CustomPanelAPI child = widget.child;
		widget.refreshContent();
		assertEquals(List.of(child, parent), removedComponents);
		assertTrue(field.hasFocus());
		CustomPanelAPI rebuiltParent = widget.parent;
		CustomPanelAPI rebuiltChild = widget.child;
		widget.close();
		assertEquals(List.of(child, parent, rebuiltChild, rebuiltParent), removedComponents);
		assertTrue(((List<?>) getField(widget, "ownedPanels")).isEmpty());
		assertTrue(field.hasFocus());
		assertTrue(router.getSession().hasInputState());
		field.close();
	}

	private class OwnedProbeWidget extends BattleNext_PanelBasedWidget {
		private CustomPanelAPI parent;
		private CustomPanelAPI child;

		private OwnedProbeWidget(BattleNext_UiInputRouter router) {
			super(router);
		}

		@Override
		protected void buildContent(CustomPanelAPI owner) {
			parent = ownPanel(owner, owner.createCustomPanel(200f, 100f, null));
			owner.addComponent(parent).inTL(0f, 50f);
			child = ownPanel(parent, owner.createCustomPanel(100f, 50f, null));
			parent.addComponent(child).inTL(0f, 0f);
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {180, 1400})
	void attachAndRefreshUseTheCompleteHostPanel(int width) {
		ProbeWidget widget = new ProbeWidget();
		CustomPanelAPI root = panel(width, 300f, new BaseCustomUIPanelPlugin());
		assertSame(root, widget.attach(root));
		for (int iteration = 0; iteration < 3; iteration++) {
			assertSame(root, widget.getPanel());
			assertEquals(width, root.getPosition().getWidth());
			assertEquals(300f, root.getPosition().getHeight());
			var components = children.get(root);
			var viewport = tooltips.get(components.get(components.size() - 1)).getViewport();
			assertEquals(root.getPosition().getX(), viewport.getX());
			assertEquals(root.getPosition().getY(), viewport.getY());
			assertEquals(root.getPosition().getWidth(), viewport.getWidth());
			assertEquals(root.getPosition().getHeight(), viewport.getHeight());
			widget.refreshContent();
		}
		widget.close();
		assertNotNull(root.getPosition());
	}

	private class ProbeWidget extends BattleNext_PanelBasedWidget {
		@Override
		protected void buildContent(CustomPanelAPI panel) {
			TooltipMakerAPI content = panel.createUIElement(panel.getPosition().getWidth(),
					panel.getPosition().getHeight(), false);
			content.addPara("probe", 0f);
			panel.addUIElement(content).inTL(0f, 0f);
		}
	}
}
