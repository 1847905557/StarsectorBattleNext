package data.scripts.report;

import data.model.report.BattleNext_BattleReportModel;
import data.scripts.battle.BattleNext_BattleResultRecorder;
import data.scripts.battle.BattleNext_Round;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_MemberReportTest {

	@Test
	void sourceBucketsAccumulateFourComponentsAndTotalsExcludeEmp() throws Exception {
		BattleNext_MemberReport member = new BattleNext_MemberReport("wolf_CS", "Wolf");
		member.addDamageDealt("weapon", "lightmg", 10d, 20d, 30d, 40d);
		member.addDamageDealt("weapon", "lightmg", 5d, 6d, 7d, 8d);
		member.addDamageDealt("fighter_wing", "talon_wing", 3d, 4d, 5d, 6d);
		member.addDamageTaken("weapon", "heavyblaster", 1d, 2d, 3d, 4d);

		assertEquals(2, member.getDealtBySource().size());
		assertEquals(90d, member.getDamageDealt());
		assertEquals(30d, member.getArmorDamageDealt());
		assertEquals(54d, member.getEmpDamageDealt());
		assertEquals(6d, member.getDamageTaken());
		BattleNext_MemberReport loaded = BattleNext_MemberReport.fromJson(member.toJson());
		assertEquals(member.getDamageDealt(), loaded.getDamageDealt());
		assertEquals(2, loaded.getDealtBySource().size());
		assertEquals("lightmg", loaded.getDealtBySource().get(0).getId());
	}

	@Test
	void reportFormatOneStoresNameAndOldShapesFailParsing() throws Exception {
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId("report-1");
		report.setName("Alpha vs Beta");
		report.addPlayerMember(new BattleNext_MemberReport("wolf_CS", "Wolf"));
		JSONObject json = report.toJson();

		assertEquals(1, json.getInt("formatVersion"));
		BattleNext_BattleReportModel loaded = new BattleNext_BattleReportModel();
		loaded.fromJson(json);
		assertEquals("Alpha vs Beta", loaded.getName());
		assertEquals(1, loaded.getPlayerMembers().size());
		assertThrows(JSONException.class, () -> new BattleNext_BattleReportModel().fromJson(
				new JSONObject("{\"formatVersion\":1,\"id\":\"old\"}")));
		assertThrows(JSONException.class, () -> BattleNext_Round.fromJson(new JSONObject(
				"{\"player\":\"a\",\"enemy\":\"b\",\"status\":\"DRAW\","
						+ "\"report\":{\"formatVersion\":1,\"id\":\"old\"}}")));
	}

	@Test
	void defaultNameContainsFleetNamesMarkerAndLocalTimestamp() {
		for (String marker : new String[]{"PvE", "Quick Match 2 Round 1",
				"Tournament Cup Match GF Round 3"}) {
			String name = BattleNext_BattleResultRecorder.defaultName("Alpha", "Beta", marker,
					1_700_000_000_000L);
			assertTrue(name.startsWith("Alpha vs Beta - " + marker + " - "));
			assertTrue(name.matches(".*\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$"));
		}
	}
}
