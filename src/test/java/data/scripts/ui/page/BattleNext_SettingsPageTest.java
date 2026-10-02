package data.scripts.ui.page;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.page.BattleNext_SettingsPage;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_SettingsPageTest extends BattleNext_TestPageFixture {

	@ParameterizedTest
	@ValueSource(ints = {180, 1400})
	void deferredGeometryUsesLayoutWidthForAllFieldsAndPreservesDraftOnRefresh(int width) throws Exception {

		deferTooltipWidth = true;
		BattleNext_SettingsPage page = new BattleNext_SettingsPage();
		CustomPanelAPI root = panel(width, 900f, new BaseCustomUIPanelPlugin());
		page.attach(root);
		float expectedWidth = Math.min(260f, width - 20f);
		List<String> names = List.of("battleTimeLimit", "defaultTimeMult", "boostTimeMult", "reportKeepCount");
		List<String> values = List.of("700.5", "pending", "4.5", "75");
		assertFalse(page.hasUnsavedChanges());
		for (int index = 0; index < names.size(); index++) {
			TextFieldAPI field = (TextFieldAPI) getField(page, names.get(index));
			assertEquals(expectedWidth, field.getPosition().getWidth(), names.get(index));
			assertEquals(28f, field.getPosition().getHeight(), names.get(index));
			field.setText(values.get(index));
		}
		assertTrue(page.hasUnsavedChanges());
		page.refreshContent();
		for (int index = 0; index < names.size(); index++) {
			TextFieldAPI field = (TextFieldAPI) getField(page, names.get(index));
			assertEquals(expectedWidth, field.getPosition().getWidth(), names.get(index));
			assertEquals(28f, field.getPosition().getHeight(), names.get(index));
			assertEquals(values.get(index), field.getText(), names.get(index));
		}
		assertTrue(page.hasUnsavedChanges());
		assertEquals(1, children.get(root).size());
		BattleNext_UiInputRouter router = (BattleNext_UiInputRouter) getField(page, "inputRouter");
		assertEquals(4, ((Map<?, ?>) getField(router.getSession(), "REGISTERED")).size());
		page.close();
		assertTrue(children.get(root).isEmpty());
		assertFalse(router.getSession().hasInputState());
	}

	@Test
	void attachRefreshAndCloseOwnThePagePanel() throws Exception {
		BattleNext_SettingsPage page = new BattleNext_SettingsPage();
		CustomPanelAPI root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());

		page.attach(root);
		assertNotNull(getField(page, "panel"));
		assertEquals(1, children.get(root).size());
		page.refreshContent();
		assertEquals(1, children.get(root).size());
		page.close();
		assertEquals(0, children.get(root).size());
	}
}
