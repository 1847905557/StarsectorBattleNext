package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.test.fixture.BattleNext_TestPageFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicInteger;

class BattleNext_OptionFieldTest extends BattleNext_TestPageFixture {

	@ParameterizedTest
	@ValueSource(ints = {72, 180, 360})
	void rowRenderingAndActionsAreIndependentOfItsOwner(int width) throws Exception {
		AtomicInteger backgrounds = new AtomicInteger();
		AtomicInteger actions = new AtomicInteger();
		CustomPanelAPI owner = panel(400f, 300f, new BaseCustomUIPanelPlugin() {
			@Override
			public void renderBelow(float alphaMult) {
				backgrounds.incrementAndGet();
			}

			@Override
			public void buttonPressed(Object buttonId) {
				actions.incrementAndGet();
			}
		});
		TooltipMakerAPI host = owner.createUIElement(width, 30f, false);
		BattleNext_OptionField option = new BattleNext_OptionField(new BattleNext_UiInputRouter(), owner,
				width, optionSource(), BattleNext_OptionField.OptionSelectionMode.SINGLE, host);
		option.attach(host, owner);
		owner.addUIElement(host).inTL(0f, 0f);
		CustomPanelAPI row = (CustomPanelAPI) getField(option, "row");
		BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(option, "picker");
		CustomPanelAPI buttonRow = (CustomPanelAPI) getField(picker, "row");
		assertNotSame(row.getPlugin(), buttonRow.getPlugin());
		owner.getPlugin().renderBelow(1f);
		row.getPlugin().renderBelow(1f);
		assertEquals(1, backgrounds.get());
		row.getPlugin().buttonPressed("parent_action");
		assertEquals(0, actions.get());
		owner.getPlugin().buttonPressed("parent_action");
		assertEquals(1, actions.get());
		option.close();
	}

	@ParameterizedTest
	@ValueSource(ints = {72, 180, 360})
	void explicitWidthLocalPositionsAndClosePreserveTheirContracts(int width) throws Exception {
		deferTooltipWidth = true;
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI overlay = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		CustomPanelAPI owner = panel(400f, 28f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI host = owner.createUIElement(width, 30f, false);
		assertEquals(0f, host.getPosition().getWidth());
		BattleNext_OptionField option = new BattleNext_OptionField(router, overlay, width, optionSource(),
				BattleNext_OptionField.OptionSelectionMode.MULTIPLE, host);
		option.attach(host, owner);
		option.getField().setText("first");
		CustomPanelAPI row = (CustomPanelAPI) getField(option, "row");
		BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(option, "picker");
		CustomPanelAPI buttonRow = (CustomPanelAPI) getField(picker, "row");
		assertNotSame(owner.getPlugin(), row.getPlugin());
		PositionAPI field = option.getField().getPosition();
		assertEquals(width, row.getPosition().getWidth());
		assertEquals(28f, row.getPosition().getHeight());
		assertEquals(width - 32f, field.getWidth());
		assertEquals(28f, field.getHeight());
		assertEquals(row.getPosition().getX(), field.getX());
		assertEquals(row.getPosition().getY(), field.getY());
		assertEquals(row.getPosition().getX() + width - 32f, buttonRow.getPosition().getX());
		assertEquals(row.getPosition().getY(), buttonRow.getPosition().getY());
		assertEquals(32f, buttonRow.getPosition().getWidth());
		assertEquals(28f, buttonRow.getPosition().getHeight());
		assertEquals("...", buttonLabels.get("TOGGLE"));
		assertEquals(30f, tooltips.get(host).getUsedHeight());
		assertSame(overlay, getField(picker, "overlayHost"));
		owner.addUIElement(host).inTL(0f, 0f);
		option.closeOverlays();
		assertSame(field, option.getField().getPosition());
		assertEquals("first", option.getField().getText());
		assertTrue(router.getSession().hasInputState());
		option.close();
		assertTrue(tooltips.get(host).getComponents().isEmpty());
		assertFalse(router.getSession().hasInputState());
		assertEquals(3, removedComponents.size());
		assertSame(row, removedComponents.get(2));
	}
}
