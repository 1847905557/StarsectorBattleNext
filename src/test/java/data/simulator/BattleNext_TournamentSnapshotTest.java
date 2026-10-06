package data.simulator;

import data.model.report.BattleNext_BattleReportModel;
import data.missions.BattleNext_MissionBattleMap;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battle.BattleNext_Match;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.test.fixture.BattleNext_TestMissionFixture;
import org.json.JSONObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_TournamentSnapshotTest extends BattleNext_TestMissionFixture {

	@ParameterizedTest
	@EnumSource(TournamentBracketType.class)
	void snapshotStartsFreshAndAdvancesAllBracketsWithBoDrawsStableRoundsAndFrozenAssets(TournamentBracketType bracket) throws Exception {

		quickBattles("alpha,beta,-1,1\n");
		standard("gamma", "gamma", "parent_variant");
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		BattleNext_TournamentModel source = new BattleNext_TournamentModel("cup", "Cup 中文 50%", bracket, 3, 3);
		source.setFleetIds(List.of("alpha", "beta", "gamma"));
		BattleNext_TournamentEngine engine = new BattleNext_TournamentEngine();
		engine.start(source);
		BattleNext_Match firstSource = engine.getNextMatch(source);
		engine.recordRound(source, firstSource.getId(), firstSource.getPlayer(), new BattleNext_BattleReportModel());
		String sourceJson = source.toJson().toString();
		BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromTournament(source, sources.getFleetStore(),
				sources.getRuleStore(), BattleNext_SimulationConfig.quickBattle(123f));
		BattleNext_TournamentSnapshot snapshot = BattleNext_TournamentSnapshot.create(request);
		assertEquals(request.id(), snapshot.getId());
		assertEquals("cup", snapshot.getSourceId());
		assertEquals("Simulation Cup 中文 50% - " + request.id(), snapshot.getTournament().getName());
		assertTrue(snapshot.getTournament().getMatches().stream().allMatch(match -> match.getRounds().isEmpty()));
		sources.getFleetStore().get("alpha").setName("Changed");
		assertEquals("Standard", snapshot.getFleetNames().get("alpha"));
		BattleNext_SimulationRequest first = snapshot.prepareRound();
		int map = first.source().getInt("actualMap");
		assertTrue(map >= 0 && map <= BattleNext_MissionBattleMap.LAST_MAP_ID);
		snapshot.transition(BattleNext_SimulationTask.State.PAUSED, "Paused");
		BattleNext_TournamentSnapshot restored = snapshot.copy();
		assertEquals(first, restored.prepareRound());
		assertEquals(map, restored.prepareRound().source().getInt("actualMap"));
		snapshot.record(result(first, "draw"));
		assertEquals(0, snapshot.getTournament().getNextPendingMatch().getPlayerWins());
		assertEquals(1, snapshot.getTournament().getNextPendingMatch().getRounds().size());
		String champion = "alpha";
		int rounds = 1;
		while (!snapshot.getTournament().isCompleted()) {
			BattleNext_SimulationRequest round = snapshot.prepareRound();
			BattleNext_Match match = snapshot.getTournament().getNextPendingMatch();
			String winner = match.getPlayer().equals(champion) ? "player" : match.getEnemy().equals(champion) ? "enemy" : "player";
			BattleNext_SimulationResult result = result(round, winner);
			snapshot.record(result);
			long version = snapshot.getVersion();
			snapshot.record(result);
			assertEquals(version, snapshot.getVersion());
			assertEquals(123f, round.config().assaultAfterSeconds());
			rounds++;
			assertTrue(rounds <= 30, "Tournament must finish within the constructed deterministic winners");
		}
		assertEquals(champion, snapshot.getTournament().getChampionFleetId());
		assertEquals(sourceJson, source.toJson().toString());
		BattleNext_SimulationResult finished = BattleNext_SimulationResult.fromJson(snapshot.result().toJson());
		assertTrue(finished.reportJson().isEmpty());
		BattleNext_TournamentSnapshot received = new BattleNext_TournamentSnapshot();
		received.fromJson(new JSONObject(finished.tournamentSnapshotJson()));
		assertEquals(champion, received.getTournament().getChampionFleetId());
		assertEquals(rounds, received.getTournament().getMatches().stream().mapToInt(match -> match.getRounds().size()).sum());
	}

	@Test
	void snapshotStorePreservesRoundAndReportsAndKeepsPlayerTournamentCatalogSeparate() throws Exception {
		quickBattles("alpha,beta,-1,1\n");
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		BattleNext_TournamentModel source = new BattleNext_TournamentModel();
		source.setId("cup");
		source.setName("Cup");
		source.setFleetIds(List.of("alpha", "beta"));
		sources.getTournamentStore().save(source);
		sources.getTournamentStore().setActiveId("cup");
		BattleNext_TournamentSnapshot snapshot = BattleNext_TournamentSnapshot.create(BattleNext_SimulationRequest.fromTournament(
				source, sources.getFleetStore(), sources.getRuleStore(), BattleNext_SimulationConfig.defaults()));
		BattleNext_SimulationRequest round = snapshot.prepareRound();
		BattleNext_TournamentSnapshotStore store = new BattleNext_TournamentSnapshotStore();
		store.reload();
		store.save(snapshot);
		store.reload();
		assertEquals(round, store.get(snapshot.getId()).getCurrentRound());
		snapshot.record(result(round, "player"));
		store.save(snapshot);
		store.reload();
		assertEquals(round.id(), store.get(snapshot.getId()).getTournament().getMatches().get(0).getRounds().get(0).getReport().getId());
		sources.getTournamentStore().reload();
		assertEquals(List.of("cup"), sources.getTournamentStore().getAll().stream().map(value -> value.getId()).toList());
		assertEquals("cup", sources.getTournamentStore().getActiveId());
		store.delete(snapshot.getId());
		assertFalse(Files.exists(directory.resolve("tournament/_simulations/" + snapshot.getId() + ".json")));
	}

	private BattleNext_SimulationResult result(BattleNext_SimulationRequest round, String winner) throws Exception {
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId(round.id());
		report.setName("Round report");
		report.setWinner(winner);
		return new BattleNext_SimulationResult(round.id(), BattleNext_SimulationResult.Outcome.COMPLETED,
				report.toJson().toString(), 10f, 100000000L, 600L, 1024L, "");
	}
}
