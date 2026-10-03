package data.model.quickbattle.io;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import data.model.BattleNext_LoadSource;
import data.test.fixture.BattleNext_TestGameFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.util.List;

class BattleNext_QuickBattleLegacyLoaderTest extends BattleNext_TestGameFixture {

	@Test
	void lastLineWithoutTerminatorAndDuplicateRowsKeepLegacyReferences() throws Exception {
		Global.setSettings(proxy(SettingsAPI.class, (method, args) -> {
			assertEquals("loadText", method);
			assertEquals("tournament/PvP_matches.csv", args[0]);
			assertEquals("AI_Battles_Next", args[1]);
			return "player,enemy,map,size\n01,02,3,1\n01,02,3,1";
		}));
		var rows = new BattleNext_QuickBattleLegacyLoader().load();
		assertEquals(List.of("pvp-legacy-0", "pvp-legacy-1"), rows.stream().map(row -> row.getId()).toList());
		assertEquals(List.of("ab_player01", "ab_player01"), rows.stream().map(row -> row.getPlayer()).toList());
		assertEquals(BattleNext_LoadSource.LEGACY, rows.get(1).getSourceType());
		assertTrue(rows.get(1).isEnabled());
	}
}
