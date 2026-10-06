package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay.Column;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_QuickBattleDisplayTest extends BattleNext_TestPageFixture {

	@Test
	void callerColumnSharesDetermineInputAndActionGeometryAndRemainStableAfterInputMutation() {

		deferTooltipWidth = true;
		List<Column> columns = new ArrayList<>(List.of(Column.PLAYER, Column.ENEMY, Column.MAP,
				Column.SCALE, Column.STATUS, Column.ACTIONS));
		List<Float> shares = new ArrayList<>(List.of(1f, 1f, 1f, 1f, 1f, 3f));
		BattleNext_QuickBattleDisplay.Presentation presentation = new BattleNext_QuickBattleDisplay.Presentation(columns, shares, false,
				row -> new BattleNext_QuickBattleDisplay.Action("Simulate", "simulate:" + row.getIndex(), row.getRawField() == null));
		columns.clear();
		shares.set(5, 1f);
		BattleNext_QuickBattleModel normal = new BattleNext_QuickBattleModel("first", "second", -1, 1f);
		normal.setId("normal");
		BattleNext_QuickBattleModel raw = BattleNext_QuickBattleModel.parseLine("# note", false);
		raw.setId("raw");
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(200f, 180f, new BaseCustomUIPanelPlugin());
		BattleNext_QuickBattleDisplay display = new BattleNext_QuickBattleDisplay(router, List.of(normal, raw), presentation,
				entry -> entry.getId(), row -> row.getRawField() == null ? List.of() : List.of("Raw row"));
		display.attach(root);
		BattleNext_TextField player = display.getRows().get(0).getField(Column.PLAYER);
		BattleNext_TextField enemy = display.getRows().get(0).getField(Column.ENEMY);
		assertEquals(8f, player.getPosition().getWidth());
		assertEquals(8f, enemy.getPosition().getWidth());
		assertEquals(28f, enemy.getPosition().getX() - player.getPosition().getX());
		assertEquals(52f, buttonPositions.get("simulate:0").getWidth());
		assertEquals(92f, display.getRows().get(1).getRawField().getPosition().getWidth());
		assertEquals("first", player.getText());
		assertEquals("second", enemy.getText());
		assertEquals("# note", display.getRows().get(1).getRawField().getText());
		assertFalse(player.isEditable());
		assertTrue(buttons.get("simulate:0").get());
		assertFalse(buttons.get("simulate:1").get());
		display.close();
		assertTrue(children.get(root).isEmpty());
		assertFalse(router.getSession().hasInputState());
	}
}
