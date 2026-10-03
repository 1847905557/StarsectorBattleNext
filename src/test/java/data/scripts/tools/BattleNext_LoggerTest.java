package data.scripts.tools;

import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class BattleNext_LoggerTest {

	@Test
	void fieldsFreezeNestedValuesAndKeepStructuredData() throws Exception {
		Map<String, Object> nested = new LinkedHashMap<>();
		nested.put("value", "中文 / quote \" / percent %");
		BattleNext_Logger.Fields fields = BattleNext_Logger.fields()
				.put("items", List.of("alpha", nested))
				.put("empty", null)
				.build();
		JSONObject entry = new JSONObject(BattleNext_Logger.format(BattleNext_LoggerTest.class,
				"test.snapshot", "observed", fields, null));

		assertEquals("data.scripts.tools.BattleNext_LoggerTest", entry.getString("source"));
		assertEquals("test.snapshot", entry.getString("event"));
		assertEquals("中文 / quote \" / percent %",
				entry.getJSONObject("fields").getJSONArray("items").getJSONObject(1).getString("value"));
		assertTrue(entry.getJSONObject("fields").isNull("empty"));
	}

	@Test
	void exceptionSummaryContainsOnlyTypeAndMessage() throws Exception {
		JSONObject entry = new JSONObject(BattleNext_Logger.format(BattleNext_LoggerTest.class,
				"test.failed", "failed", BattleNext_Logger.fields().put("id", "alpha").build(),
				new IllegalStateException("中文 failure")));

		JSONObject exception = entry.getJSONObject("exception");
		assertEquals(IllegalStateException.class.getName(), exception.getString("type"));
		assertEquals("中文 failure", exception.getString("message"));
		assertEquals(2, exception.length());
	}
}
