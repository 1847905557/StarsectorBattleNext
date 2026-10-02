package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_TextFieldSessionTest extends BattleNext_TestPageFixture {

	@Test
	void closingOneFieldPreservesTheOtherFieldsFocusAndRegistration() throws Exception {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI owner = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI host = owner.createUIElement(200f, 100f, false);
		BattleNext_TextField first = new BattleNext_TextField(router, owner, 200f, 28f, host);
		BattleNext_TextField second = new BattleNext_TextField(router, owner, 200f, 28f, host);
		first.attach(host, owner);
		second.attach(host, owner);
		owner.addUIElement(host).inTL(0f, 0f);
		second.grabFocus(false);
		first.close();
		assertTrue(second.hasFocus());
		assertSame(router, getField(second, "inputRouter"));
		assertEquals(1, ((Map<?, ?>) getField(router.getSession(), "REGISTERED")).size());
		assertEquals(1, ((Map<?, ?>) getField(router.getSession(), "VIEWPORTS")).size());
		second.close();
		assertFalse(router.getSession().hasInputState());
		assertTrue(((Map<?, ?>) getField(router.getSession(), "VIEWPORTS")).isEmpty());
		assertTrue(tooltips.get(host).getComponents().isEmpty());
	}

	@Test
	void textInputUsesBothInnerAndOuterScrollingViewports() throws Exception {
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI owner = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI outer = owner.createUIElement(400f, 100f, true);
		CustomPanelAPI innerPanel = owner.createCustomPanel(300f, 200f, null);
		outer.addCustom(innerPanel, 0f);
		outer.addSpacer(400f);
		TooltipMakerAPI inner = innerPanel.createUIElement(300f, 200f, true);
		inner.addSpacer(60f);
		BattleNext_TextField field = new BattleNext_TextField(router, innerPanel, 200f, 28f, inner, outer);
		field.attach(inner, innerPanel);
		inner.addSpacer(400f);
		innerPanel.addUIElement(inner).inTL(0f, 0f);
		owner.addUIElement(outer).inTL(0f, 0f);
		assertEquals(List.of(inner, outer), ((Map<?, ?>) getField(router.getSession(), "VIEWPORTS")).get(field));
		outer.getExternalScroller().setYOffset(100f);
		PositionAPI position = field.getPosition();
		InputEventAPI event = proxy(InputEventAPI.class, (method, args) -> switch (method) {
			case "isLMBDownEvent" -> true;
			case "getX" -> (int) position.getCenterX();
			case "getY" -> (int) position.getCenterY();
			default -> null;
		});
		assertFalse(router.handleGlobalInput(List.of(event)));
		assertFalse(field.hasFocus());
		field.grabFocus(false);
		field.close();
		assertFalse(router.getSession().hasInputState());
	}
}
