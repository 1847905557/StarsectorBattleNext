package data.scripts.tournament;

import data.model.report.BattleNext_BattleReportModel;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_Round;
import data.scripts.tournament.BattleNext_StartedTournament.ExecutionKind;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class BattleNext_TournamentEngineTest {

	private final BattleNext_TournamentEngine engine = new BattleNext_TournamentEngine();
	private final Map<BattleNext_TournamentModel, BattleNext_StartedTournament> instances = new HashMap<>();
	private final Map<BattleNext_TournamentModel, List<String>> participants = new HashMap<>();

	@Test
	void drawAndSideSwapUseOrderedRounds() {
		BattleNext_TournamentModel tournament = tournament(2, TournamentBracketType.SINGLE_ELIMINATION, 4);
		start(tournament);
		BattleNext_Match match = engine.getNextMatch(instances.get(tournament));
		engine.recordRound(tournament, instances.get(tournament), match.getId(), "draw", report().getId());
		assertEquals(BattleNext_Round.RoundStatus.DRAW, match.getRounds().get(0).getStatus());
		assertEquals("F1", match.getPlayer());
		engine.recordRound(tournament, instances.get(tournament), match.getId(), "F1", report().getId());
		assertEquals("F2", match.getPlayer());
		engine.recordRound(tournament, instances.get(tournament), match.getId(), "F1", report().getId());
		assertEquals(BattleNext_Round.RoundStatus.ENEMY_WIN, match.getRounds().get(2).getStatus());
		assertEquals(2, match.getPlayerWins());
		assertEquals(BattleNext_Match.MatchStatus.PENDING, match.getStatus());
		engine.recordRound(tournament, instances.get(tournament), match.getId(), "F1", report().getId());
		assertEquals("F1", instances.get(tournament).getChampionFleetId());
		assertEquals(4, match.getRounds().size());
	}

	@Test
	void largeBoCountDoesNotOverflowThreshold() {
		BattleNext_TournamentModel tournament = tournament(2, TournamentBracketType.SINGLE_ELIMINATION, Integer.MAX_VALUE);
		start(tournament);
		BattleNext_Match match = engine.getNextMatch(instances.get(tournament));
		engine.recordRound(tournament, instances.get(tournament), match.getId(), match.getPlayer(), report().getId());
		assertEquals(BattleNext_Match.MatchStatus.PENDING, match.getStatus());
	}

	@Test
	void playableMatchTotalsUseFormatAndSkipByes() {
		for (int count : new int[]{2, 3, 4, 5, 8, 32}) {
			BattleNext_TournamentModel single = tournament(count, TournamentBracketType.SINGLE_ELIMINATION, 3);
			assertEquals(count - 1, BattleNext_TournamentEngine.playableMatchTotal(single, participants.get(single)));
			BattleNext_TournamentModel roundRobin = tournament(count, TournamentBracketType.ROUND_ROBIN, 3);
			assertEquals(count * (count - 1) / 2, BattleNext_TournamentEngine.playableMatchTotal(roundRobin, participants.get(roundRobin)));
			BattleNext_TournamentModel doubleElimination = tournament(count, TournamentBracketType.DOUBLE_ELIMINATION, 3);
			assertEquals(2 * count - 2, BattleNext_TournamentEngine.playableMatchTotal(doubleElimination, participants.get(doubleElimination)));
		}
		BattleNext_TournamentModel tournament = tournament(4, TournamentBracketType.SINGLE_ELIMINATION, 4);
		start(tournament);
		assertEquals(3, instances.get(tournament).getPlayableMatchTotal(tournament));
		assertEquals(2, instances.get(tournament).getPlayableMatchCount());
		assertEquals(3, tournament.getWinTarget());
	}

	@Test
	void seededBracketsCompleteForSupportedFleetCounts() {
		for (int count : new int[]{2, 3, 4, 5, 8, 32}) {
			for (TournamentBracketType type : new TournamentBracketType[]{TournamentBracketType.SINGLE_ELIMINATION, TournamentBracketType.DOUBLE_ELIMINATION}) {
				BattleNext_TournamentModel tournament = tournament(count, type, 1);
				start(tournament);
				int played = finishByPlayer(tournament);
				assertTrue(instances.get(tournament).isCompleted(), type + " with " + count + " fleets");
				assertTrue(instances.get(tournament).getFleetIds().contains(instances.get(tournament).getChampionFleetId()));
				assertTrue(played > 0);
				assertNull(engine.getNextMatch(instances.get(tournament)));
				assertEliminationLosses(tournament, type, count);
			}
		}
	}

	@Test
	void doubleEliminationHandlesAlternatingWinners() {
		for (int count : new int[]{2, 3, 4, 5, 8, 32}) {
			BattleNext_TournamentModel tournament = tournament(count, TournamentBracketType.DOUBLE_ELIMINATION, 1);
			start(tournament);
			int played = 0;
			while (!instances.get(tournament).isCompleted() && played < 2000) {
				BattleNext_Match next = engine.getNextMatch(instances.get(tournament));
				assertNotNull(next);
				engine.recordRound(tournament, instances.get(tournament), next.getId(), played % 2 == 0 ? next.getEnemy() : next.getPlayer(), report().getId());
				played++;
			}
			assertTrue(instances.get(tournament).isCompleted(), "Alternating results with " + count + " fleets");
			assertEliminationLosses(tournament, TournamentBracketType.DOUBLE_ELIMINATION, count);
		}
	}

	@Test
	void grandFinalResetsWhenLosersChampionWins() {
		BattleNext_TournamentModel tournament = tournament(2, TournamentBracketType.DOUBLE_ELIMINATION, 1);
		start(tournament);
		engine.recordRound(tournament, instances.get(tournament), "W1-1", "F1", report().getId());
		assertEquals("F2", instances.get(tournament).findMatch("L1-1").getWinnerFleetId());
		engine.recordRound(tournament, instances.get(tournament), "GF", "F2", report().getId());
		assertFalse(instances.get(tournament).isCompleted());
		assertEquals(BattleNext_Match.MatchStage.GRAND_FINAL_RESET, instances.get(tournament).findMatch("GF_RESET").getStage());
		engine.recordRound(tournament, instances.get(tournament), "GF_RESET", "F1", report().getId());
		assertEquals("F1", instances.get(tournament).getChampionFleetId());
	}

	@Test
	void rollbackKeepsIndependentResultsAndClearsDependents() throws Exception {
		for (TournamentBracketType type : new TournamentBracketType[]{TournamentBracketType.SINGLE_ELIMINATION, TournamentBracketType.DOUBLE_ELIMINATION}) {
			BattleNext_TournamentModel tournament = tournament(4, type, 1);
			start(tournament);
			engine.recordRound(tournament, instances.get(tournament), "W1-1", instances.get(tournament).findMatch("W1-1").getPlayer(), report().getId());
			BattleNext_BattleReportModel independent = report();
			independent.setId("independent");
			engine.recordRound(tournament, instances.get(tournament), "W1-2", instances.get(tournament).findMatch("W1-2").getPlayer(), independent.getId());
			BattleNext_BattleReportModel dependent = report();
			dependent.setId("discard-me-123");
			engine.recordRound(tournament, instances.get(tournament), "W2-1", instances.get(tournament).findMatch("W2-1").getPlayer(), dependent.getId());
			engine.rollback(tournament, instances.get(tournament), "W1-1");
			assertEquals(BattleNext_Match.MatchStatus.PENDING, instances.get(tournament).findMatch("W1-1").getStatus());
			assertEquals(BattleNext_Match.MatchStatus.COMPLETE, instances.get(tournament).findMatch("W1-2").getStatus());
			assertEquals(1, instances.get(tournament).findMatch("W1-2").getRounds().size());
			assertEquals("independent", instances.get(tournament).findMatch("W1-2").getRounds().get(0).getReportId());
			assertNull(instances.get(tournament).findMatch("W2-1"));
			assertFalse(instances.get(tournament).toJson().toString().contains("discard-me-123"));
			assertEquals("W1-1", engine.getNextMatch(instances.get(tournament)).getId());
		}
	}

	@Test
	void roundRobinCycleUsesSeedOrderAndKofAdvances() {
		BattleNext_TournamentModel roundRobin = tournament(3, TournamentBracketType.ROUND_ROBIN, 1);
		start(roundRobin);
		engine.recordRound(roundRobin, instances.get(roundRobin), "RR-0-1", "F1", report().getId());
		engine.recordRound(roundRobin, instances.get(roundRobin), "RR-0-2", "F3", report().getId());
		engine.recordRound(roundRobin, instances.get(roundRobin), "RR-1-2", "F2", report().getId());
		assertEquals("F1", instances.get(roundRobin).getChampionFleetId());
		BattleNext_TournamentModel kof = tournament(5, TournamentBracketType.KING_OF_THE_HILL, 1);
		start(kof);
		assertEquals(4, finishByPlayer(kof));
		assertTrue(instances.get(kof).isCompleted());
	}

	@Test
	void seededPairingsAndHeadToHeadRanking() {
		BattleNext_TournamentModel seeded = tournament(8, TournamentBracketType.SINGLE_ELIMINATION, 1);
		start(seeded);
		assertEquals("F1", instances.get(seeded).findMatch("W1-1").getPlayer());
		assertEquals("F8", instances.get(seeded).findMatch("W1-1").getEnemy());
		assertEquals("F4", instances.get(seeded).findMatch("W1-2").getPlayer());
		assertEquals("F5", instances.get(seeded).findMatch("W1-2").getEnemy());
		BattleNext_TournamentModel roundRobin = tournament(4, TournamentBracketType.ROUND_ROBIN, 1);
		start(roundRobin);
		engine.recordRound(roundRobin, instances.get(roundRobin), "RR-0-1", "F2", report().getId());
		engine.recordRound(roundRobin, instances.get(roundRobin), "RR-0-2", "F1", report().getId());
		engine.recordRound(roundRobin, instances.get(roundRobin), "RR-0-3", "F1", report().getId());
		engine.recordRound(roundRobin, instances.get(roundRobin), "RR-1-2", "F3", report().getId());
		engine.recordRound(roundRobin, instances.get(roundRobin), "RR-1-3", "F2", report().getId());
		engine.recordRound(roundRobin, instances.get(roundRobin), "RR-2-3", "F4", report().getId());
		assertEquals("F2", instances.get(roundRobin).getChampionFleetId());
	}

	@Test
	void fleetLimitRejectsExcessParticipants() {
		BattleNext_TournamentModel tournament = tournament(5, TournamentBracketType.SINGLE_ELIMINATION, 1);
		tournament.setMaxFleet(4);
		assertThrows(IllegalArgumentException.class, () -> start(tournament));
	}

	@Test
	void nestedEnumsRejectUnknownValues() throws Exception {
		BattleNext_TournamentModel tournament = tournament(2, TournamentBracketType.SINGLE_ELIMINATION, 1);
		start(tournament);
		BattleNext_Match match = engine.getNextMatch(instances.get(tournament));
		engine.recordRound(tournament, instances.get(tournament), match.getId(), match.getPlayer(), report().getId());
		JSONObject json = instances.get(tournament).toJson();
		JSONObject matchJson = json.getJSONArray("matches").getJSONObject(0);
		matchJson.put("status", "UNKNOWN");
		BattleNext_StartedTournament loaded = new BattleNext_StartedTournament();
		loaded.fromJson(json);
		assertTrue(loaded.getMatches().isEmpty());
		assertTrue(loaded.getLoadErrors().get(0).contains("match.status"));
		matchJson.put("status", "COMPLETE");
		matchJson.getJSONArray("rounds").getJSONObject(0).put("status", "UNKNOWN");
		loaded.fromJson(json);
		assertEquals(1, loaded.getMatches().size());
		assertTrue(loaded.getMatches().get(0).getRounds().isEmpty());
		assertTrue(loaded.getLoadErrors().get(0).contains("round.status"));
		assertEquals(json.toString(), loaded.toJson().toString());
	}

	@Test
	void jsonRoundTripKeepsProgressAndDependencies() throws Exception {
		BattleNext_TournamentModel tournament = tournament(5, TournamentBracketType.DOUBLE_ELIMINATION, 1);
		start(tournament);
		BattleNext_Match first = engine.getNextMatch(instances.get(tournament));
		engine.recordRound(tournament, instances.get(tournament), first.getId(), first.getPlayer(), report().getId());
		BattleNext_TournamentModel restored = new BattleNext_TournamentModel();
		instances.put(restored, instances.get(tournament).copy());
		restored.fromJson(tournament.toJson());
		assertEquals(instances.get(tournament).getPendingMatchId(), instances.get(restored).getPendingMatchId());
		finishByPlayer(restored);
		assertTrue(instances.get(restored).isCompleted());
	}

	private void start(BattleNext_TournamentModel configuration) {
		instances.put(configuration, engine.start(configuration, participants.get(configuration), ExecutionKind.MANUAL));
	}

	private BattleNext_TournamentModel tournament(int count, TournamentBracketType type, int boCount) {
		BattleNext_TournamentModel result = new BattleNext_TournamentModel("test", "Test", type, boCount, count);
		List<String> fleets = new ArrayList<>();
		for (int index = 1; index <= count; index++) {
			fleets.add("F" + index);
		}
		participants.put(result, fleets);
		return result;
	}

	private int finishByPlayer(BattleNext_TournamentModel tournament) {
		int played = 0;
		while (!instances.get(tournament).isCompleted() && played < 2000) {
			BattleNext_Match next = engine.getNextMatch(instances.get(tournament));
			assertNotNull(next, "No pending match before tournament completion");
			engine.recordRound(tournament, instances.get(tournament), next.getId(), next.getPlayer(), report().getId());
			played++;
		}
		assertTrue(played < 2000, "Tournament did not finish");
		return played;
	}

	private BattleNext_BattleReportModel report() {
		return new BattleNext_BattleReportModel();
	}

	@Test
	void sparseSeedsPreservePositionsAndCompleteAllFormatsUsingOnlyRealOpponents() throws Exception {
		for (TournamentBracketType type : TournamentBracketType.values()) {
			BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup", type, 1, 7);
			List<String> slots = new ArrayList<>(List.of("", "F1", "", "F2", "F3", "", ""));
			BattleNext_StartedTournament started = engine.start(configuration, slots, ExecutionKind.MANUAL);
			slots.set(1, "other");
			assertEquals(List.of("", "F1", "", "F2", "F3", "", ""), started.getFleetIds());
			instances.put(configuration, started.copy());
			int played = finishByPlayer(configuration);
			assertEquals(instances.get(configuration).getPlayableMatchTotal(configuration), played);
			assertTrue(List.of("F1", "F2", "F3").contains(instances.get(configuration).getChampionFleetId()));
			for (BattleNext_Match match : instances.get(configuration).getMatches()) {
				if (match.getRounds().isEmpty()) continue;
				assertFalse(match.getPlayer().isEmpty());
				assertFalse(match.getEnemy().isEmpty());
			}
		}
	}

	@Test
	void participantValidationRejectsDuplicateIdentityInsufficientFleetsAndCapacityMismatch() {
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup", TournamentBracketType.SINGLE_ELIMINATION, 1, 4);
		for (List<String> slots : List.of(List.of("a", "a", "", ""), List.of("a", "", "", ""), List.of("a", "b"))) {
			assertThrows(BattleNext_TournamentEngine.ConfigurationRejection.class,
					() -> engine.start(configuration, slots, ExecutionKind.MANUAL));
		}
	}

	private void assertEliminationLosses(BattleNext_TournamentModel tournament, TournamentBracketType type, int count) {
		Map<String, Integer> losses = new HashMap<>();
		for (BattleNext_Match match : instances.get(tournament).getMatches()) {
			if (!match.getLoserFleetId().isEmpty()) {
				losses.merge(match.getLoserFleetId(), 1, Integer::sum);
			}
		}
		for (String fleet : instances.get(tournament).getFleetIds()) {
			if (!fleet.equals(instances.get(tournament).getChampionFleetId())) {
				assertEquals(type == TournamentBracketType.DOUBLE_ELIMINATION ? 2 : 1,
						losses.getOrDefault(fleet, 0), type + " losses for " + count + " fleets: " + fleet);
			}
		}
	}
}
