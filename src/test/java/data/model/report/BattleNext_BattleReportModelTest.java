package data.model.report;

import data.scripts.report.BattleNext_MemberReport;
import data.scripts.report.BattleNext_DamageSourceReport;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;

class BattleNext_BattleReportModelTest {

	@Test
	void failedDetailParsingKeepsTheDirectorySnapshotAndCanBeRetried() throws Exception {

		JSONObject metadata = new JSONObject().put("id", "report").put("name", "Directory");
		JSONObject complete = new JSONObject(metadata.toString()).put("playerMembers", new JSONArray()).put("enemyMembers", new JSONArray());
		AtomicInteger attempts = new AtomicInteger();
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.fromDirectory(metadata, 2, () -> attempts.incrementAndGet() == 1 ? metadata : complete);
		assertThrows(IllegalStateException.class, report::prepareDetails);
		assertEquals(2, report.getMemberCount());
		assertEquals("Directory", report.getName());

		report.prepareDetails();
		assertEquals(2, attempts.get());
		assertEquals(0, report.getMemberCount());

		report.prepareDetails();
		assertEquals(2, attempts.get());

		metadata.put("formatVersion", 2);
		assertThrows(IllegalArgumentException.class, () -> report.fromDirectory(metadata, 0, () -> complete));
	}

	@Test
	void roundTripPreservesBothSidesAndNestedDamageSources() throws Exception {

		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId(" report ");
		report.setName(" Battle ");
		report.setWinner(" player ");
		report.setCreatedAt(5000000000L);
		report.setDurationMillis(30000L);
		BattleNext_MemberReport player = new BattleNext_MemberReport("local_wolf", "wolf", "Player");
		player.addDamageDealt("weapon", "laser", 1, 2, 3, 4);
		player.setFinalState(0.5f, 0.7f, "alive");
		BattleNext_MemberReport enemy = new BattleNext_MemberReport("local_lasher", "lasher", "Enemy");
		enemy.addDamageTaken("weapon", "laser", 1, 2, 3, 4);
		enemy.setFinalState(0, 0.1f, "destroyed");
		report.addPlayerMember(player);
		report.addEnemyMember(enemy);

		BattleNext_BattleReportModel loaded = new BattleNext_BattleReportModel();
		loaded.fromJson(report.toJson());
		assertEquals(report.toJson().toString(), loaded.toJson().toString());
		assertEquals("report", loaded.getId());
		assertEquals("Battle", loaded.getName());
		assertEquals("player", loaded.getWinner());
		assertEquals(30000L, loaded.getDurationMillis());

		BattleNext_MemberReport restoredPlayer = loaded.getPlayerMembersReadonly().get(0);
		BattleNext_MemberReport restoredEnemy = loaded.getEnemyMembersReadonly().get(0);
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

		BattleNext_DamageSourceReport dealt = restoredPlayer.getDealtBySourceCopy().get(0);
		BattleNext_DamageSourceReport taken = restoredEnemy.getTakenBySourceCopy().get(0);
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
		assertEquals("wolf", loaded.getPlayerMembersReadonly().get(0).getHullId());
		assertEquals("lasher", loaded.getEnemyMembersReadonly().get(0).getHullId());
		assertEquals(6d, loaded.getPlayerMembersReadonly().get(0).getDamageDealt());
		assertEquals(4d, loaded.getEnemyMembersReadonly().get(0).getEmpDamageTaken());
		assertEquals(5000000000L, loaded.getCreatedAt());
		assertThrows(UnsupportedOperationException.class, () -> loaded.getPlayerMembersReadonly().clear());
		assertThrows(UnsupportedOperationException.class, () -> loaded.getEnemyMembersReadonly().clear());
	}

	@Test
	void defaultsAndNonNegativeTimesRemainExplicit() throws Exception {
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
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

		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId("original");
		report.setName("Original");
		report.addPlayerMember(new BattleNext_MemberReport("wolf_CS", "wolf", "First"));
		JSONObject json = report.toJson();
		report.fromJson(json);
		assertEquals(1, report.getPlayerMembersReadonly().size());

		JSONObject invalid = new JSONObject(json.toString()).put("id", "replacement").put("name", "Replacement");
		invalid.put("enemyMembers", new JSONArray().put(new JSONObject().put("name", "Broken")));
		report.fromJson(invalid);
		assertEquals("replacement", report.getId());
		assertEquals("Replacement", report.getName());
		assertEquals(1, report.getPlayerMembersReadonly().size());
		assertTrue(report.getEnemyMembersReadonly().isEmpty());
		assertTrue(report.getLoadErrorsCopy().get(0).contains("enemyMembers[0]"));
		assertEquals(invalid.toString(), report.toJson().toString());

		json.put("playerMembers", new JSONArray());
		report.fromJson(json);
		assertTrue(report.getPlayerMembersReadonly().isEmpty());
		assertTrue(report.getLoadErrorsCopy().isEmpty());
	}
}
