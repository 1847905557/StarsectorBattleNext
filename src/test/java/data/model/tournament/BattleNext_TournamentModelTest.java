package data.model.tournament;

import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import org.json.JSONException;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BattleNext_TournamentModelTest {

	@Test
	void configurationRoundTripPreservesInputs() throws Exception {
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup 中文 50%", TournamentBracketType.DOUBLE_ELIMINATION, 5, 3);
		configuration.setRuleId("rule");
		configuration.setMapId(8);
		BattleNext_TournamentModel restored = new BattleNext_TournamentModel();
		restored.fromJson(configuration.toJson());
		assertEquals(configuration.toJson().toString(), restored.toJson().toString());
		assertEquals("cup", restored.getId());
		assertEquals("Cup 中文 50%", restored.getName());
		assertEquals(TournamentBracketType.DOUBLE_ELIMINATION, restored.getBracketType());
		assertEquals(5, restored.getBoCount());
		assertEquals(3, restored.getMaxFleet());
		assertEquals("rule", restored.getRuleId());
		assertEquals(8, restored.getMapId());
		assertEquals(3, restored.getWinTarget());
		restored.setMaxFleet(4);
		assertEquals(3, configuration.getMaxFleet());
	}

	@ParameterizedTest
	@ValueSource(strings = {"enum", "bo", "min", "max", "missing"})
	void invalidConfigurationParsingPreservesPublishedFields(String failure) throws Exception {
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("original", "Original", TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		String before = configuration.toJson().toString();
		JSONObject json = configuration.toJson().put("id", "replacement").put("name", "Replacement");
		switch (failure) {
			case "enum" -> json.put("bracketType", "INVALID");
			case "bo" -> json.put("boCount", 0);
			case "min" -> json.put("maxFleet", 1);
			case "max" -> json.put("maxFleet", 33);
			case "missing" -> json.remove("boCount");
		}
		Class<? extends Exception> exceptionType = failure.equals("missing") ? JSONException.class : IllegalArgumentException.class;
		var exception = assertThrows(exceptionType, () -> configuration.fromJson(json));
		if (failure.equals("missing")) {
			assertTrue(exception.getMessage().contains("boCount"));
		} else {
			assertEquals(failure.equals("enum") ? "Invalid tournament.bracketType: INVALID" : "Invalid tournament limits", exception.getMessage());
		}
		assertEquals(before, configuration.toJson().toString());
	}

	@Test
	void configurationComputesLargeBoThreshold() {
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup", TournamentBracketType.SINGLE_ELIMINATION, Integer.MAX_VALUE, 4);
		assertEquals(1073741824, configuration.getWinTarget());
	}
}
