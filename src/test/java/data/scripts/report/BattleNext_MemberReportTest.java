package data.scripts.report;

import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import java.util.List;

class BattleNext_MemberReportTest {

	@Test
	void sourceBucketsAccumulateFourComponentsAndTotalsExcludeEmp() throws Exception {
		BattleNext_MemberReport member = new BattleNext_MemberReport("local_wolf_CS", "wolf", "Wolf");
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
		assertEquals("local_wolf_CS", loaded.getVariantId());
		assertEquals("wolf", loaded.getHullId());
		assertEquals(member.getDamageDealt(), loaded.getDamageDealt());
		assertEquals(2, loaded.getDealtBySource().size());
		assertEquals("lightmg", loaded.getDealtBySource().get(0).getId());
	}

	@Test
	void malformedAndConflictingDamageSourcesRetainRawStatisticsAndPublishHealthySources() throws Exception {
		var member = new BattleNext_MemberReport("wolf_CS", "wolf", "Wolf");
		member.addDamageDealt("weapon", "laser", 1, 2, 3, 4);
		JSONObject laser = member.toJson().getJSONArray("dealtBySource").getJSONObject(0);
		JSONObject healthy = new BattleNext_DamageSourceReport("weapon", "healthy").toJson();
		JSONObject raw = member.toJson().put("dealtBySource", new JSONArray().put(laser).put("malformed").put(laser).put(healthy));
		var loaded = BattleNext_MemberReport.fromJson(raw);
		assertEquals(List.of("healthy"), loaded.getDealtBySource().stream().map(BattleNext_DamageSourceReport::getId).toList());
		assertEquals(3, loaded.getLoadErrors().size());
		assertEquals(raw.toString(), loaded.toJson().toString());
		loaded.toJson().getJSONArray("dealtBySource").put("external mutation");
		assertEquals(raw.toString(), loaded.toJson().toString());
	}

}
