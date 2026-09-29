package data.model.report.io;

import org.json.JSONObject;
import org.json.JSONException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BattleNext_BattleReportStandardLoaderTest {

	@Test
	void reportsUseFormatOneAndMissingRequiredDataFailsParsing() throws Exception {
		JSONObject old = new JSONObject("{\"formatVersion\":1,\"id\":\"old\"}");
		JSONObject current = new JSONObject("{\"formatVersion\":1,\"id\":\"new\","
				+ "\"name\":\"New battle\",\"playerMembers\":[],\"enemyMembers\":[]}");

		assertThrows(JSONException.class, () -> BattleNext_BattleReportStandardLoader.parseReport(old));
		assertEquals("new", BattleNext_BattleReportStandardLoader.parseReport(current).getId());
		assertEquals("New battle", BattleNext_BattleReportStandardLoader.parseReport(current).getName());
	}
}
