package data.model.quickbattle.io;

import data.model.quickbattle.BattleNext_QuickBattleStore;
import data.model.BattleNext_LoadSource;
import data.model.quickbattle.BattleNext_QuickBattleModel.QuickBattleRowType;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

class BattleNext_QuickBattleTextRoundTripTest extends BattleNext_TestFleetIoFixture {

	@Test
	void formalSourcesKeepDuplicateRawDisabledAndFinalRowsAcrossSaving() throws Exception {

		write("tournament/quick_battle.csv", "player,enemy,map,size\nalpha,beta,1,1\nalpha,beta,1,1\n# note\n# beta,alpha,2,1\n\n");
		write("tournament/PvP_matches.csv", "player,enemy,map,size\n01,02,3,1\n01,02,3,1\n# 03,04,5,1");
		BattleNext_QuickBattleStore store = new BattleNext_QuickBattleStore();
		store.reload();
		assertEquals(8, store.getAll().size());
		var numbered = store.get("pvp-legacy-0");
		assertEquals("ab_player01", numbered.getPlayer());
		assertEquals("ab_player02", numbered.getEnemy());
		assertEquals(numbered.getPlayer(), store.get("pvp-legacy-1").getPlayer());
		assertEquals(BattleNext_LoadSource.LEGACY, numbered.getSourceType());
		assertEquals("tournament/PvP_matches.csv", numbered.getSourcePath());
		assertEquals("ab_player03", store.get("pvp-legacy-2").getPlayer());
		assertFalse(store.get("pvp-legacy-2").isEnabled());
		store.save(numbered);
		var standard = store.get("quick-standard-0");
		assertEquals(BattleNext_LoadSource.STANDARD, standard.getSourceType());
		assertEquals("tournament/quick_battle.csv", standard.getSourcePath());
		assertEquals(QuickBattleRowType.COMMENT_BATTLE, store.get("quick-standard-3").getRowType());
		store.save(standard);
		assertEquals(List.of("player,enemy,map,size", "01,02,3,1.0", "01,02,3,1.0", "# 03,04,5,1.0"),
				Files.readAllLines(directory.resolve("tournament/PvP_matches.csv"), StandardCharsets.UTF_8));
		store.reload();
		assertEquals(8, store.getAll().size());
		assertEquals("# note", store.get("quick-standard-2").getRawContent());
		assertFalse(store.get("quick-standard-3").isEnabled());
		assertEquals("ab_player01", store.get("pvp-legacy-0").getPlayer());
		assertTrue(dataReads.isEmpty());
	}
}
