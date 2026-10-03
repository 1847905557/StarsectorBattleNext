package data.model.tournament;

import data.model.BattleNext_LoadSource;
import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_Round;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_TournamentModelTest {

	@ParameterizedTest
	@EnumSource(TournamentBracketType.class)
	void roundTripPreservesBracketProgressDependenciesAndReports(TournamentBracketType type) throws Exception {

		var tournament = new BattleNext_TournamentModel("id", "Name", type, 5, 3);
		tournament.setFleetIds(List.of("a", "b", "c"));
		tournament.setRuleId("rule");
		tournament.setMapId(8);
		var match = new BattleNext_Match("match", "a", "b", 8, 1.5f);
		match.addDependency("previous");
		var round = new BattleNext_Round("a", "b");
		round.setStatus(BattleNext_Round.RoundStatus.PLAYER_WIN);
		round.getReport().setName("Round report");
		match.addRound(round);
		match.setStatus(BattleNext_Match.MatchStatus.COMPLETE);
		tournament.addMatch(match);
		tournament.setStarted(true);
		tournament.setCompleted(true);
		tournament.setChampionFleetId("a");
		tournament.setPendingMatchId("match");
		var loaded = new BattleNext_TournamentModel();
		loaded.fromJson(tournament.toJson());
		assertEquals(tournament.toJson().toString(), loaded.toJson().toString());
		assertEquals(3, loaded.getWinTarget());
		assertEquals("a", loaded.findMatch("match").getWinnerFleetId());
		assertEquals("Round report", loaded.findMatch("match").getRounds().get(0).getReport().getName());
		assertThrows(UnsupportedOperationException.class, () -> loaded.getFleetIds().clear());
		assertThrows(UnsupportedOperationException.class, () -> loaded.getMatches().clear());
	}

	@ParameterizedTest
	@ValueSource(strings = {"enum", "bo", "min", "max", "fleets", "match", "missing", "version"})
	void failedParsingPreservesAllFieldsAndCollections(String failure) throws Exception {

		var tournament = new BattleNext_TournamentModel("original", "Original", TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		tournament.setFleetIds(List.of("a", "b"));
		tournament.addMatch(new BattleNext_Match("old", "a", "b", 1, 1));
		String before = tournament.toJson().toString();
		JSONObject json = tournament.toJson().put("id", "replacement").put("name", "Replacement");
		switch (failure) {
			case "enum" -> json.put("bracketType", "INVALID");
			case "bo" -> json.put("boCount", 0);
			case "min" -> json.put("maxFleet", 1);
			case "max" -> json.put("maxFleet", 33);
			case "fleets" -> json.put("fleetIds", new JSONArray(List.of("a", "b", "c")));
			case "match" -> json.put("matches", new JSONArray().put(new BattleNext_Match("new", "a", "b", 1, 1).toJson()).put(new JSONObject().put("id", "broken")));
			case "missing" -> json.remove("boCount");
			case "version" -> json.put("formatVersion", 2);
		}
		Class<? extends Exception> exceptionType = failure.equals("missing") || failure.equals("match")
				? JSONException.class : IllegalArgumentException.class;
		assertThrows(exceptionType, () -> tournament.fromJson(json));
		assertEquals(before, tournament.toJson().toString());
		assertEquals("old", tournament.getMatches().get(0).getId());
	}

	@Test
	void repeatedParsingReplacesProgressAndCarriesCurrentSourceToNewMatches() throws Exception {
		var tournament = new BattleNext_TournamentModel();
		tournament.setSourceType(BattleNext_LoadSource.STANDARD);
		tournament.setSourcePath("tournament.json");
		var json = new BattleNext_TournamentModel("new", "New", TournamentBracketType.ROUND_ROBIN, 3, 2).toJson();
		json.put("matches", new JSONArray().put(new BattleNext_Match("new", "a", "b", 1, 1).toJson()));
		tournament.fromJson(json);
		assertEquals(BattleNext_LoadSource.STANDARD, tournament.findMatch("new").getSourceType());
		assertEquals("tournament.json", tournament.findMatch("new").getSourcePath());
		tournament.fromJson(json);
		assertEquals(1, tournament.getMatches().size());
		json.put("matches", new JSONArray());
		tournament.fromJson(json);
		assertTrue(tournament.getMatches().isEmpty());
	}

	@Test
	void draftAndGeneratedMatchMutationsRespectTheirLifecycle() {

		var tournament = new BattleNext_TournamentModel();
		List<String> teams = new ArrayList<>(List.of(" a ", "b"));
		tournament.setFleetIds(teams);
		teams.clear();
		tournament.setFleetIds(tournament.getFleetIds());
		assertEquals(List.of("a", "b"), tournament.getFleetIds());
		var draft = new BattleNext_Match("draft", "a", "b", 1, 1);
		tournament.addMatch(draft);
		tournament.setSourceType(BattleNext_LoadSource.STANDARD);
		tournament.setSourcePath("source.json");
		assertEquals("source.json", draft.getSourcePath());
		tournament.setStarted(true);
		assertThrows(IllegalStateException.class, () -> tournament.setFleetIds(List.of("new")));
		tournament.addFleetId("new");
		tournament.addMatch(new BattleNext_Match("blocked", "a", "b", 1, 1));
		tournament.clearMatches();
		assertEquals(List.of("a", "b"), tournament.getFleetIds());
		assertEquals(List.of(draft), tournament.getMatches());
		var generated = new BattleNext_Match("generated", "b", "a", 1, 1);
		tournament.addGeneratedMatch(generated);
		assertEquals(BattleNext_LoadSource.STANDARD, generated.getSourceType());
		assertEquals("source.json", generated.getSourcePath());
		tournament.setPendingMatchId("generated");
		tournament.clearGeneratedMatches();
		assertTrue(tournament.getMatches().isEmpty());
		assertEquals("", tournament.getPendingMatchId());
	}

	@Test
	void playableIndexesSkipByesAndTotalsIncludeFinalReset() {
		var tournament = new BattleNext_TournamentModel("id", "Name", TournamentBracketType.DOUBLE_ELIMINATION, Integer.MAX_VALUE, 4);
		tournament.setFleetIds(List.of("a", "b", "c", "d"));
		tournament.addMatch(new BattleNext_Match("bye", "a", "", 1, 1));
		tournament.addMatch(new BattleNext_Match("first", "a", "b", 1, 1));
		tournament.addMatch(new BattleNext_Match("second", "c", "d", 1, 1));
		assertEquals(2, tournament.getPlayableMatchCount());
		assertEquals(0, tournament.getPlayableMatchIndex("first"));
		assertEquals(1, tournament.getPlayableMatchIndex("second"));
		assertEquals(-1, tournament.getPlayableMatchIndex("bye"));
		assertEquals(-1, tournament.getPlayableMatchIndex("missing"));
		assertEquals(6, tournament.getPlayableMatchTotal());
		tournament.addGeneratedMatch(new BattleNext_Match("GF_RESET", "a", "b", 1, 1));
		assertEquals(7, tournament.getPlayableMatchTotal());
		assertEquals(1073741824, tournament.getWinTarget());
	}
}
