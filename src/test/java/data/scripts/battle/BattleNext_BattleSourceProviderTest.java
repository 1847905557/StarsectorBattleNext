package data.scripts.battle;

import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import data.scripts.tools.BattleNext_Settings;
import data.test.fixture.BattleNext_TestTextCatalog;

import java.util.List;

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
		BattleNext_StartedTournament started = new BattleNext_TournamentEngine().start(tournament, List.of("alpha", "beta"), BattleNext_StartedTournament.ExecutionKind.MANUAL);
		BattleNext_StartedTournamentStore instances = new BattleNext_StartedTournamentStore();
		instances.save(started);
		instances.activate(started.getId());
		var provider = new BattleNext_BattleSourceProvider();
		assertEquals(1, provider.load().size());
		assertEquals(started.getId(), provider.getActiveTournament().getId());
		assertEquals("alpha", provider.loadQuickBattles().get(0).getPlayer());
		assertNull(provider.getActiveTournament());
		assertTrue(provider.getRuleWarnings().isEmpty());
		write("tournament/quick_battle.csv", "player,enemy,map,size\nbeta,alpha,4,2\n");
		assertEquals("beta", provider.loadQuickBattles().get(0).getPlayer());
		assertEquals(started.getId(), instances.getActiveId());
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

	@Test
	void completeTournamentCheckRereadsReferencesAndCreationRejectsTheSameOrderedFailures() throws Exception {
		standard("alpha", "alpha", "parent_variant");
		standard("broken", "broken", "unknown_variant");
		var provider = new BattleNext_BattleSourceProvider();
		provider.reloadTournamentCatalog();
		var configuration = new BattleNext_TournamentModel("cup", "Cup", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 4);
		provider.getTournamentStore().save(configuration);
		List<String> slots = List.of("alpha", "alpha", "broken", "missing");
		var checked = provider.checkTournament("cup", slots);
		assertEquals(List.of(BattleNext_BattleValidation.FailureKind.CONFIGURATION, BattleNext_BattleValidation.FailureKind.ASSEMBLY,
				BattleNext_BattleValidation.FailureKind.REFERENCE), checked.failures().stream().map(BattleNext_BattleValidation.Failure::kind).toList());
		var store = new BattleNext_StartedTournamentStore();
		var rejection = assertThrows(BattleNext_BattleValidation.Rejection.class, () -> new BattleNext_TournamentEngine().create(configuration,
				slots, BattleNext_StartedTournament.ExecutionKind.MANUAL, store, provider.getFleetStore(), provider.getRuleStore()));
		assertEquals(checked, rejection.getResult());
		assertEquals("Tournament qualification failed: cup", rejection.getMessage());
		store.reload();
		assertTrue(store.getAll().isEmpty());
		BattleNext_Settings.save(true, true, false, false, 500f, 1f, 3f, 50, 0);
		assertEquals(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_duplicate_fleets"), checked.messages().get(0));
		standard("beta", "beta", "child_variant");
		assertTrue(provider.checkTournament("cup", List.of("alpha", "", "beta", "")).failures().isEmpty());
		assertFalse(provider.checkTournament("cup", List.of("alpha", "", "", "")).failures().isEmpty());
	}
}
