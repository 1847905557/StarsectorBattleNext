package data.model.report;

import data.scripts.report.BattleNext_MemberReport;
import org.json.JSONArray;
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
		var player = new BattleNext_MemberReport("local_wolf", "wolf", "Player");
		player.addDamageDealt("weapon", "laser", 1, 2, 3, 4);
		player.setFinalState(0.5f, 0.7f, "alive");
		var enemy = new BattleNext_MemberReport("local_lasher", "lasher", "Enemy");
		enemy.addDamageTaken("weapon", "laser", 1, 2, 3, 4);
		enemy.setFinalState(0, 0.1f, "destroyed");
		report.addPlayerMember(player);
		report.addEnemyMember(enemy);
		var loaded = new BattleNext_BattleReportModel();
		loaded.fromJson(report.toJson());
		assertEquals(report.toJson().toString(), loaded.toJson().toString());
		assertEquals("report", loaded.getId());
		assertEquals("Battle", loaded.getName());
		assertEquals("player", loaded.getWinner());
		assertEquals(30000L, loaded.getDurationMillis());
		var restoredPlayer = loaded.getPlayerMembers().get(0);
		var restoredEnemy = loaded.getEnemyMembers().get(0);
		assertEquals("local_wolf", restoredPlayer.getVariantId());
		assertEquals("Player", restoredPlayer.getName());
		assertEquals(0.5f, restoredPlayer.getFinalHullFraction());
		assertEquals(0.7f, restoredPlayer.getFinalCr());
		assertEquals("alive", restoredPlayer.getStatus());
		assertEquals("local_lasher", restoredEnemy.getVariantId());
		assertEquals("Enemy", restoredEnemy.getName());
		assertEquals(0f, restoredEnemy.getFinalHullFraction());
		assertEquals(0.1f, restoredEnemy.getFinalCr());
		assertEquals("destroyed", restoredEnemy.getStatus());
		var dealt = restoredPlayer.getDealtBySource().get(0);
		var taken = restoredEnemy.getTakenBySource().get(0);
		assertEquals("weapon", dealt.getKind());
		assertEquals("laser", dealt.getId());
		assertEquals("weapon", taken.getKind());
		assertEquals("laser", taken.getId());
		assertEquals(1d, dealt.getHull());
		assertEquals(2d, dealt.getArmor());
		assertEquals(3d, dealt.getShields());
		assertEquals(4d, dealt.getEmp());
		assertEquals(1d, taken.getHull());
		assertEquals(2d, taken.getArmor());
		assertEquals(3d, taken.getShields());
		assertEquals(4d, taken.getEmp());
		assertEquals("wolf", loaded.getPlayerMembers().get(0).getHullId());
		assertEquals("lasher", loaded.getEnemyMembers().get(0).getHullId());
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
	void repeatedParsingPublishesValidMembersAndPreservesMalformedRawItems() throws Exception {
		var report = new BattleNext_BattleReportModel();
		report.setId("original");
		report.setName("Original");
		report.addPlayerMember(new BattleNext_MemberReport("wolf_CS", "wolf", "First"));
		JSONObject json = report.toJson();
		report.fromJson(json);
		assertEquals(1, report.getPlayerMembers().size());
		JSONObject invalid = new JSONObject(json.toString()).put("id", "replacement").put("name", "Replacement");
		invalid.put("enemyMembers", new JSONArray().put(new JSONObject().put("name", "Broken")));
		report.fromJson(invalid);
		assertEquals("replacement", report.getId());
		assertEquals("Replacement", report.getName());
		assertEquals(1, report.getPlayerMembers().size());
		assertTrue(report.getEnemyMembers().isEmpty());
		assertTrue(report.getLoadErrors().get(0).contains("enemyMembers[0]"));
		assertEquals(invalid.toString(), report.toJson().toString());
		json.put("playerMembers", new JSONArray());
		report.fromJson(json);
		assertTrue(report.getPlayerMembers().isEmpty());
		assertTrue(report.getLoadErrors().isEmpty());
	}
}
