package data.model.quickbattle.io;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import data.model.BattleNext_LoadSource;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.model.quickbattle.BattleNext_QuickBattleModel.QuickBattleRowType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_QuickBattleLoadingIntegrationTest {

	@Test
	void standardCatalogRetainsPlayableCommentedAndRawRowsInOrder() throws Exception {
		String csv = "player,enemy,map,size\nalpha,beta,-1,1.5\n# beta,alpha,4,2\n# note\n";
		withText(csv, false, () -> {
			List<BattleNext_QuickBattleModel> rows = new BattleNext_QuickBattleStandardLoader().load();

			assertEquals(3, rows.size());
			assertEquals(List.of("quick-standard-0", "quick-standard-1", "quick-standard-2"),
					rows.stream().map(BattleNext_QuickBattleModel::getId).toList());
			assertEquals(List.of(QuickBattleRowType.BATTLE, QuickBattleRowType.COMMENT_BATTLE, QuickBattleRowType.RAW),
					rows.stream().map(BattleNext_QuickBattleModel::getRowType).toList());
			assertTrue(rows.get(0).isEnabled());
			assertFalse(rows.get(1).isEnabled());
			assertEquals("# note", rows.get(2).toSourceLine());
			assertEquals(BattleNext_LoadSource.STANDARD, rows.get(0).getSourceType());
			assertEquals("tournament/quick_battle.csv", rows.get(0).getSourcePath());
		});
	}

	@Test
	void legacyCatalogConvertsFleetReferencesAndRetainsItsSource() throws Exception {
		String csv = "player,enemy,map,size\n01,02,3,1\n# 03,04,5,1\n";
		withText(csv, false, () -> {
			List<BattleNext_QuickBattleModel> rows = new BattleNext_QuickBattleLegacyLoader().load();

			assertEquals(2, rows.size());
			assertEquals("ab_player01", rows.get(0).getPlayer());
			assertEquals("ab_player02", rows.get(0).getEnemy());
			assertEquals("pvp-legacy-1", rows.get(1).getId());
			assertFalse(rows.get(1).isEnabled());
			assertTrue(rows.get(1).usesLegacyIds());
			assertEquals(BattleNext_LoadSource.LEGACY, rows.get(0).getSourceType());
			assertEquals("tournament/PvP_matches.csv", rows.get(0).getSourcePath());
		});
	}

	@Test
	void unavailableLegacyCatalogIsAnEmptyOptionalSource() throws Exception {
		withText("", true, () -> assertTrue(new BattleNext_QuickBattleLegacyLoader().load().isEmpty()));
	}

	@Test
	void invalidQuickBattleNumbersRemainEditableRawRows() {
		BattleNext_QuickBattleModel invalid = BattleNext_QuickBattleModel.parseLine("alpha,beta,12,1", false);
		BattleNext_QuickBattleModel nonFinite = BattleNext_QuickBattleModel.parseLine("alpha,beta,1,NaN", false);

		assertEquals(QuickBattleRowType.RAW, invalid.getRowType());
		assertEquals(QuickBattleRowType.RAW, nonFinite.getRowType());
		assertFalse(invalid.isEnabled());
		assertEquals("# alpha,beta,12,1", invalid.toSourceLine());
	}

	private void withText(String text, boolean missing, CheckedAction action) throws Exception {
		SettingsAPI previous = Global.getSettings();
		SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
					if ("loadText".equals(method.getName())) {
						if (missing) throw new IOException("Legacy catalog unavailable");
						return text;
					}
					return null;
				});
		Global.setSettings(settings);
		try {
			action.run();
		} finally {
			Global.setSettings(previous);
		}
	}

	private interface CheckedAction {
		void run() throws Exception;
	}
}
