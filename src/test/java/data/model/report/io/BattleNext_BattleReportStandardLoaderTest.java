package data.model.report.io;

import data.model.BattleNext_LoadSource;
import data.model.report.BattleNext_BattleReportModel;
import data.test.fixture.BattleNext_TestGameFixture;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.json.JSONObject;
import org.json.JSONException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BattleNext_BattleReportStandardLoaderTest extends BattleNext_TestGameFixture {

	@Test
	void missingOptionalReportDirectoryLoadsAnEmptyCatalog() throws Exception {
		assertFalse(Files.exists(directory.resolve("tournament/_reports")));
		assertEquals(List.of(), new BattleNext_BattleReportStandardLoader().load());
		assertEquals(List.of(), dataReads);
	}

	@Test
	void reportsSortByTimestampWithStableTieOrderAndMarkTheirSource() throws Exception {
		var root = directory.resolve("tournament/_reports");
		Files.createDirectories(root);
		for (String id : List.of("a", "b", "c")) {
			var report = new BattleNext_BattleReportModel();
			report.setId(id);
			report.setCreatedAt(id.equals("c") ? 1 : 2);
			Files.writeString(root.resolve(id + ".json"), report.toJson().toString(), StandardCharsets.UTF_8);
		}
		Files.createDirectories(root.resolve("nested"));
		Files.writeString(root.resolve("nested/ignored.json"), "broken", StandardCharsets.UTF_8);
		Files.writeString(root.resolve("ignored.txt"), "broken", StandardCharsets.UTF_8);
		var reports = new BattleNext_BattleReportStandardLoader().load();
		assertEquals(List.of("a", "b", "c"), reports.stream().map(report -> report.getId()).toList());
		assertEquals(BattleNext_LoadSource.STANDARD, reports.get(0).getSourceType());
		assertEquals("tournament/_reports/a.json", reports.get(0).getSourcePath());
	}

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
