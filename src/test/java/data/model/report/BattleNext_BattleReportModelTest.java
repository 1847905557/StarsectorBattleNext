package data.model.report;

import data.scripts.report.BattleNext_MemberReport;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BattleNext_BattleReportModelTest {

	@Test
	void roundTripPreservesBothSidesAndNestedDamageSources() throws Exception {

		var report = new BattleNext_BattleReportModel();
		report.setId(" report ");
		report.setName(" Battle ");
		report.setWinner(" player ");
		report.setCreatedAt(5000000000L);
		report.setDurationMillis(30000L);
		var player = new BattleNext_MemberReport("wolf", "Player");
		player.addDamageDealt("weapon", "laser", 1, 2, 3, 4);
		player.setFinalState(0.5f, 0.7f, "alive");
		var enemy = new BattleNext_MemberReport("lasher", "Enemy");
		enemy.addDamageTaken("weapon", "laser", 1, 2, 3, 4);
		enemy.setFinalState(0, 0.1f, "destroyed");
		report.addPlayerMember(player);
		report.addEnemyMember(enemy);
		var loaded = new BattleNext_BattleReportModel();
		loaded.fromJson(report.toJson());
		assertEquals(report.toJson().toString(), loaded.toJson().toString());
		assertEquals(6d, loaded.getPlayerMembers().get(0).getDamageDealt());
		assertEquals(4d, loaded.getEnemyMembers().get(0).getEmpDamageTaken());
		assertEquals(5000000000L, loaded.getCreatedAt());
		assertThrows(UnsupportedOperationException.class, () -> loaded.getPlayerMembers().clear());
		assertThrows(UnsupportedOperationException.class, () -> loaded.getEnemyMembers().clear());
	}

	@Test
	void defaultsAndNonNegativeTimesRemainExplicit() throws Exception {
		var report = new BattleNext_BattleReportModel();
		report.setWinner(" ");
		report.setDurationMillis(-1);
		report.setCreatedAt(-1);
		assertEquals("draw", report.getWinner());
		assertEquals(0, report.getDurationMillis());
		assertEquals(0, report.getCreatedAt());
		report.fromJson(new JSONObject().put("name", "Minimal").put("playerMembers", new JSONArray()).put("enemyMembers", new JSONArray()));
		assertEquals("draw", report.getWinner());
		assertEquals(0, report.getDurationMillis());
		assertEquals("", report.getId());
	}

	@Test
	void repeatedParsingReplacesMembersAndLateFailureKeepsEveryField() throws Exception {
		var report = new BattleNext_BattleReportModel();
		report.setId("original");
		report.setName("Original");
		report.addPlayerMember(new BattleNext_MemberReport("wolf", "First"));
		JSONObject json = report.toJson();
		report.fromJson(json);
		assertEquals(1, report.getPlayerMembers().size());
		String before = report.toJson().toString();
		JSONObject invalid = new JSONObject(json.toString()).put("id", "replacement").put("name", "Replacement");
		invalid.put("enemyMembers", new JSONArray().put(new JSONObject().put("name", "Broken")));
		assertThrows(JSONException.class, () -> report.fromJson(invalid));
		assertEquals(before, report.toJson().toString());
		json.put("playerMembers", new JSONArray());
		report.fromJson(json);
		assertTrue(report.getPlayerMembers().isEmpty());
		assertThrows(IllegalArgumentException.class, () -> report.fromJson(json.put("formatVersion", 2)));
	}
}
