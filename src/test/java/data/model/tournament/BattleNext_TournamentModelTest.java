package data.model.tournament;

import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import org.json.JSONException;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
		assertThrows(exceptionType, () -> configuration.fromJson(json));
		assertEquals(before, configuration.toJson().toString());
	}

	@Test
	void configurationComputesLargeBoThreshold() {
		for (TournamentBracketType bracket : TournamentBracketType.values()) {
			BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup", bracket, Integer.MAX_VALUE, 4);
			assertEquals(1073741824, configuration.getWinTarget());
		}
	}
}
