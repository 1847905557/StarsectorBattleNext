package data.scripts.tournament;

import data.model.report.BattleNext_BattleReportModel;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_Round;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class BattleNext_TournamentEngineTest {

	private final BattleNext_TournamentEngine engine = new BattleNext_TournamentEngine();

	@Test
	void drawAndSideSwapUseOrderedRounds() {
		BattleNext_TournamentModel tournament = tournament(2, TournamentBracketType.SINGLE_ELIMINATION, 4);
		engine.start(tournament);
		BattleNext_Match match = engine.getNextMatch(tournament);
		engine.recordRound(tournament, match.getId(), "draw", report());
		assertEquals(BattleNext_Round.RoundStatus.DRAW, match.getRounds().get(0).getStatus());
		assertEquals("F1", match.getPlayer());
		engine.recordRound(tournament, match.getId(), "F1", report());
		assertEquals("F2", match.getPlayer());
		engine.recordRound(tournament, match.getId(), "F1", report());
		assertEquals(BattleNext_Round.RoundStatus.ENEMY_WIN, match.getRounds().get(2).getStatus());
		assertEquals(2, match.getPlayerWins());
		assertEquals(BattleNext_Match.MatchStatus.PENDING, match.getStatus());
		engine.recordRound(tournament, match.getId(), "F1", report());
		assertEquals("F1", tournament.getChampionFleetId());
		assertEquals(4, match.getRounds().size());
	}

	@Test
	void largeBoCountDoesNotOverflowThreshold() {
		BattleNext_TournamentModel tournament = tournament(2, TournamentBracketType.SINGLE_ELIMINATION, Integer.MAX_VALUE);
		engine.start(tournament);
		BattleNext_Match match = engine.getNextMatch(tournament);
		engine.recordRound(tournament, match.getId(), match.getPlayer(), report());
		assertEquals(BattleNext_Match.MatchStatus.PENDING, match.getStatus());
	}

	@Test
	void playableMatchTotalsUseFormatAndSkipByes() {
		for (int count : new int[]{2, 3, 4, 5, 8, 32}) {
			BattleNext_TournamentModel single = tournament(count, TournamentBracketType.SINGLE_ELIMINATION, 3);
			assertEquals(count - 1, single.getPlayableMatchTotal());
			BattleNext_TournamentModel roundRobin = tournament(count, TournamentBracketType.ROUND_ROBIN, 3);
			assertEquals(count * (count - 1) / 2, roundRobin.getPlayableMatchTotal());
			BattleNext_TournamentModel doubleElimination = tournament(count, TournamentBracketType.DOUBLE_ELIMINATION, 3);
			assertEquals(2 * count - 2, doubleElimination.getPlayableMatchTotal());
		}
		BattleNext_TournamentModel tournament = tournament(4, TournamentBracketType.SINGLE_ELIMINATION, 4);
		engine.start(tournament);
		assertEquals(3, tournament.getPlayableMatchTotal());
		assertEquals(2, tournament.getPlayableMatchCount());
		assertEquals(3, tournament.getWinTarget());
	}

	@Test
	void seededBracketsCompleteForSupportedFleetCounts() {
		for (int count : new int[]{2, 3, 4, 5, 8, 32}) {
			for (TournamentBracketType type : new TournamentBracketType[]{TournamentBracketType.SINGLE_ELIMINATION, TournamentBracketType.DOUBLE_ELIMINATION}) {
				BattleNext_TournamentModel tournament = tournament(count, type, 1);
				engine.start(tournament);
				int played = finishByPlayer(tournament);
				assertTrue(tournament.isCompleted(), type + " with " + count + " fleets");
				assertTrue(tournament.getFleetIds().contains(tournament.getChampionFleetId()));
				assertTrue(played > 0);
				assertNull(engine.getNextMatch(tournament));
				assertEliminationLosses(tournament, type, count);
			}
		}
	}

	@Test
	void doubleEliminationHandlesAlternatingWinners() {
		for (int count : new int[]{2, 3, 4, 5, 8, 32}) {
			BattleNext_TournamentModel tournament = tournament(count, TournamentBracketType.DOUBLE_ELIMINATION, 1);
			engine.start(tournament);
			int played = 0;
			while (!tournament.isCompleted() && played < 2000) {
				BattleNext_Match next = engine.getNextMatch(tournament);
				assertNotNull(next);
				engine.recordRound(tournament, next.getId(), played % 2 == 0 ? next.getEnemy() : next.getPlayer(), report());
				played++;
			}
			assertTrue(tournament.isCompleted(), "Alternating results with " + count + " fleets");
			assertEliminationLosses(tournament, TournamentBracketType.DOUBLE_ELIMINATION, count);
		}
	}

	@Test
	void grandFinalResetsWhenLosersChampionWins() {
		BattleNext_TournamentModel tournament = tournament(2, TournamentBracketType.DOUBLE_ELIMINATION, 1);
		engine.start(tournament);
		engine.recordRound(tournament, "W1-1", "F1", report());
		assertEquals("F2", tournament.findMatch("L1-1").getWinnerFleetId());
		engine.recordRound(tournament, "GF", "F2", report());
		assertFalse(tournament.isCompleted());
		assertEquals(BattleNext_Match.MatchStage.GRAND_FINAL_RESET, tournament.findMatch("GF_RESET").getStage());
		engine.recordRound(tournament, "GF_RESET", "F1", report());
		assertEquals("F1", tournament.getChampionFleetId());
	}

	@Test
	void rollbackKeepsIndependentResultsAndClearsDependents() throws Exception {
		for (TournamentBracketType type : new TournamentBracketType[]{TournamentBracketType.SINGLE_ELIMINATION, TournamentBracketType.DOUBLE_ELIMINATION}) {
			BattleNext_TournamentModel tournament = tournament(4, type, 1);
			engine.start(tournament);
			engine.recordRound(tournament, "W1-1", tournament.findMatch("W1-1").getPlayer(), report());
			BattleNext_BattleReportModel independent = report();
			independent.setId("independent");
			engine.recordRound(tournament, "W1-2", tournament.findMatch("W1-2").getPlayer(), independent);
			BattleNext_BattleReportModel dependent = report();
			dependent.setId("discard-me-123");
			engine.recordRound(tournament, "W2-1", tournament.findMatch("W2-1").getPlayer(), dependent);
			engine.rollback(tournament, "W1-1");
			assertEquals(BattleNext_Match.MatchStatus.PENDING, tournament.findMatch("W1-1").getStatus());
			assertEquals(BattleNext_Match.MatchStatus.COMPLETE, tournament.findMatch("W1-2").getStatus());
			assertEquals(1, tournament.findMatch("W1-2").getRounds().size());
			assertEquals("independent", tournament.findMatch("W1-2").getRounds().get(0).getReport().getId());
			assertNull(tournament.findMatch("W2-1"));
			assertFalse(tournament.toJson().toString().contains("discard-me-123"));
			assertEquals("W1-1", engine.getNextMatch(tournament).getId());
		}
	}

	@Test
	void roundRobinCycleUsesSeedOrderAndKofAdvances() {
		BattleNext_TournamentModel roundRobin = tournament(3, TournamentBracketType.ROUND_ROBIN, 1);
		engine.start(roundRobin);
		engine.recordRound(roundRobin, "RR-0-1", "F1", report());
		engine.recordRound(roundRobin, "RR-0-2", "F3", report());
		engine.recordRound(roundRobin, "RR-1-2", "F2", report());
		assertEquals("F1", roundRobin.getChampionFleetId());
		BattleNext_TournamentModel kof = tournament(5, TournamentBracketType.KING_OF_THE_HILL, 1);
		engine.start(kof);
		assertEquals(4, finishByPlayer(kof));
		assertTrue(kof.isCompleted());
	}

	@Test
	void seededPairingsAndHeadToHeadRanking() {
		BattleNext_TournamentModel seeded = tournament(8, TournamentBracketType.SINGLE_ELIMINATION, 1);
		engine.start(seeded);
		assertEquals("F1", seeded.findMatch("W1-1").getPlayer());
		assertEquals("F8", seeded.findMatch("W1-1").getEnemy());
		assertEquals("F4", seeded.findMatch("W1-2").getPlayer());
		assertEquals("F5", seeded.findMatch("W1-2").getEnemy());
		BattleNext_TournamentModel roundRobin = tournament(4, TournamentBracketType.ROUND_ROBIN, 1);
		engine.start(roundRobin);
		engine.recordRound(roundRobin, "RR-0-1", "F2", report());
		engine.recordRound(roundRobin, "RR-0-2", "F1", report());
		engine.recordRound(roundRobin, "RR-0-3", "F1", report());
		engine.recordRound(roundRobin, "RR-1-2", "F3", report());
		engine.recordRound(roundRobin, "RR-1-3", "F2", report());
		engine.recordRound(roundRobin, "RR-2-3", "F4", report());
		assertEquals("F2", roundRobin.getChampionFleetId());
	}

	@Test
	void fleetLimitRejectsExcessParticipants() {
		BattleNext_TournamentModel tournament = tournament(5, TournamentBracketType.SINGLE_ELIMINATION, 1);
		tournament.setMaxFleet(4);
		assertThrows(IllegalArgumentException.class, () -> engine.start(tournament));
	}

	@Test
	void nestedEnumsRejectUnknownValues() throws Exception {
		BattleNext_TournamentModel tournament = tournament(2, TournamentBracketType.SINGLE_ELIMINATION, 1);
		engine.start(tournament);
		BattleNext_Match match = engine.getNextMatch(tournament);
		engine.recordRound(tournament, match.getId(), match.getPlayer(), report());
		JSONObject json = tournament.toJson();
		JSONObject matchJson = json.getJSONArray("matches").getJSONObject(0);
		matchJson.put("status", "UNKNOWN");
		assertThrows(IllegalArgumentException.class, () -> new BattleNext_TournamentModel().fromJson(json));
		matchJson.put("status", "COMPLETE");
		matchJson.getJSONArray("rounds").getJSONObject(0).put("status", "UNKNOWN");
		assertThrows(IllegalArgumentException.class, () -> new BattleNext_TournamentModel().fromJson(json));
	}

	@Test
	void jsonRoundTripKeepsProgressAndDependencies() throws Exception {
		BattleNext_TournamentModel tournament = tournament(5, TournamentBracketType.DOUBLE_ELIMINATION, 1);
		engine.start(tournament);
		BattleNext_Match first = engine.getNextMatch(tournament);
		engine.recordRound(tournament, first.getId(), first.getPlayer(), report());
		BattleNext_TournamentModel restored = new BattleNext_TournamentModel();
		restored.fromJson(tournament.toJson());
		assertEquals(tournament.getPendingMatchId(), restored.getPendingMatchId());
		finishByPlayer(restored);
		assertTrue(restored.isCompleted());
	}

	private BattleNext_TournamentModel tournament(int count, TournamentBracketType type, int boCount) {
		BattleNext_TournamentModel result = new BattleNext_TournamentModel("test", "Test", type, boCount, count);
		List<String> fleets = new ArrayList<>();
		for (int index = 1; index <= count; index++) fleets.add("F" + index);
		result.setFleetIds(fleets);
		return result;
	}

	private int finishByPlayer(BattleNext_TournamentModel tournament) {
		int played = 0;
		while (!tournament.isCompleted() && played < 2000) {
			BattleNext_Match next = engine.getNextMatch(tournament);
			assertNotNull(next, "No pending match before tournament completion");
			engine.recordRound(tournament, next.getId(), next.getPlayer(), report());
			played++;
		}
		assertTrue(played < 2000, "Tournament did not finish");
		return played;
	}

	private BattleNext_BattleReportModel report() {
		return new BattleNext_BattleReportModel();
	}

	private void assertEliminationLosses(BattleNext_TournamentModel tournament, TournamentBracketType type, int count) {
		Map<String, Integer> losses = new HashMap<>();
		for (BattleNext_Match match : tournament.getMatches()) {
			if (!match.getLoserFleetId().isEmpty()) {
				losses.merge(match.getLoserFleetId(), 1, Integer::sum);
			}
		}
		for (String fleet : tournament.getFleetIds()) {
			if (!fleet.equals(tournament.getChampionFleetId())) {
				assertEquals(type == TournamentBracketType.DOUBLE_ELIMINATION ? 2 : 1,
						losses.getOrDefault(fleet, 0), type + " losses for " + count + " fleets: " + fleet);
			}
		}
	}
}
