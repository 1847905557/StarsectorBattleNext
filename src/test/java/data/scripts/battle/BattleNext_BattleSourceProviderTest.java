package data.scripts.battle;

import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_BattleSourceProviderTest extends BattleNext_TestFleetIoFixture {

	@Test
	void explicitQuickBattleLoadingRetainsValidRowsAndSharesPvpValidation() throws Exception {

		standard("alpha", "alpha", "parent_variant");
		standard("beta", "beta", "child_variant");
		standard("broken", "broken", "missing_variant");
		write("tournament/quick_battle.csv", "player,enemy,map,size\nalpha,beta,2,1.5\n# beta,alpha,3,1\n"
				+ "invalid raw row\nalpha,missing,0,1\nalpha,broken,0,1\nalpha,beta,-1,2\n");
		write("tournament/PvP_matches.csv", "player,enemy,map,size\n");
		var provider = new BattleNext_BattleSourceProvider();
		var pvp = provider.load();
		var warnings = List.copyOf(provider.getWarnings());
		var pve = provider.loadQuickBattles();
		assertEquals(pvp.stream().map(BattleNext_Match::getId).toList(), pve.stream().map(BattleNext_Match::getId).toList());
		assertEquals(List.of(2, -1), pve.stream().map(BattleNext_Match::getMap).toList());
		assertEquals(List.of(1.5f, 2f), pve.stream().map(BattleNext_Match::getMapScale).toList());
		assertEquals(List.of("alpha", "alpha"), pve.stream().map(BattleNext_Match::getPlayer).toList());
		assertEquals(warnings, provider.getWarnings());
		assertTrue(warnings.stream().anyMatch(warning -> warning.contains("missing player/enemy")));
		assertTrue(warnings.stream().anyMatch(warning -> warning.contains("missing_variant")));
	}

	@Test
	void quickBattleEntryClearsActiveTournamentAndReloadsCurrentSources() throws Exception {

		standard("alpha", "alpha", "parent_variant");
		standard("beta", "beta", "child_variant");
		write("tournament/quick_battle.csv", "player,enemy,map,size\nalpha,beta,2,1.5\n");
		write("tournament/PvP_matches.csv", "player,enemy,map,size\n");
		var tournaments = new BattleNext_TournamentStore();
		tournaments.reload();
		var tournament = new BattleNext_TournamentModel();
		tournament.setId("active");
		tournament.setName("Active");
		tournaments.save(tournament);
		tournaments.setActiveId("active");
		var provider = new BattleNext_BattleSourceProvider();
		assertTrue(provider.load().isEmpty());
		assertEquals("active", provider.getActiveTournament().getId());
		assertEquals("alpha", provider.loadQuickBattles().get(0).getPlayer());
		assertNull(provider.getActiveTournament());
		assertTrue(provider.getRuleWarnings().isEmpty());
		write("tournament/quick_battle.csv", "player,enemy,map,size\nbeta,alpha,4,2\n");
		assertEquals("beta", provider.loadQuickBattles().get(0).getPlayer());
		assertEquals("active", tournaments.getActiveId());
	}

	@Test
	void failedFleetReloadPublishesWarningsAndRecoversOnNextLoad() throws Exception {
		standard("alpha", "alpha", "parent_variant");
		write("tournament/quick_battle.csv", "player,enemy,map,size\nalpha,alpha,0,1\n");
		write("tournament/PvP_matches.csv", "player,enemy,map,size\n");
		var provider = new BattleNext_BattleSourceProvider();
		assertEquals(1, provider.loadQuickBattles().size());
		standard("duplicate", "alpha", "child_variant");
		assertTrue(provider.loadQuickBattles().isEmpty());
		assertTrue(provider.getWarnings().get(0).contains("alpha"));
		standard("duplicate", "beta", "child_variant");
		assertEquals(1, provider.loadQuickBattles().size());
		assertTrue(provider.getWarnings().isEmpty());
	}
}
