package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.util.List;

class BattleNext_QuickBattleAssemblyIntegrationTest extends BattleNext_TestFleetIoFixture {

	@Test
	void selectorAndMissionAgreeOnModularSelfBattlesAndAssemblyErrors() throws Exception {

		csvFleet("valid", "1", "1,parent_local,,,,,,\n");
		variant("valid", "parent_local", "parent", 3, new JSONObject().put("module", "child_local"));
		variant("valid", "child_local", "child", 7, null);
		csvFleet("broken", "2", "1,broken_local,,,,,,\n");
		variant("broken", "broken_local", "unknown_hull", 0, null);
		write("tournament/quick_battle.csv", "player,enemy,map,size\nab_player1,ab_player1,-1,1\nab_player2,ab_player2,-1,1\n");
		write("tournament/PvP_matches.csv", "player,enemy,map,size\n");
		var editor = new BattleNext_QuickBattleEditor(() -> {}, new BaseCustomUIPanelPlugin());
		attachEditor(editor, panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		List<?> rows = (List<?>) getField(editor, "rows");
		assertEquals("BattleNext_common_available", ((LabelAPI) getField(rows.get(0), "statusLabel")).getText());
		assertEquals("BattleNext_common_unavailable", ((LabelAPI) getField(rows.get(1), "statusLabel")).getText());
		var provider = new BattleNext_BattleSourceProvider();
		var matches = provider.load();
		assertEquals(1, matches.size());
		assertEquals("ab_player1", matches.get(0).getPlayer());
		assertEquals("ab_player1", matches.get(0).getEnemy());
		var validation = BattleNext_QuickBattleEditor.class.getDeclaredMethod("validationErrors", rows.get(1).getClass());
		validation.setAccessible(true);
		assertEquals(provider.getWarnings(), validation.invoke(editor, rows.get(1)));
		assertTrue(provider.getWarnings().get(0).contains("Unknown hull: unknown_hull"));
		int people = createdPeople.size();
		for (int iteration = 0; iteration < 120; iteration++) {
			editor.refreshRowStatuses();
		}
		assertEquals(people, createdPeople.size());
		((TextFieldAPI) getField(rows.get(0), "playerField")).setText("ab_player2");
		editor.refreshRowStatuses();
		assertEquals("BattleNext_common_unavailable", ((LabelAPI) getField(rows.get(0), "statusLabel")).getText());
		((TextFieldAPI) getField(rows.get(0), "playerField")).setText("ab_player1");
		editor.refreshRowStatuses();
		assertEquals("BattleNext_common_available", ((LabelAPI) getField(rows.get(0), "statusLabel")).getText());
	}

	@Test
	void reloadRecomputesAssemblyResultsAfterAResourceIsRepaired() throws Exception {

		csvFleet("repair", "1", "1,parent_local,,,,,,\n");
		variant("repair", "parent_local", "unknown_hull", 0, null);
		write("tournament/quick_battle.csv", "player,enemy,map,size\nab_player1,ab_player1,-1,1\n");
		write("tournament/PvP_matches.csv", "player,enemy,map,size\n");
		var editor = new BattleNext_QuickBattleEditor(() -> {}, new BaseCustomUIPanelPlugin());
		attachEditor(editor, panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		List<?> rows = (List<?>) getField(editor, "rows");
		assertEquals("BattleNext_common_unavailable", ((LabelAPI) getField(rows.get(0), "statusLabel")).getText());
		variant("repair", "parent_local", "parent", 0, new JSONObject().put("module", "child_local"));
		variant("repair", "child_local", "child", 8, null);
		editor.deserialize();
		rows = (List<?>) getField(editor, "rows");
		assertEquals("BattleNext_common_available", ((LabelAPI) getField(rows.get(0), "statusLabel")).getText());
		var provider = new BattleNext_BattleSourceProvider();
		assertEquals(1, provider.load().size());
		assertTrue(provider.getWarnings().isEmpty());
	}
}
